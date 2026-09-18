    const SUPABASE_URL = "https://sklzrmubjeotbanagsag.supabase.co";
    const SUPABASE_KEY = "sb_publishable_3a2PFNZrt1MKWWwl00Yz_Q_jMH-RXyG";

    let currentBusiness = null;
    let currentUser = null;
    let currentPin = '';
    let staffMembers = [];

    async function sFetch(path, options = {}) {
      const headers = {
        'apikey': SUPABASE_KEY,
        'Authorization': `Bearer ${SUPABASE_KEY}`,
        'Content-Type': 'application/json',
        ...(options.headers || {})
      };
      const res = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, { ...options, headers });
      return res.json();
    }

    async function checkAuthOnStartup() {
      if (!bridge()) return;
      
      const savedUserStr = bridge().getCurrentUser(); // We'll hijack this to store { business: {}, user: {} }
      if (savedUserStr) {
        try {
          const authData = JSON.parse(savedUserStr);
          if (authData.business && authData.user) {
            currentBusiness = authData.business;
            currentUser = authData.user;
            document.getElementById('authOverlay').classList.add('hidden');
            refreshData(); // normal startup
            return;
          }
        } catch(e){}
      }

      // Show setup/login
      document.getElementById('authOverlay').classList.remove('hidden');
      showSetupScreen();
    }

    function showSetupScreen() {
      document.getElementById('setupScreen').classList.remove('hidden');
      document.getElementById('loginScreen').classList.add('hidden');
      document.getElementById('setupUrlInput').placeholder = "Enter your Business Name";
      document.querySelector('#setupScreen h2').textContent = "Join / Create Business";
      document.querySelector('#setupScreen p').textContent = "Enter your business name to get started.";
      const lbl = document.querySelector('#setupScreen label');
      if (lbl) lbl.textContent = "Business Name";
      document.getElementById('setupError').classList.add('hidden');
    }

    async function handleSetupSubmit() {
      const bName = document.getElementById('setupUrlInput').value.trim();
      if (!bName) return;
      
      const btn = document.getElementById('setupBtn');
      btn.innerHTML = '<span class="material-symbols-outlined animate-spin">refresh</span> Connecting...';
      btn.disabled = true;

      try {
        // Find existing business
        let businesses = await sFetch(`businesses?name=eq.${encodeURIComponent(bName)}&select=*`);
        if (businesses.length > 0) {
          currentBusiness = businesses[0];
        } else {
          // Create new business
          const res = await sFetch(`businesses`, {
            method: 'POST',
            headers: { 'Prefer': 'return=representation' },
            body: JSON.stringify({ name: bName })
          });
          currentBusiness = res[0];
        }
        
        await fetchStaff();
        showLoginScreen();
      } catch (err) {
        document.getElementById('setupError').textContent = "Connection failed.";
        document.getElementById('setupError').classList.remove('hidden');
      } finally {
        btn.innerHTML = '<span class="material-symbols-outlined">sync</span> Connect Business';
        btn.disabled = false;
      }
    }
    
    async function fetchStaff() {
      staffMembers = await sFetch(`staff?business_id=eq.${currentBusiness.id}&select=*`);
    }

    function showLoginScreen() {
      document.getElementById('setupScreen').classList.add('hidden');
      document.getElementById('loginScreen').classList.remove('hidden');
      populateUserDropdown();
    }

    function populateUserDropdown() {
      const select = document.getElementById('loginUserSelect');
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

    async function createNewStaff(name, pin) {
      document.getElementById('loginError').textContent = 'Creating...';
      try {
        await sFetch(`staff`, {
          method: 'POST',
          body: JSON.stringify({ business_id: currentBusiness.id, name, pin, role: 'staff' })
        });
        await fetchStaff();
        populateUserDropdown();
        document.getElementById('loginError').textContent = '';
      } catch(e) {
        document.getElementById('loginError').textContent = 'Failed to create staff';
      }
    }

    function appendPin(digit) {
      if (currentPin.length < 4) {
        currentPin += digit;
        updatePinDisplay();
        if (currentPin.length === 4) verifyPin();
      }
    }
    function clearPin() { currentPin = ''; updatePinDisplay(); }
    function deletePin() { currentPin = currentPin.slice(0, -1); updatePinDisplay(); }
    function updatePinDisplay() {
      for (let i = 1; i <= 4; i++) {
        const el = document.getElementById('pin' + i);
        if (i <= currentPin.length) {
          el.textContent = '•'; el.classList.add('bg-primary/20', 'border-primary');
        } else {
          el.textContent = ''; el.classList.remove('bg-primary/20', 'border-primary');
        }
      }
    }

    function verifyPin() {
      const selectedId = document.getElementById('loginUserSelect').value;
      if (!selectedId || selectedId === 'CREATE_NEW') {
        document.getElementById('loginError').textContent = "Select a user";
        clearPin(); return;
      }
      
      const user = staffMembers.find(u => u.id === selectedId);
      if (user && user.pin === currentPin) {
        currentUser = user;
        bridge().saveCurrentUser(JSON.stringify({ business: currentBusiness, user: currentUser }));
        document.getElementById('authOverlay').classList.add('hidden');
        refreshData();
      } else {
        document.getElementById('loginError').textContent = "Incorrect PIN";
        clearPin();
      }
    }
    
    function resetBusinessLink() {
      if(confirm('Unlink this device?')) {
        bridge().saveCurrentUser('');
        currentBusiness = null;
        currentUser = null;
        showSetupScreen();
      }
    }
