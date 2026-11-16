/**
 * ============================================================================
 * MODULE: SUPABASE AUTHENTICATION & MULTI-TENANT ONBOARDING
 * ============================================================================
 * Handles business tenant registration, staff profile selection,
 * and 4-digit PIN authentication.
 *
 * SECURITY MODEL (requires supabase/migrations/001_hashed_credentials.sql):
 *   * Business passwords and staff PINs are bcrypt-hashed server-side and are
 *     NEVER sent to or read by the client. All verification happens in
 *     SECURITY DEFINER RPCs (connect_business / create_staff /
 *     verify_staff_pin / list_staff).
 *   * Each device obtains a per-device Supabase Auth session (anonymous
 *     sign-in). Successful PIN verification binds that session's uid to the
 *     staff row (staff.auth_user_id), which migration 002's strict RLS uses to
 *     scope every table to the device's tenant.
 *   * If the anonymous provider is unavailable, the app degrades gracefully to
 *     the shared publishable key (permissive-RLS mode, pre-migration-002).
 */

const SUPABASE_URL = "https://sklzrmubjeotbanagsag.supabase.co";
const SUPABASE_KEY = "sb_publishable_3a2PFNZrt1MKWWwl00Yz_Q_jMH-RXyG";

// Global Authentication State
let currentBusiness = null;
let currentUser = null;
let currentPin = '';
let staffMembers = [];
// Business password kept in memory only for the current setup session so that
// "Create New Staff" can authenticate server-side. Never persisted.
let sessionBusinessPassword = null;
let authSessionReady = false;
let pinVerifyInFlight = false;
let supabaseAuthClient = null;

function getSupabaseAuthClient() {
  if (!window.supabase) return null;
  if (!supabaseAuthClient) {
    try {
      supabaseAuthClient = supabase.createClient(SUPABASE_URL, SUPABASE_KEY);
    } catch (e) {
      console.warn("[Auth] Failed to create Supabase client:", e);
      return null;
    }
  }
  return supabaseAuthClient;
}

/**
 * Ensures the device holds a Supabase Auth session (anonymous sign-in).
 * Returns true when a session exists; false when degraded to the shared key.
 */
async function ensureAuthSession() {
  if (authSessionReady) return true;
  const client = getSupabaseAuthClient();
  if (!client) return false;
  try {
    const { data } = await client.auth.getSession();
    if (data && data.session) {
      authSessionReady = true;
      return true;
    }
    const res = await client.auth.signInAnonymously();
    if (res && res.data && res.data.session) {
      authSessionReady = true;
      return true;
    }
    console.warn("[Auth] Anonymous sign-in unavailable:", res && res.error);
  } catch (e) {
    console.warn("[Auth] Could not obtain auth session:", e);
  }
  return false;
}

/**
 * Builds request headers. Uses the per-device session JWT when available so
 * migration 002's tenant-scoped RLS applies; falls back to the publishable key.
 */
async function authHeaders() {
  let token = SUPABASE_KEY;
  const client = getSupabaseAuthClient();
  if (client) {
    try {
      const { data } = await client.auth.getSession();
      if (data && data.session && data.session.access_token) {
        token = data.session.access_token;
      }
    } catch (e) {
      console.warn("[Auth] Session lookup failed, using publishable key:", e);
    }
  }
  return {
    'apikey': SUPABASE_KEY,
    'Authorization': `Bearer ${token}`
  };
}

/**
 * Standardized Supabase REST client wrapper.
 * Injects auth headers and application/json automatically.
 */
async function sFetch(path, options = {}) {
  const base = await authHeaders();
  const headers = {
    ...base,
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };
  const res = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, { ...options, headers });
  const text = await res.text();
  if (!res.ok) {
    let msg = `Request failed (${res.status})`;
    try {
      const errObj = text ? JSON.parse(text) : null;
      if (errObj && (errObj.message || errObj.error_description || errObj.error)) {
        msg = errObj.message || errObj.error_description || errObj.error;
      }
    } catch (_) { /* non-JSON error body */ }
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }
  return text ? JSON.parse(text) : null;
}

/**
 * Invokes a PostgREST RPC. Throws Error with the server-side message on failure.
 */
async function sRpc(fnName, args = {}) {
  const base = await authHeaders();
  const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${fnName}`, {
    method: 'POST',
    headers: { ...base, 'Content-Type': 'application/json' },
    body: JSON.stringify(args)
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (_) { /* ignore */ }
  if (!res.ok) {
    const msg = (data && (data.message || data.error_description || data.error)) ||
      `RPC ${fnName} failed (${res.status})`;
    throw new Error(msg);
  }
  return data;
}

/**
 * Validates saved business & staff credentials on startup.
 * If credentials exist and data is reachable, logs directly into dashboard;
 * otherwise displays setup screen.
 */
async function checkAuthOnStartup() {
  if (!bridge()) return;

  const savedUserStr = bridge().getCurrentUser();
  if (savedUserStr) {
    try {
      const authData = JSON.parse(savedUserStr);
      if (authData.business && authData.user) {
        // Probe data access: under strict RLS a device whose staff binding was
        // invalidated (e.g. WebView storage cleared, rebind on another device)
        // cannot read tenant data and must log in again.
        const reachable = await probeTenantAccess(authData.business.id);
        if (reachable) {
          currentBusiness = authData.business;
          currentUser = authData.user;
          document.getElementById('authOverlay').classList.add('hidden');
          refreshData(); // Launch dashboard
          return;
        }
        console.warn("[Auth] Saved session cannot access tenant data; requiring login.");
      }
    } catch (e) {
      console.warn("[Auth] Corrupt saved user profile, prompting login:", e);
    }
  }

  // Display initial setup/login modal
  document.getElementById('authOverlay').classList.remove('hidden');
  showSetupScreen();
}

/** Lightweight read probe to confirm tenant data access. */
async function probeTenantAccess(businessId) {
  try {
    await sFetch(`products?business_id=eq.${businessId}&select=id&limit=1`);
    return true;
  } catch (e) {
    return false;
  }
}

/**
 * Shows the business join / registration panel.
 */
function showSetupScreen() {
  document.getElementById('setupScreen').classList.remove('hidden');
  document.getElementById('loginScreen').classList.add('hidden');
  document.getElementById('setupError').classList.add('hidden');
}

/**
 * Connects to an existing business tenant or creates a new one, verifying the
 * password server-side via RPC. The password never leaves this device except
 * inside the RPC payload for verification.
 */
async function handleSetupSubmit() {
  const bName = document.getElementById('businessNameInput').value.trim();
  const bPass = document.getElementById('businessPasswordInput').value.trim();

  if (!bName || !bPass) {
    document.getElementById('setupError').textContent = "Both Name and Password are required";
    document.getElementById('setupError').classList.remove('hidden');
    return;
  }

  const btn = document.getElementById('setupBtn');
  btn.innerHTML = '<span class="material-symbols-outlined animate-spin">refresh</span> Connecting...';
  btn.disabled = true;
  document.getElementById('setupError').classList.add('hidden');

  try {
    await ensureAuthSession();
    const rows = await sRpc('connect_business', { p_name: bName, p_password: bPass });
    const b = Array.isArray(rows) ? rows[0] : rows;
    if (!b || !b.out_id) throw new Error("Unexpected response from server");
    currentBusiness = { id: b.out_id, name: b.out_name, created_at: b.out_created_at };
    sessionBusinessPassword = bPass;

    await fetchStaff();
    showLoginScreen();
  } catch (err) {
    document.getElementById('setupError').textContent =
      (err && err.message) ? err.message : "Connection failed. Try again.";
    document.getElementById('setupError').classList.remove('hidden');
  } finally {
    btn.innerHTML = '<span class="material-symbols-outlined">login</span> Connect';
    btn.disabled = false;
  }
}

/**
 * Fetches staff members belonging to the current business tenant.
 * Prefers the server-side RPC; falls back to a restricted column select for
 * databases where migration 001 has not been applied yet. PINs are never
 * requested or compared client-side.
 */
async function fetchStaff() {
  if (!currentBusiness) return;
  try {
    const rows = await sRpc('list_staff', {
      p_business_id: currentBusiness.id,
      p_business_password: sessionBusinessPassword
    });
    staffMembers = (rows || []).map(r => ({
      id: r.out_id,
      name: r.out_name,
      role: r.out_role,
      is_active: r.out_is_active
    }));
    return;
  } catch (e) {
    console.warn("[Auth] list_staff RPC unavailable, falling back:", e);
  }
  // Fallback: explicit column list only. Never selects credential columns.
  staffMembers = await sFetch(
    `staff?business_id=eq.${currentBusiness.id}&select=id,business_id,name,role,is_active`
  ) || [];
}

/**
 * Switches overlay to the Staff selection & PIN entry screen.
 */
function showLoginScreen() {
  document.getElementById('setupScreen').classList.add('hidden');
  document.getElementById('loginScreen').classList.remove('hidden');
  populateUserDropdown();
}

/**
 * Populates the staff profile selector.
 */
function populateUserDropdown() {
  const select = document.getElementById('loginUserSelect');
  if (!select) return;
  select.innerHTML = '<option value="">Select your name...</option>';
  select.innerHTML += '<option value="CREATE_NEW">+ Add New Staff</option>';
  staffMembers.forEach(u => {
    const opt = document.createElement('option');
    opt.value = u.id;
    opt.textContent = u.name;
    select.appendChild(opt);
  });
  select.onchange = (e) => {
    if (e.target.value === 'CREATE_NEW') {
      const name = prompt("Enter new staff name:");
      if (!name) { select.value = ''; return; }
      const pin = prompt("Create a 4-digit PIN for " + name + ":");
      if (pin && pin.length === 4) {
        createNewStaff(name, pin);
      } else {
        if (pin) alert("PIN must be exactly 4 digits.");
        select.value = '';
      }
    }
  };
}

/**
 * Registers a new staff account under the current business tenant.
 * Server-side RPC verifies the business password and hashes the PIN.
 */
async function createNewStaff(name, pin) {
  const errEl = document.getElementById('loginError');
  errEl.textContent = 'Creating...';
  try {
    let bizPassword = sessionBusinessPassword;
    if (!bizPassword) {
      // Re-login from a saved session: staff creation requires the business
      // password, which was intentionally not persisted.
      bizPassword = prompt("Confirm business password to add staff:");
      if (!bizPassword) {
        errEl.textContent = '';
        return;
      }
    }
    const rows = await sRpc('create_staff', {
      p_business_id: currentBusiness.id,
      p_name: name,
      p_pin: pin,
      p_business_password: bizPassword
    });
    const r = Array.isArray(rows) ? rows[0] : rows;
    if (r && r.out_id) {
      staffMembers.push({ id: r.out_id, name: r.out_name, role: r.out_role, is_active: r.out_is_active });
    } else {
      await fetchStaff();
    }
    populateUserDropdown();
    errEl.textContent = '';
  } catch (e) {
    errEl.textContent = (e && e.message) ? e.message : 'Failed to create staff';
  }
}

/**
 * PIN Keypad input handler.
 */
function appendPin(digit) {
  if (currentPin.length < 4) {
    currentPin += digit;
    updatePinDisplay();
    if (currentPin.length === 4) verifyPin();
  }
}

function clearPin() {
  currentPin = '';
  updatePinDisplay();
}

function deletePin() {
  currentPin = currentPin.slice(0, -1);
  updatePinDisplay();
}

function updatePinDisplay() {
  for (let i = 1; i <= 4; i++) {
    const el = document.getElementById('pin' + i);
    if (!el) continue;
    if (i <= currentPin.length) {
      el.textContent = '•';
      el.classList.add('bg-primary/20', 'border-primary');
    } else {
      el.textContent = '';
      el.classList.remove('bg-primary/20', 'border-primary');
    }
  }
}

/**
 * Verifies entered 4-digit PIN against the selected staff record via a
 * server-side RPC. The PIN is compared only inside the database (bcrypt);
 * failed attempts lock the account after 10 tries for 15 minutes.
 */
async function verifyPin() {
  if (pinVerifyInFlight) return;
  const selectedId = document.getElementById('loginUserSelect').value;
  if (!selectedId || selectedId === 'CREATE_NEW') {
    document.getElementById('loginError').textContent = "Select a user";
    clearPin();
    return;
  }

  pinVerifyInFlight = true;
  try {
    await ensureAuthSession();
    const rows = await sRpc('verify_staff_pin', { p_staff_id: selectedId, p_pin: currentPin });
    const r = Array.isArray(rows) ? rows[0] : rows;
    if (!r || !r.out_id) throw new Error("Unexpected response from server");
    currentUser = {
      id: r.out_id,
      business_id: r.out_business_id,
      name: r.out_name,
      role: r.out_role,
      is_active: r.out_is_active
    };
    sessionBusinessPassword = null; // No longer needed; clear from memory.
    if (bridge()) {
      bridge().saveCurrentUser(JSON.stringify({ business: currentBusiness, user: currentUser }));
    }
    document.getElementById('authOverlay').classList.add('hidden');
    refreshData();
  } catch (e) {
    document.getElementById('loginError').textContent = (e && e.message) ? e.message : "Incorrect PIN";
  } finally {
    pinVerifyInFlight = false;
    clearPin();
  }
}

/**
 * Unlinks the current device from the business.
 */
function resetBusinessLink() {
  if (confirm('Unlink this device?')) {
    if (bridge()) {
      bridge().saveCurrentUser('');
    }
    // Sign out of the per-device auth session so a stale binding cannot be reused.
    const client = getSupabaseAuthClient();
    if (client) {
      try { client.auth.signOut(); } catch (e) { console.warn(e); }
    }
    authSessionReady = false;
    currentBusiness = null;
    currentUser = null;
    sessionBusinessPassword = null;
    showSetupScreen();
    document.getElementById('authOverlay').classList.remove('hidden');
  }
}
