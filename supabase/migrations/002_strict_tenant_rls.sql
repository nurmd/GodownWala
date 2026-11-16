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
SET search_path = public
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
-- 2. Replace permissive baseline policies with strict tenant-scoped policies
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS "anon_all_businesses" ON public.businesses;
DROP POLICY IF EXISTS "anon_all_staff" ON public.staff;
DROP POLICY IF EXISTS "anon_all_products" ON public.products;
DROP POLICY IF EXISTS "anon_all_parties" ON public.parties;
DROP POLICY IF EXISTS "anon_all_transactions" ON public.transactions;

-- Resolves the caller's business from the staff row bound to their auth uid.
-- SECURITY DEFINER so the staff lookup is not subject to staff RLS itself.
CREATE OR REPLACE FUNCTION public.my_business_id()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT business_id FROM public.staff WHERE auth_user_id = auth.uid() LIMIT 1;
$$;

-- staff: a device may only see/manage the staff row bound to it.
CREATE POLICY "staff_bound_to_auth_uid" ON public.staff
  FOR ALL TO authenticated
  USING (auth_user_id = auth.uid())
  WITH CHECK (auth_user_id = auth.uid());

-- businesses: readable by devices bound to a staff member of that business.
-- Tenant creation still happens through the connect_business RPC.
CREATE POLICY "businesses_visible_to_members" ON public.businesses
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.staff s
     WHERE s.business_id = businesses.id AND s.auth_user_id = auth.uid()
  ));

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

GRANT SELECT (id, name, created_at) ON public.businesses TO authenticated;
GRANT SELECT (id, business_id, name, role, is_active, created_at, auth_user_id) ON public.staff TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.products TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.parties TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.transactions TO authenticated;

-- RPCs remain callable by both roles (connect/create flow happens pre-binding).
GRANT EXECUTE ON FUNCTION public.connect_business(TEXT, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.create_staff(UUID, TEXT, TEXT, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.verify_staff_pin(UUID, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.list_staff(UUID, TEXT) TO anon, authenticated;
