(function () {
    'use strict';

    var tabButtons = Array.prototype.slice.call(document.querySelectorAll('[data-tab-trigger]'));
    var tabContents = Array.prototype.slice.call(document.querySelectorAll('[data-tab-content]'));
    var forms = Array.prototype.slice.call(document.querySelectorAll('.auth-form'));
    var passwordToggles = Array.prototype.slice.call(document.querySelectorAll('[data-password-toggle]'));
    var tierButtons = Array.prototype.slice.call(document.querySelectorAll('[data-direct-tier]'));

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
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            var tier = form.getAttribute('data-tier') || 'basic';
            window.location.href = '/index.html?tier=' + tier;
        });
    });

    tierButtons.forEach(function (button) {
        button.addEventListener('click', function () {
            var tier = button.getAttribute('data-direct-tier') || 'basic';
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
})();
