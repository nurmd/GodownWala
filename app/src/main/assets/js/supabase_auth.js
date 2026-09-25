/**
 * ============================================================================
 * MODULE: SUPABASE AUTHENTICATION & MULTI-TENANT ONBOARDING
 * ============================================================================
 * Handles business tenant registration, staff profile selection,
 * and secure 4-digit PIN authentication.
 */

const SUPABASE_URL = "https://sklzrmubjeotbanagsag.supabase.co";
const SUPABASE_KEY = "sb_publishable_3a2PFNZrt1MKWWwl00Yz_Q_jMH-RXyG";

// Global Authentication State
let currentBusiness = null;
let currentUser = null;
let currentPin = '';
let staffMembers = [];

/**
 * Standardized Supabase REST client wrapper.
 * Inject anon key and application/json headers automatically.
 */
async function sFetch(path, options = {}) {
  const headers = {
    'apikey': SUPABASE_KEY,
    'Authorization': `Bearer ${SUPABASE_KEY}`,
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };
  const res = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, { ...options, headers });
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

/**
 * Validates saved business & staff credentials on startup.
 * If credentials exist, logs directly into dashboard; otherwise displays setup screen.
 */
async function checkAuthOnStartup() {
  if (!bridge()) return;
  
  const savedUserStr = bridge().getCurrentUser();
  if (savedUserStr) {
    try {
      const authData = JSON.parse(savedUserStr);
      if (authData.business && authData.user) {
        currentBusiness = authData.business;
        currentUser = authData.user;
        document.getElementById('authOverlay').classList.add('hidden');
        refreshData(); // Launch dashboard
        return;
      }
    } catch (e) {
      console.warn("[Auth] Corrupt saved user profile, prompting login:", e);
    }
  }

  // Display initial setup/login modal
  document.getElementById('authOverlay').classList.remove('hidden');
  showSetupScreen();
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
 * Connects to an existing business tenant or creates a new one with a password.
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
    let businesses = await sFetch(`businesses?name=eq.${encodeURIComponent(bName)}&select=*`);
    
    if (businesses && businesses.length > 0) {
      const b = businesses[0];
      if (b.password !== bPass) {
        document.getElementById('setupError').textContent = "Incorrect Business Password.";
        document.getElementById('setupError').classList.remove('hidden');
        return;
      }
      currentBusiness = b;
    } else {
      // Create new business tenant
      const res = await sFetch(`businesses`, {
        method: 'POST',
        headers: { 'Prefer': 'return=representation' },
        body: JSON.stringify({ name: bName, password: bPass })
      });
      currentBusiness = res[0];
    }
    
    await fetchStaff();
    showLoginScreen();
  } catch (err) {
    document.getElementById('setupError').textContent = "Connection failed. Try again.";
    document.getElementById('setupError').classList.remove('hidden');
  } finally {
    btn.innerHTML = '<span class="material-symbols-outlined">login</span> Connect';
    btn.disabled = false;
  }
}

/**
 * Fetches staff members belonging to the current business tenant.
 */
async function fetchStaff() {
  if (!currentBusiness) return;
  staffMembers = await sFetch(`staff?business_id=eq.${currentBusiness.id}&select=*`) || [];
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
      const pin = prompt("Create a 4-digit PIN for " + name + ":");
      if (name && pin && pin.length === 4) {
        createNewStaff(name, pin);
      } else {
        select.value = '';
      }
    }
  };
}

/**
 * Registers a new staff account under the current business tenant.
 */
async function createNewStaff(name, pin) {
  document.getElementById('loginError').textContent = 'Creating...';
  try {
    await sFetch(`staff`, {
      method: 'POST',
      headers: { 'Prefer': 'return=representation' },
      body: JSON.stringify({ business_id: currentBusiness.id, name, pin, role: 'staff' })
    });
    await fetchStaff();
    populateUserDropdown();
    document.getElementById('loginError').textContent = '';
  } catch (e) {
    document.getElementById('loginError').textContent = 'Failed to create staff';
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
 * Verifies entered 4-digit PIN against selected staff record.
 */
function verifyPin() {
  const selectedId = document.getElementById('loginUserSelect').value;
  if (!selectedId || selectedId === 'CREATE_NEW') {
    document.getElementById('loginError').textContent = "Select a user";
    clearPin();
    return;
  }
  
  const user = staffMembers.find(u => u.id === selectedId);
  if (user && user.pin === currentPin) {
    currentUser = user;
    if (bridge()) {
      bridge().saveCurrentUser(JSON.stringify({ business: currentBusiness, user: currentUser }));
    }
    document.getElementById('authOverlay').classList.add('hidden');
    refreshData();
  } else {
    document.getElementById('loginError').textContent = "Incorrect PIN";
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
    currentBusiness = null;
    currentUser = null;
    showSetupScreen();
    document.getElementById('authOverlay').classList.remove('hidden');
  }
}
