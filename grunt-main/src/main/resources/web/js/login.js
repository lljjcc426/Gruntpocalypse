(function () {
    'use strict';

    var tabButtons = Array.prototype.slice.call(document.querySelectorAll('[data-tab-trigger]'));
    var tabContents = Array.prototype.slice.call(document.querySelectorAll('[data-tab-content]'));
    var forms = Array.prototype.slice.call(document.querySelectorAll('.auth-form'));
    var passwordToggles = Array.prototype.slice.call(document.querySelectorAll('[data-password-toggle]'));
    var tierButtons = Array.prototype.slice.call(document.querySelectorAll('[data-direct-tier]'));
    var feedback = document.getElementById('auth-feedback');

    function normalizeTier(tier) {
        var value = String(tier || '').toLowerCase();
        if (value === 'user') return 'user';
        if (value === 'platform-admin' || value === 'platform_admin' || value === 'platformadmin') return 'platform-admin';
        if (value === 'super-admin' || value === 'super_admin' || value === 'superadmin' || value === 'admin') return 'super-admin';
        return 'user';
    }

    function showFeedback(message, kind) {
        if (!feedback) return;
        feedback.style.display = message ? 'block' : 'none';
        feedback.textContent = message || '';
        feedback.className = 'auth-feedback ' + (kind || 'info');
    }

    async function requestJson(path, options) {
        var response = await fetch(path, Object.assign({
            credentials: 'same-origin'
        }, options || {}));
        var text = await response.text();
        var payload = {};
        if (text) {
            try {
                payload = JSON.parse(text);
            } catch (_) {
                payload = { message: text };
            }
        }
        if (!response.ok) {
            var error = new Error(payload.message || response.statusText || 'Request failed');
            error.status = response.status;
            throw error;
        }
        return payload;
    }

    async function login(username, password, tier) {
        return requestJson('/api/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                username: username,
                password: password,
                tier: normalizeTier(tier)
            })
        });
    }

    function resolveNextLocation(payload, tier) {
        var params = new URLSearchParams(window.location.search);
        var next = params.get('next');
        if (next) return next;
        return (payload && payload.redirect) || ('/index.html?tier=' + normalizeTier(tier));
    }

    async function hydrateExistingSession() {
        try {
            var payload = await requestJson('/api/auth/me', { method: 'GET' });
            if (payload && payload.authenticated) {
                window.location.replace(resolveNextLocation(payload, payload.tier || 'user'));
            }
        } catch (_) {
            // Unauthenticated is expected on first visit.
        }
    }

    function activateTab(tabName) {
        tabButtons.forEach(function (button) {
            var isActive = button.getAttribute('data-tab-trigger') === tabName;
            button.classList.toggle('active', isActive);
            button.setAttribute('aria-selected', isActive ? 'true' : 'false');
        });
        tabContents.forEach(function (content) {
            var isActive = content.getAttribute('data-tab-content') === tabName;
            content.classList.toggle('active', isActive);
        });
    }

    tabButtons.forEach(function (button) {
        button.addEventListener('click', function () {
            var tabName = button.getAttribute('data-tab-trigger');
            activateTab(tabName);
        });
    });

    forms.forEach(function (form) {
        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            var tier = normalizeTier(form.getAttribute('data-tier') || 'user');
            if (form.getAttribute('data-tab-content') === 'signup') {
                showFeedback('Self-service sign-up is not available yet. Use the configured workspace account to continue.', 'info');
                activateTab('signin');
                return;
            }
            var usernameInput = form.querySelector('input[type="text"]');
            var passwordInput = form.querySelector('input[type="password"]');
            var username = usernameInput ? usernameInput.value.trim() : '';
            var password = passwordInput ? passwordInput.value : '';
            showFeedback('', 'info');
            try {
                var payload = await login(username, password, tier);
                window.location.href = resolveNextLocation(payload, tier);
            } catch (error) {
                showFeedback(error.message || 'Login failed', 'error');
            }
        });
    });

    tierButtons.forEach(function (button) {
        button.addEventListener('click', function () {
            var tier = normalizeTier(button.getAttribute('data-direct-tier') || 'user');
            window.location.href = '/index.html?tier=' + tier;
        });
    });

    passwordToggles.forEach(function (button) {
        button.addEventListener('click', function () {
            var shell = button.parentElement;
            if (!shell) return;
            var input = shell.querySelector('input');
            if (!input) return;
            var isPassword = input.getAttribute('type') === 'password';
            input.setAttribute('type', isPassword ? 'text' : 'password');
            button.setAttribute('aria-label', isPassword ? 'Hide password' : 'Show password');
        });
    });

    hydrateExistingSession();
})();
