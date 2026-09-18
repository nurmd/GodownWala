    let cloudUsers = [];
    let currentPin = '';
    let currentUser = null;
    let authCallbackMap = {};
    
    function generateUUID() {
      return 'cb_' + Math.random().toString(36).substr(2, 9);
    }

    window.cloudSyncCallback = function(callbackId, success, payload) {
      if (authCallbackMap[callbackId]) {
        authCallbackMap[callbackId](success, payload);
        delete authCallbackMap[callbackId];
      }
    };

    function callCloud(payloadObj, callback) {
      if (!bridge() || !bridge().getCloudUrl()) {
        callback(false, "Cloud URL not set");
        return;
      }
      const cbId = generateUUID();
      authCallbackMap[cbId] = callback;
      bridge().syncCloudData(JSON.stringify(payloadObj), cbId);
    }

    function checkAuthOnStartup() {
      if (!bridge()) return;
      
      const savedUserStr = bridge().getCurrentUser();
      if (savedUserStr) {
        currentUser = JSON.parse(savedUserStr);
        document.getElementById('authOverlay').classList.add('hidden');
        refreshData(); // normal startup
        return;
      }

      // Need to login or link
      document.getElementById('authOverlay').classList.remove('hidden');
      
      const cloudUrl = bridge().getCloudUrl();
      if (!cloudUrl) {
        showSetupScreen();
      } else {
        showLoginScreen();
      }
    }

    function showSetupScreen() {
      document.getElementById('setupScreen').classList.remove('hidden');
      document.getElementById('loginScreen').classList.add('hidden');
      document.getElementById('setupError').classList.add('hidden');
    }

    function showLoginScreen() {
      document.getElementById('setupScreen').classList.add('hidden');
      document.getElementById('loginScreen').classList.remove('hidden');
      document.getElementById('loginError').textContent = 'Fetching users...';
      
      callCloud({action: 'get_users'}, function(success, responseStr) {
        if (!success) {
          document.getElementById('loginError').textContent = responseStr || 'Failed to fetch users';
          return;
        }
        try {
          const data = typeof responseStr === 'string' ? JSON.parse(responseStr) : responseStr;
          if (data.success) {
            cloudUsers = data.users || [];
            populateUserDropdown();
            document.getElementById('loginError').textContent = '';
          } else {
            document.getElementById('loginError').textContent = data.error || 'Failed to fetch users';
          }
        } catch(e) {
          document.getElementById('loginError').textContent = 'Invalid response from server';
        }
      });
    }

    function populateUserDropdown() {
      const select = document.getElementById('loginUserSelect');
      select.innerHTML = '<option value="">Select your name...</option>';
      cloudUsers.forEach(u => {
        const opt = document.createElement('option');
        opt.value = u.name;
        opt.textContent = u.name;
        select.appendChild(opt);
      });
    }

    function handleSetupSubmit() {
      const url = document.getElementById('setupUrlInput').value.trim();
      if (!url.startsWith('https://script.google.com/macros/s/')) {
        document.getElementById('setupError').textContent = "Invalid Apps Script URL";
        document.getElementById('setupError').classList.remove('hidden');
        return;
      }
      
      const btn = document.getElementById('setupBtn');
      btn.innerHTML = '<span class="material-symbols-outlined animate-spin">refresh</span> Connecting...';
      btn.disabled = true;

      bridge().saveCloudUrl(url);
      
      callCloud({action: 'get_users'}, function(success, responseStr) {
        btn.innerHTML = '<span class="material-symbols-outlined">sync</span> Connect Device';
        btn.disabled = false;
        
        if (success) {
          showLoginScreen();
        } else {
          document.getElementById('setupError').textContent = "Failed to connect to sheet. Check the URL.";
          document.getElementById('setupError').classList.remove('hidden');
          bridge().saveCloudUrl(''); // clear it
        }
      });
    }

    function resetBusinessLink() {
      if(confirm('Are you sure you want to unlink this device? You will need the URL to link again.')) {
        bridge().saveCloudUrl('');
        showSetupScreen();
      }
    }

    function appendPin(digit) {
      if (currentPin.length < 4) {
        currentPin += digit;
        updatePinDisplay();
        if (currentPin.length === 4) {
          verifyPin();
        }
      }
    }

    function clearPin() {
      currentPin = '';
      updatePinDisplay();
      document.getElementById('loginError').textContent = '';
    }

    function deletePin() {
      if (currentPin.length > 0) {
        currentPin = currentPin.slice(0, -1);
        updatePinDisplay();
      }
    }

    function updatePinDisplay() {
      for (let i = 1; i <= 4; i++) {
        const el = document.getElementById('pin' + i);
        if (i <= currentPin.length) {
          el.textContent = '•';
          el.classList.add('bg-primary/20', 'border-primary');
        } else {
          el.textContent = '';
          el.classList.remove('bg-primary/20', 'border-primary');
        }
      }
    }

    function verifyPin() {
      const selectedName = document.getElementById('loginUserSelect').value;
      if (!selectedName) {
        document.getElementById('loginError').textContent = "Please select a user first";
        clearPin();
        return;
      }
      
      const user = cloudUsers.find(u => u.name === selectedName);
      if (user && user.pin === currentPin) {
        // Success
        currentUser = user;
        bridge().saveCurrentUser(JSON.stringify(user));
        document.getElementById('authOverlay').classList.add('hidden');
        refreshData();
      } else {
        document.getElementById('loginError').textContent = "Incorrect PIN";
        clearPin();
      }
    }

