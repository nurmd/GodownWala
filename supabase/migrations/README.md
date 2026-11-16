# GodownWala Supabase Migrations

Run these in the Supabase SQL Editor (Dashboard → SQL Editor), in order.

## 001_hashed_credentials.sql — safe to run now

- Adds `password_hash` / `pin_hash` (bcrypt via `pgcrypto`), staff lockout
  columns, and `staff.auth_user_id` for per-device binding.
- Backfills hashes from the existing plaintext columns.
- Adds server-side RPCs: `connect_business`, `create_staff`, `verify_staff_pin`,
  `list_staff`. The app (from the release that ships this repo's current
  `supabase_auth.js`) uses only these RPCs — plaintext secrets are never sent
  over the wire or readable by clients through the app.
- **Backward compatible:** does not drop columns or change policies, so
  already-deployed app versions keep working. Note the plaintext columns remain
  server-readable until 002 runs.

## 002_strict_tenant_rls.sql — run ONLY after rollout of the new app version

- Drops the plaintext `businesses.password` and `staff.pin` columns.
- Replaces the permissive baseline RLS policies with strict tenant-scoped
  policies bound to `staff.auth_user_id = auth.uid()`.
- Revokes all table privileges from the shared `anon` key.

### Dashboard prerequisite

Authentication → Providers → enable **Anonymous sign-ins**, so devices can get
a per-device JWT used for tenant binding.

### Rollout checklist

1. Run 001.
2. Release the app version containing the RPC-based auth flow (verify login,
   staff creation, PIN verification, and sync on a real device).
3. Update all devices (Play/release channel or APK distribution).
4. Confirm no devices are running versions older than the RPC-based release
   (old versions fail loudly once 002 runs).
5. Enable Anonymous sign-ins provider.
6. Run 002, then verify on a device: login, sync, realtime updates, and that
   a second business's data is not visible.

## Notes

- If `CREATE EXTENSION pgcrypto` fails with a permission error, enable it in
  Dashboard → Database → Extensions → `pgcrypto`, then re-run the migration.
- The RPCs use `search_path = public, extensions` so `crypt()`/`gen_salt()`
  resolve whether pgcrypto is installed in `public` or `extensions`.
- Migration 001 is idempotent — safe to re-run if something fails partway.
- Lockout: 10 failed PIN attempts locks the staff account for 15 minutes.
- Binding model (002): each **device** (auth uid) gets a row in
  `device_bindings(auth_user_id → business_id, staff_id)`. Several devices may
  share one staff profile without stealing each other's access. Existing
  `staff.auth_user_id` values are backfilled into `device_bindings`.
- A device that restored a saved login gets its auth session on startup; if it
  has no valid binding under strict RLS, it is returned to the login screen and
  must enter the PIN once (which creates the binding).
- Clearing WebView storage creates a new anonymous uid → log in again with the
  PIN to rebind.
