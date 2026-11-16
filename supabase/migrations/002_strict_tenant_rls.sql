-- ============================================================================
-- GodownWala - Migration 002: Drop Plaintext Credentials + Strict Tenant RLS
-- ============================================================================
-- Run this AFTER migration 001 and ONLY after every device has been updated to
-- the app version that:
--   * signs in to Supabase Auth anonymously and uses the session token, and
--   * performs login/staff-creation through the migration-001 RPCs.
--
-- WARNING: old app versions WILL BREAK after this runs (login and data reads
-- are denied to the shared anon key). Verify the new app on a device first.
--
-- PREREQUISITE (Supabase Dashboard -> Authentication -> Providers):
--   * Enable "Anonymous sign-ins" so devices can obtain a per-device JWT.
--
-- MULTI-DEVICE MODEL: each device (auth uid) is bound to a tenant through
-- public.device_bindings. Multiple devices may use the same staff profile;
-- bindings never overwrite each other (unlike the 001 staff.auth_user_id
-- column, which is kept only as informational metadata).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Remove legacy plaintext credential columns (hashes were backfilled in 001)
-- ---------------------------------------------------------------------------
ALTER TABLE public.businesses DROP COLUMN IF EXISTS password;
ALTER TABLE public.staff DROP COLUMN IF EXISTS pin;

-- create_staff wrote the plaintext pin column for backward compatibility;
-- recreate it without that column now.
CREATE OR REPLACE FUNCTION public.create_staff(
  p_business_id UUID, p_name TEXT, p_pin TEXT, p_business_password TEXT
)
RETURNS TABLE (out_id UUID, out_business_id UUID, out_name TEXT, out_role TEXT, out_is_active BOOLEAN)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_staff_id UUID;
BEGIN
  IF p_pin IS NULL OR p_pin !~ '^[0-9]{4}$' THEN
    RAISE EXCEPTION 'PIN must be exactly 4 digits';
  END IF;
  IF p_name IS NULL OR length(btrim(p_name)) = 0 THEN
    RAISE EXCEPTION 'Staff name is required';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.businesses
     WHERE id = p_business_id
       AND password_hash IS NOT NULL
       AND password_hash = crypt(p_business_password, password_hash)
  ) THEN
    RAISE EXCEPTION 'Business authentication failed. Cannot create staff.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.staff
     WHERE business_id = p_business_id AND lower(staff.name) = lower(btrim(p_name))
  ) THEN
    RAISE EXCEPTION 'A staff member with this name already exists';
  END IF;

  INSERT INTO public.staff (business_id, name, pin_hash, role, is_active)
  VALUES (p_business_id, btrim(p_name), crypt(p_pin, gen_salt('bf', 10)), 'staff', true)
  RETURNING public.staff.id INTO v_staff_id;

  RETURN QUERY SELECT s.id, s.business_id, s.name, s.role, s.is_active
    FROM public.staff s WHERE s.id = v_staff_id;
END $$;

-- ---------------------------------------------------------------------------
-- 2. Per-device tenant bindings
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.device_bindings (
  auth_user_id UUID PRIMARY KEY,
  business_id UUID NOT NULL REFERENCES public.businesses(id) ON DELETE CASCADE,
  staff_id UUID REFERENCES public.staff(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.device_bindings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "device_bindings_own" ON public.device_bindings;
CREATE POLICY "device_bindings_own" ON public.device_bindings
  FOR ALL TO authenticated
  USING (auth_user_id = auth.uid())
  WITH CHECK (auth_user_id = auth.uid());

-- Carry over any bindings established by migration 001's verify_staff_pin.
INSERT INTO public.device_bindings (auth_user_id, business_id, staff_id)
SELECT s.auth_user_id, s.business_id, s.id
  FROM public.staff s
 WHERE s.auth_user_id IS NOT NULL AND s.business_id IS NOT NULL
ON CONFLICT (auth_user_id) DO NOTHING;

-- Replace verify_staff_pin so it binds the *device* (auth uid), not the staff
-- row, allowing several devices to share one staff profile.
CREATE OR REPLACE FUNCTION public.verify_staff_pin(p_staff_id UUID, p_pin TEXT)
RETURNS TABLE (out_id UUID, out_business_id UUID, out_name TEXT, out_role TEXT, out_is_active BOOLEAN)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_staff public.staff%ROWTYPE;
BEGIN
  SELECT * INTO v_staff FROM public.staff WHERE id = p_staff_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Staff member not found';
  END IF;
  IF v_staff.is_active = false THEN
    RAISE EXCEPTION 'This staff account is disabled';
  END IF;
  IF v_staff.locked_until IS NOT NULL AND v_staff.locked_until > now() THEN
    RAISE EXCEPTION 'Too many failed attempts. Try again in a few minutes.';
  END IF;

  IF v_staff.pin_hash IS NULL OR v_staff.pin_hash <> crypt(p_pin, v_staff.pin_hash) THEN
    UPDATE public.staff
       SET failed_attempts = failed_attempts + 1,
           locked_until = CASE WHEN failed_attempts + 1 >= 10
                               THEN now() + INTERVAL '15 minutes' END
     WHERE id = p_staff_id;
    RAISE EXCEPTION 'Incorrect PIN';
  END IF;

  UPDATE public.staff
     SET failed_attempts = 0,
         locked_until = NULL,
         auth_user_id = COALESCE(auth.uid(), auth_user_id)
   WHERE id = p_staff_id;

  IF auth.uid() IS NOT NULL THEN
    INSERT INTO public.device_bindings (auth_user_id, business_id, staff_id)
    VALUES (auth.uid(), v_staff.business_id, v_staff.id)
    ON CONFLICT (auth_user_id) DO UPDATE
      SET business_id = EXCLUDED.business_id,
          staff_id = EXCLUDED.staff_id,
          last_seen = now();
  END IF;

  RETURN QUERY SELECT s.id, s.business_id, s.name, s.role, s.is_active
    FROM public.staff s WHERE s.id = p_staff_id;
END $$;

-- list_staff: allow either the business password or an already-bound device.
CREATE OR REPLACE FUNCTION public.list_staff(
  p_business_id UUID, p_business_password TEXT
)
RETURNS TABLE (out_id UUID, out_name TEXT, out_role TEXT, out_is_active BOOLEAN)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_password_ok BOOLEAN;
  v_bound BOOLEAN;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM public.businesses
     WHERE id = p_business_id
       AND password_hash IS NOT NULL
       AND p_business_password IS NOT NULL
       AND password_hash = crypt(p_business_password, password_hash)
  ) INTO v_password_ok;

  SELECT EXISTS (
    SELECT 1 FROM public.device_bindings
     WHERE business_id = p_business_id AND auth_user_id = auth.uid()
  ) INTO v_bound;

  IF NOT (v_password_ok OR v_bound) THEN
    RAISE EXCEPTION 'Not authorized to list staff for this business';
  END IF;

  RETURN QUERY SELECT s.id, s.name, s.role, s.is_active
    FROM public.staff s
   WHERE s.business_id = p_business_id AND s.is_active = true
   ORDER BY s.name;
END $$;

-- ---------------------------------------------------------------------------
-- 3. Replace permissive baseline policies with strict tenant-scoped policies
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS "anon_all_businesses" ON public.businesses;
DROP POLICY IF EXISTS "anon_all_staff" ON public.staff;
DROP POLICY IF EXISTS "anon_all_products" ON public.products;
DROP POLICY IF EXISTS "anon_all_parties" ON public.parties;
DROP POLICY IF EXISTS "anon_all_transactions" ON public.transactions;
DROP POLICY IF EXISTS "staff_bound_to_auth_uid" ON public.staff;
DROP POLICY IF EXISTS "businesses_visible_to_members" ON public.businesses;
DROP POLICY IF EXISTS "products_tenant_isolated" ON public.products;
DROP POLICY IF EXISTS "parties_tenant_isolated" ON public.parties;
DROP POLICY IF EXISTS "transactions_tenant_isolated" ON public.transactions;

-- Resolves the caller's tenant from their device binding.
-- SECURITY DEFINER so the lookup itself is not subject to RLS.
CREATE OR REPLACE FUNCTION public.my_business_id()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT business_id FROM public.device_bindings WHERE auth_user_id = auth.uid() LIMIT 1;
$$;

-- staff: devices see the staff directory of their own tenant only.
CREATE POLICY "staff_tenant_visible" ON public.staff
  FOR SELECT TO authenticated
  USING (business_id = public.my_business_id());

-- businesses: readable by bound devices of that business.
CREATE POLICY "businesses_visible_to_members" ON public.businesses
  FOR SELECT TO authenticated
  USING (id = public.my_business_id());

-- products / parties / transactions: strictly scoped to the caller's tenant.
CREATE POLICY "products_tenant_isolated" ON public.products
  FOR ALL TO authenticated
  USING (business_id = public.my_business_id())
  WITH CHECK (business_id = public.my_business_id());

CREATE POLICY "parties_tenant_isolated" ON public.parties
  FOR ALL TO authenticated
  USING (business_id = public.my_business_id())
  WITH CHECK (business_id = public.my_business_id());

CREATE POLICY "transactions_tenant_isolated" ON public.transactions
  FOR ALL TO authenticated
  USING (business_id = public.my_business_id())
  WITH CHECK (business_id = public.my_business_id());

-- The shared anon key gets NO table access (default deny under RLS).
-- Devices must hold a real session JWT (authenticated role) to read/write data.
REVOKE ALL ON public.businesses FROM anon;
REVOKE ALL ON public.staff FROM anon;
REVOKE ALL ON public.products FROM anon;
REVOKE ALL ON public.parties FROM anon;
REVOKE ALL ON public.transactions FROM anon;
REVOKE ALL ON public.device_bindings FROM anon;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.products TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.parties TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.transactions TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.device_bindings TO authenticated;

-- RPCs remain callable by both roles (connect/create flow happens pre-binding).
GRANT EXECUTE ON FUNCTION public.connect_business(TEXT, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.create_staff(UUID, TEXT, TEXT, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.verify_staff_pin(UUID, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.list_staff(UUID, TEXT) TO anon, authenticated;

NOTIFY pgrst, 'reload schema';
