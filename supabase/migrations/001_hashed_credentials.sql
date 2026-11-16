-- ============================================================================
-- GodownWala - Migration 001: Hashed Credentials + Server-Side Verification RPCs
-- ============================================================================
-- Run this FIRST, in the Supabase SQL Editor.
--
-- WHAT IT DOES
--   1. Enables pgcrypto and adds hashed credential columns:
--        businesses.password_hash, staff.pin_hash
--   2. Adds staff lockout columns (failed_attempts, locked_until) and an
--      auth_user_id column used by migration 002 for per-device binding.
--   3. Backfills hashes from the existing plaintext columns (idempotent).
--   4. Creates SECURITY DEFINER RPCs so the app verifies credentials entirely
--      server-side; plaintext secrets are never sent to or read by clients.
--
-- COMPATIBILITY
--   * Does NOT drop plaintext columns or change grants/policies, so the
--     currently deployed app versions keep working unchanged.
--   * The plaintext columns remain readable until migration 002 is run. Run
--     002 only after every device has been updated to the app version that
--     uses these RPCs.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- 1. New columns
-- ---------------------------------------------------------------------------
ALTER TABLE public.businesses ADD COLUMN IF NOT EXISTS password_hash TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS pin_hash TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;
-- Binds a staff profile to the device's Supabase auth uid (used by migration 002).
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS auth_user_id UUID;

-- ---------------------------------------------------------------------------
-- 2. Make legacy plaintext columns nullable (RPC inserts no longer supply them)
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'businesses' AND column_name = 'password'
  ) THEN
    EXECUTE 'ALTER TABLE public.businesses ALTER COLUMN password DROP NOT NULL';
  END IF;
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'staff' AND column_name = 'pin'
  ) THEN
    EXECUTE 'ALTER TABLE public.staff ALTER COLUMN pin DROP NOT NULL';
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 3. Backfill hashes from existing plaintext columns (skipped if absent)
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'businesses' AND column_name = 'password'
  ) THEN
    UPDATE public.businesses
       SET password_hash = crypt(password, gen_salt('bf', 10))
     WHERE password_hash IS NULL;
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'staff' AND column_name = 'pin'
  ) THEN
    UPDATE public.staff
       SET pin_hash = crypt(pin, gen_salt('bf', 10))
     WHERE pin_hash IS NULL;
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 4. RPCs (SECURITY DEFINER; verified server-side, secrets stay server-side)
-- ---------------------------------------------------------------------------

-- Connect to a business by name + password, creating it if the name is new.
-- Returns a single row: out_id, out_name, out_created_at.
-- Raises 'Incorrect Business Password' when the name exists but password mismatches.
CREATE OR REPLACE FUNCTION public.connect_business(p_name TEXT, p_password TEXT)
RETURNS TABLE (out_id UUID, out_name TEXT, out_created_at TIMESTAMPTZ)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_id UUID;
BEGIN
  IF p_name IS NULL OR p_password IS NULL
     OR length(btrim(p_name)) < 2 OR length(btrim(p_password)) < 4 THEN
    RAISE EXCEPTION 'Business name (min 2 chars) and password (min 4 chars) are required';
  END IF;

  SELECT id INTO v_id
    FROM public.businesses
   WHERE lower(businesses.name) = lower(btrim(p_name));

  IF v_id IS NOT NULL THEN
    IF NOT EXISTS (
      SELECT 1 FROM public.businesses
       WHERE id = v_id
         AND password_hash IS NOT NULL
         AND password_hash = crypt(p_password, password_hash)
    ) THEN
      RAISE EXCEPTION 'Incorrect Business Password';
    END IF;
    RETURN QUERY SELECT b.id, b.name, b.created_at
      FROM public.businesses b WHERE b.id = v_id;
  ELSE
    -- New tenant: create it.
    -- Raises unique_violation (-> "already exists") on a concurrent same-name insert.
    INSERT INTO public.businesses (name, password_hash)
    VALUES (btrim(p_name), crypt(p_password, gen_salt('bf', 10)))
    RETURNING public.businesses.id INTO v_id;
    RETURN QUERY SELECT b.id, b.name, b.created_at
      FROM public.businesses b WHERE b.id = v_id;
  END IF;
END $$;

-- Register a new staff member under a business. Requires the business password
-- so that only devices which authenticated to the tenant can mint accounts.
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

  INSERT INTO public.staff (business_id, name, pin, pin_hash, role, is_active)
  VALUES (p_business_id, btrim(p_name), p_pin, crypt(p_pin, gen_salt('bf', 10)), 'staff', true)
  RETURNING public.staff.id INTO v_staff_id;

  RETURN QUERY SELECT s.id, s.business_id, s.name, s.role, s.is_active
    FROM public.staff s WHERE s.id = v_staff_id;
END $$;

-- NOTE: create_staff still writes the plaintext `pin` column (kept for backward
-- compatibility with deployed app versions that read it). Migration 002 drops
-- plaintext columns, at which point change the INSERT above to omit `pin` and
-- the `pin_hash IS NOT NULL` guard on businesses becomes unnecessary.

-- Verify a staff PIN server-side. Implements lockout after 10 failed attempts
-- (15-minute lock). On success, binds the staff row to the caller's Supabase
-- auth uid when a session exists (used by migration 002 for tenant-scoped RLS).
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

  RETURN QUERY SELECT s.id, s.business_id, s.name, s.role, s.is_active
    FROM public.staff s WHERE s.id = p_staff_id;
END $$;

-- List staff names for a business. Allowed when either the business password
-- is presented (login/setup flow) or the caller's auth uid is bound to a staff
-- member of that business (saved-session flow, required under migration 002 RLS).
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
    SELECT 1 FROM public.staff
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

GRANT EXECUTE ON FUNCTION public.connect_business(TEXT, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.create_staff(UUID, TEXT, TEXT, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.verify_staff_pin(UUID, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.list_staff(UUID, TEXT) TO anon, authenticated;

-- Ask PostgREST to reload its schema cache immediately so the new RPCs are
-- callable without waiting for the automatic reload.
NOTIFY pgrst, 'reload schema';
