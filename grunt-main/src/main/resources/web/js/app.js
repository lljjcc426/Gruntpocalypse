/* Grunt Web UI */
(function () {
    'use strict';

      const K = {
          theme: 'grunt.web.theme',
          left: 'grunt.web.split.leftPct',
          bottom: 'grunt.web.split.bottomPct',
          projectLeft: 'grunt.web.project.leftPct'
      };
      const CFG = {
          theme: 'light',
          left: 30,
          bottom: 28,
          projectLeft: 34,
          minLeft: 280,
          minRight: 420,
          minTop: 260,
          minBottom: 160,
          minProjectLeft: 280,
          minProjectRight: 360,
          projectCompact: 900,
          compact: 1100
      };
    const TF_ORDER = ['Optimization', 'Miscellaneous', 'Controlflow', 'Encryption', 'Redirect', 'Renaming', 'Minecraft'];

    let schema = null;
    let configDoc = null;
    let currentConfigName = 'config.json';
    let expanded = {};
    let running = false;
      let isCompact = false;
      let activePane = 'config';
      let leftPct = CFG.left;
      let bottomPct = CFG.bottom;
      let projectLeftPct = CFG.projectLeft;
      let scope = 'input';
    let sessionId = '';
    let consoleWs = null;
      let progressWs = null;
      let schemaFieldMap = Object.create(null);
      let expandedCategories = Object.create(null);

    const uploadState = {
        inputFileName: '',
        libraryFiles: [],
        assetFiles: []
    };

    const project = {
        tree: { input: [], output: [] },
        meta: {
            input: { available: false, classCount: 0 },
            output: { available: false, classCount: 0 }
        },
        tabs: { input: [], output: [] },
        active: { input: null, output: null },
        selected: { input: null, output: null }
    };

    const el = {
        workspace: document.getElementById('workspace'),
        navBtns: Array.from(document.querySelectorAll('.workspace-nav-btn')),
        panelConfig: document.getElementById('panel-config'),
          panelProject: document.getElementById('panel-project'),
          panelConsole: document.getElementById('panel-console'),
          projectBody: document.querySelector('.project-body'),
          configPanel: document.getElementById('config-panel'),
          splitV: document.getElementById('splitter-vertical'),
          splitH: document.getElementById('splitter-horizontal'),
          projectSplit: document.getElementById('project-splitter'),
          configFileInput: document.getElementById('config-file-input'),
        inputFileInput: document.getElementById('input-file-input'),
        librariesFileInput: document.getElementById('libraries-file-input'),
        assetsFileInput: document.getElementById('assets-file-input'),
        btnSaveConfig: document.getElementById('btn-save-config'),
        btnObf: document.getElementById('btn-obfuscate'),
        btnDl: document.getElementById('btn-download'),
        statusDot: document.querySelector('.status-dot'),
        statusText: document.getElementById('status-text'),
        progressWrap: document.getElementById('progress-container'),
        progressBar: document.getElementById('progress-bar'),
        progressText: document.getElementById('progress-text'),
        console: document.getElementById('console-output'),
        settingsWrap: document.getElementById('settings-content'),
        tfWrap: document.getElementById('transformers-content'),
        btnTheme: document.getElementById('btn-theme-toggle'),
        themeIcon: document.getElementById('theme-icon'),
        themeText: document.getElementById('theme-text'),
        tree: document.getElementById('project-tree'),
        tabs: document.getElementById('editor-tabs'),
        code: document.getElementById('code-viewer'),
        meta: document.getElementById('project-meta'),
        btnScopeIn: document.getElementById('btn-scope-input'),
        btnScopeOut: document.getElementById('btn-scope-output')
    };

    function clone(value) {
        return value == null ? value : JSON.parse(JSON.stringify(value));
    }

    function isObj(value) {
        return !!value && typeof value === 'object' && !Array.isArray(value);
    }

    function esc(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function renderHighlightedLine(line) {
        const raw = line == null ? '' : String(line);
        const safeFallback = esc(raw || ' ');
        try {
            const hljs = window.hljs;
            if (!hljs || typeof hljs.highlight !== 'function') return safeFallback;
            const result = hljs.highlight(raw || ' ', { language: 'java', ignoreIllegals: true });
            return result && typeof result.value === 'string' && result.value ? result.value : safeFallback;
        } catch (_) {
            return safeFallback;
        }
    }

    function clamp(value, min, max) {
        return Math.min(max, Math.max(min, value));
    }

    function keyOf(path) {
        return (Array.isArray(path) ? path : String(path || '').split('.')).join('.');
    }

    function idOf(path) {
        return 'cfg-' + keyOf(path).replace(/[^A-Za-z0-9_-]/g, '-');
    }

    function getPath(obj, path) {
        return (Array.isArray(path) ? path : String(path || '').split('.')).reduce((cur, part) => cur == null ? cur : cur[part], obj);
    }

    function setPath(obj, path, value) {
        const parts = Array.isArray(path) ? path.slice() : String(path || '').split('.');
        let cur = obj;
        for (let i = 0; i < parts.length - 1; i++) {
            if (!isObj(cur[parts[i]])) cur[parts[i]] = {};
            cur = cur[parts[i]];
        }
        cur[parts[parts.length - 1]] = value;
    }

    function merge(defaults, incoming) {
        if (Array.isArray(defaults)) {
            return Array.isArray(incoming) ? clone(incoming) : clone(defaults);
        }
        if (!isObj(defaults)) {
            return incoming === undefined ? defaults : incoming;
        }
        const out = {};
        Object.keys(defaults).forEach((key) => {
            out[key] = merge(defaults[key], incoming ? incoming[key] : undefined);
        });
        if (isObj(incoming)) {
            Object.keys(incoming).forEach((key) => {
                if (!(key in out)) out[key] = clone(incoming[key]);
            });
        }
        return out;
    }

    function setStatus(kind, text) {
        el.statusDot.className = 'status-dot ' + kind;
        el.statusText.textContent = text;
        document.body.setAttribute('data-status', kind);
    }

    function log(msg, level) {
        const line = document.createElement('div');
        line.className = 'console-line ' + (level || 'info');
        line.textContent = msg;
        el.console.appendChild(line);
        el.console.scrollTop = el.console.scrollHeight;
    }

    function applyAccessTier() {
        const tier = new URLSearchParams(window.location.search).get('tier');
        const isBasic = (tier || '').toLowerCase() === 'basic';
        const proBadge = document.querySelector('.pro-badge');
        if (proBadge) proBadge.style.display = isBasic ? 'none' : 'inline-flex';
    }

      function restoreSplit() {
          const left = parseFloat(localStorage.getItem(K.left) || '');
          const bottom = parseFloat(localStorage.getItem(K.bottom) || '');
          const projectLeft = parseFloat(localStorage.getItem(K.projectLeft) || '');
          if (Number.isFinite(left)) leftPct = left;
          if (Number.isFinite(bottom)) bottomPct = bottom;
          if (Number.isFinite(projectLeft)) projectLeftPct = projectLeft;
      }

      function saveSplit() {
          localStorage.setItem(K.left, String(leftPct));
          localStorage.setItem(K.bottom, String(bottomPct));
          localStorage.setItem(K.projectLeft, String(projectLeftPct));
      }

      function applySplit() {
          document.documentElement.style.setProperty('--left-split', leftPct.toFixed(2) + '%');
          document.documentElement.style.setProperty('--bottom-split', bottomPct.toFixed(2) + '%');
          document.documentElement.style.setProperty('--project-left-split', projectLeftPct.toFixed(2) + '%');
      }

    function clampLeft(pct) {
        const width = Math.max(1, el.workspace.clientWidth);
        return clamp(pct, CFG.minLeft / width * 100, 100 - CFG.minRight / width * 100);
    }

      function clampBottom(pct) {
          const height = Math.max(1, el.workspace.clientHeight);
          return clamp(pct, CFG.minBottom / height * 100, 100 - CFG.minTop / height * 100);
      }

      function isProjectStacked() {
          return window.innerWidth <= CFG.projectCompact;
      }

      function clampProjectLeft(pct) {
          const width = Math.max(1, el.projectBody ? el.projectBody.clientWidth : 1);
          return clamp(
              pct,
              CFG.minProjectLeft / width * 100,
              100 - CFG.minProjectRight / width * 100
          );
      }

    function setActivePane(pane) {
        activePane = pane;
        if (!isCompact) return;
        const panes = {
            config: el.panelConfig,
            project: el.panelProject,
            console: el.panelConsole
        };
        Object.keys(panes).forEach((key) => panes[key].classList.toggle('active', key === pane));
        el.navBtns.forEach((btn) => btn.classList.toggle('active', btn.getAttribute('data-pane') === pane));
    }

    function syncCompact() {
        isCompact = window.innerWidth < CFG.compact;
        document.body.classList.toggle('compact', isCompact);
        if (!isCompact) {
            [el.panelConfig, el.panelProject, el.panelConsole].forEach((pane) => pane.classList.remove('active'));
            return;
        }
        setActivePane(activePane);
    }

      function setupSplitters() {
          el.splitV.addEventListener('mousedown', (event) => {
              if (isCompact) return;
            const startX = event.clientX;
            const width = el.workspace.clientWidth;
            const start = width * leftPct / 100;
            document.body.classList.add('is-resizing-vertical');
            const onMove = (moveEvent) => {
                leftPct = clampLeft((start + (moveEvent.clientX - startX)) / width * 100);
                applySplit();
            };
            const onUp = () => {
                document.body.classList.remove('is-resizing-vertical');
                saveSplit();
                document.removeEventListener('mousemove', onMove);
                document.removeEventListener('mouseup', onUp);
            };
            document.addEventListener('mousemove', onMove);
            document.addEventListener('mouseup', onUp);
        });

          el.splitH.addEventListener('mousedown', (event) => {
              if (isCompact) return;
            const startY = event.clientY;
            const height = el.workspace.clientHeight;
            const start = height * bottomPct / 100;
            document.body.classList.add('is-resizing-horizontal');
            const onMove = (moveEvent) => {
                bottomPct = clampBottom((start + (startY - moveEvent.clientY)) / height * 100);
                applySplit();
            };
            const onUp = () => {
                document.body.classList.remove('is-resizing-horizontal');
                saveSplit();
                document.removeEventListener('mousemove', onMove);
                document.removeEventListener('mouseup', onUp);
            };
              document.addEventListener('mousemove', onMove);
              document.addEventListener('mouseup', onUp);
          });

          el.projectSplit.addEventListener('mousedown', (event) => {
              if (isProjectStacked()) return;
              const startX = event.clientX;
              const width = el.projectBody.clientWidth;
              const start = width * projectLeftPct / 100;
              document.body.classList.add('is-resizing-project');
              const onMove = (moveEvent) => {
                  projectLeftPct = clampProjectLeft((start + (moveEvent.clientX - startX)) / width * 100);
                  applySplit();
              };
              const onUp = () => {
                  document.body.classList.remove('is-resizing-project');
                  saveSplit();
                  document.removeEventListener('mousemove', onMove);
                  document.removeEventListener('mouseup', onUp);
              };
              document.addEventListener('mousemove', onMove);
              document.addEventListener('mouseup', onUp);
          });
      }

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', 'light');
        el.themeIcon.className = 'ui-icon fas fa-satellite-dish';
        el.themeText.textContent = 'Telemetry';
        localStorage.setItem(K.theme, 'light');
    }

    function applySavedTheme() {
        applyTheme('light');
    }

    function buildSchemaFieldMap() {
        schemaFieldMap = Object.create(null);
        if (!schema || !Array.isArray(schema.sections)) return;
        schema.sections.forEach((section) => {
            (section.fields || []).forEach((field) => {
                schemaFieldMap[keyOf(field.path)] = field;
            });
        });
    }

    function getSchemaField(path) {
        return schemaFieldMap[keyOf(path)] || null;
    }

    function getFieldDefault(field) {
        if (schema && schema.defaults) {
            const value = getPath(schema.defaults, field.path);
            if (value !== undefined) return clone(value);
        }
        return clone(field.default);
    }

    function getFieldOptions(field) {
        const source = Array.isArray(field.options) ? field.options : (Array.isArray(field.enum) ? field.enum : []);
        return source.map((item) => {
            if (isObj(item)) {
                return {
                    value: item.value,
                    label: item.label == null ? String(item.value) : String(item.label)
                };
            }
            return {
                value: item,
                label: String(item)
            };
        });
    }

    function sameValue(left, right) {
        return JSON.stringify(left) === JSON.stringify(right);
    }

    function clampNumber(value, min, max) {
        let next = value;
        if (typeof min === 'number') next = Math.max(min, next);
        if (typeof max === 'number') next = Math.min(max, next);
        return next;
    }

    function formatFieldWarning(field, reason) {
        const label = keyOf(field.path);
        switch (reason) {
            case 'required':
                return label + ' (missing required value)';
            case 'range':
                return label + ' (out of range)';
            case 'option':
                return label + ' (invalid option)';
            default:
                return label + ' (type mismatch)';
        }
    }

    function matchesFieldType(field, value) {
        if (value === undefined) return true;
        switch (field.type) {
            case 'boolean':
                return typeof value === 'boolean';
            case 'int':
                return typeof value === 'number' && Number.isInteger(value);
            case 'float':
                return typeof value === 'number' && Number.isFinite(value);
            case 'list':
                return Array.isArray(value) && value.every((item) => typeof item === 'string');
            default:
                return typeof value === 'string';
        }
    }

    function normalizeFieldValue(field, value) {
        const fallback = getFieldDefault(field);
        if (value === undefined) {
            return { value: fallback, warning: null };
        }
        if (!matchesFieldType(field, value)) {
            return { value: fallback, warning: 'type' };
        }

        let next = clone(value);
        let warning = null;

        if (field.required) {
            const missingString = field.type === 'string' && !String(next || '').trim();
            const missingList = field.type === 'list' && (!Array.isArray(next) || next.length === 0);
            if (missingString || missingList) {
                return { value: fallback, warning: 'required' };
            }
        }

        if ((field.type === 'int' || field.type === 'float') && typeof next === 'number') {
            const clamped = clampNumber(next, field.min, field.max);
            if (clamped !== next) {
                next = clamped;
                warning = 'range';
            }
        }

        const options = getFieldOptions(field);
        if (options.length && !options.some((option) => option.value === next)) {
            return { value: fallback, warning: 'option' };
        }

        return { value: next, warning: warning };
    }

    function validateConfigDocument(candidate) {
        if (!isObj(candidate)) throw new Error('Config file must contain a JSON object');
        const merged = merge(schema.defaults, candidate);
        const warnings = [];
        schema.sections.forEach((section) => {
            (section.fields || []).forEach((field) => {
                const normalized = normalizeFieldValue(field, getPath(merged, field.path));
                if (normalized.warning) {
                    warnings.push(formatFieldWarning(field, normalized.warning));
                }
                setPath(merged, field.path, normalized.value);
            });
        });
        return { document: merged, warnings: warnings.sort() };
    }

    function syncUploadBoundFields() {
        if (!configDoc) return;
        if (uploadState.inputFileName) {
            setPath(configDoc, ['Settings', 'Input'], uploadState.inputFileName);
        }
        if (uploadState.libraryFiles.length) {
            setPath(configDoc, ['Settings', 'Libraries'], uploadState.libraryFiles.slice());
        }
    }

    function setConfigDocument(next, options) {
        const opts = options || {};
        configDoc = next ? clone(next) : clone(schema.defaults);
        if (!opts.keepFileName) currentConfigName = opts.fileName || 'config.json';
        syncUploadBoundFields();
        renderConfig();
    }

    function resetProjectState() {
        project.tree = { input: [], output: [] };
        project.meta = {
            input: { available: false, classCount: 0 },
            output: { available: false, classCount: 0 }
        };
        project.tabs = { input: [], output: [] };
        project.active = { input: null, output: null };
        project.selected = { input: null, output: null };
    }

    function updateMetaLabel() {
        const meta = project.meta[scope];
        const name = scope === 'input' ? 'Input' : 'Output';
        el.meta.textContent = meta.available ? (name + ': ' + meta.classCount + ' classes') : (name + ': No data');
    }

    function readValue(node) {
        const type = node.getAttribute('data-config-type');
        if (type === 'boolean') return !!node.checked;
        if (type === 'int') return parseInt(node.value, 10) || 0;
        if (type === 'float') return parseFloat(node.value) || 0;
        if (type === 'list') return node.value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
        return node.value;
    }

    function onField(node) {
        if (!configDoc) return;
        const path = (node.getAttribute('data-config-path') || '').split('.').filter(Boolean);
        if (!path.length) return;
        const field = getSchemaField(path);
        const rawValue = readValue(node);
        if (!field) {
            setPath(configDoc, path, rawValue);
            return;
        }
        const normalized = normalizeFieldValue(field, rawValue);
        setPath(configDoc, path, normalized.value);
        if (!sameValue(rawValue, normalized.value)) {
            renderConfig();
        }
    }

    function getFieldMeta(field) {
        const parts = [];
        if (field.required) parts.push('Required');
        if (typeof field.min === 'number' || typeof field.max === 'number') {
            const min = typeof field.min === 'number' ? field.min : '-inf';
            const max = typeof field.max === 'number' ? field.max : '+inf';
            parts.push('Range: ' + min + ' - ' + max);
        }
        if (field.readOnly && field.fileBinding) {
            parts.push('Managed by uploaded ' + field.fileBinding + ' files');
        }
        return parts;
    }

    function renderField(field, options) {
        const opts = options || {};
        const value = getPath(configDoc, field.path);
        const id = idOf(field.path);
        const pathKey = keyOf(field.path);
        const label = esc(opts.rawLabel ? field.key : (field.label || field.key));
        const disabled = field.readOnly ? ' disabled' : '';
        const placeholder = field.placeholder ? ' placeholder="' + esc(field.placeholder) + '"' : '';
        const description = '';
        const metaHtml = '';
        const numberAttrs = (typeof field.min === 'number' ? ' min="' + esc(field.min) + '"' : '') +
            (typeof field.max === 'number' ? ' max="' + esc(field.max) + '"' : '') +
            (typeof field.step === 'number' ? ' step="' + esc(field.step) + '"' : '');
        const optionsHtml = getFieldOptions(field);
        const detailHtml = description + metaHtml;
        if (field.type === 'boolean') {
            return '<div class="config-item config-item-boolean"><div class="config-item-head">' +
                '<div class="config-copy"><label class="config-label" for="' + id + '">' + label + '</label>' +
                detailHtml + '</div>' +
                '<label class="toggle-switch' + (value ? ' is-checked' : '') + '" for="' + id + '">' +
                '<input type="checkbox" class="toggle-input" id="' + id + '"' +
                (value ? ' checked' : '') + disabled +
                ' data-config-path="' + esc(pathKey) + '" data-config-type="boolean">' +
                '<span class="toggle-slider"></span></label></div></div>';
        }
        if (optionsHtml.length) {
            return '<div class="config-item config-item-select"><label class="config-label" for="' + id + '">' + label + '</label>' +
                detailHtml + '<div class="config-control">' +
                '<select class="config-input" id="' + id + '"' + disabled +
                ' data-config-path="' + esc(pathKey) + '" data-config-type="' + esc(field.type || 'string') + '">' +
                optionsHtml.map((option) => {
                    const selected = String(option.value) === String(value) ? ' selected' : '';
                    return '<option value="' + esc(option.value) + '"' + selected + '>' + esc(option.label) + '</option>';
                }).join('') + '</select><span class="config-control-icon"><i class="ui-icon fas fa-chevron-down"></i></span></div></div>';
        }
        if (field.type === 'int') {
            return '<div class="config-item config-item-number"><label class="config-label" for="' + id + '">' + label + '</label>' +
                detailHtml + '<div class="config-control">' +
                '<input type="number" class="config-input" id="' + id + '" value="' + esc(value) + '"' + disabled +
                numberAttrs + placeholder +
                ' data-config-path="' + esc(pathKey) + '" data-config-type="int"></div></div>';
        }
        if (field.type === 'float') {
            return '<div class="config-item config-item-number"><label class="config-label" for="' + id + '">' + label + '</label>' +
                detailHtml + '<div class="config-control">' +
                '<input type="number" class="config-input" id="' + id + '" value="' + esc(value) + '"' + disabled +
                (typeof field.step === 'number' ? ' step="' + esc(field.step) + '"' : ' step="0.1"') +
                (typeof field.min === 'number' ? ' min="' + esc(field.min) + '"' : '') +
                (typeof field.max === 'number' ? ' max="' + esc(field.max) + '"' : '') + placeholder +
                ' data-config-path="' + esc(pathKey) + '" data-config-type="float"></div></div>';
        }
        if (field.type === 'list') {
            return '<div class="config-item config-item-list"><label class="config-label" for="' + id + '">' + label + '</label>' +
                detailHtml + '<div class="config-control">' +
                '<textarea class="config-input" rows="3" id="' + id + '"' + disabled + placeholder +
                ' data-config-path="' + esc(pathKey) + '" data-config-type="list">' +
                esc(Array.isArray(value) ? value.join('\n') : '') + '</textarea></div></div>';
        }
        return '<div class="config-item config-item-text"><label class="config-label" for="' + id + '">' + label + '</label>' +
            detailHtml + '<div class="config-control">' +
            '<input type="text" class="config-input" id="' + id + '" value="' + esc(value) + '"' + disabled + placeholder +
            ' data-config-path="' + esc(pathKey) + '" data-config-type="string"></div></div>';
    }

    function renderConfig() {
        if (!schema || !configDoc) {
            el.settingsWrap.innerHTML = '<div class="loading">Loading configuration schema...</div>';
            el.tfWrap.innerHTML = '<div class="loading">Loading configuration schema...</div>';
            return;
        }

        const generalSections = schema.sections
            .filter((section) => section.kind !== 'transformer' && section.key !== 'UI')
            .sort((a, b) => (a.order || 0) - (b.order || 0));
        const generalFields = [];
        generalSections.forEach((section) => {
            (section.fields || []).forEach((field) => generalFields.push(renderField(field)));
        });
        el.settingsWrap.innerHTML = generalFields.length
            ? '<div class="settings-card settings-card-flat"><div class="settings-card-body">' + generalFields.join('') + '</div></div>'
            : '<div class="empty-state">No settings available</div>';

        const grouped = {};
        schema.sections.filter((section) => section.kind === 'transformer').forEach((section) => {
            const category = section.category || 'Miscellaneous';
            if (!grouped[category]) grouped[category] = [];
            grouped[category].push(section);
        });

        let html = '';
        TF_ORDER.forEach((category) => {
            const items = grouped[category] ? grouped[category].slice().sort((a, b) => (a.order || 0) - (b.order || 0)) : null;
            if (!items || !items.length) return;
            const categoryExpanded = expandedCategories[category] !== false;
            html += '<div class="category-group' + (categoryExpanded ? ' expanded' : ' collapsed') + '">' +
                '<div class="category-group-header" data-transformer-category="' + esc(category) + '">' +
                '<span class="category-badge category-' + esc(category) + '">' + esc(category) + '</span>' +
                '<span class="category-actions"><span class="category-count">' + items.length + ' modules</span>' +
                '<span class="category-chevron">' + (categoryExpanded ? '&#8722;' : '&#43;') + '</span></span></div>' +
                '<div class="category-group-body' + (categoryExpanded ? ' open' : '') + '">';
            items.forEach((section) => {
                const enabledField = (section.fields || []).find((field) => field.key === 'Enabled');
                const enabled = enabledField ? !!getPath(configDoc, enabledField.path) : false;
                const fields = (section.fields || []).filter((field) => field.key !== 'Enabled');
                const isExpanded = !!expanded[section.key];
                html += '<div class="transformer-card' + (enabled ? ' enabled' : '') + (isExpanded ? ' expanded' : '') + '">' +
                    '<div class="transformer-header" data-transformer-header="' + esc(section.key) + '">' +
                    '<div class="transformer-summary"><div class="transformer-name-row"><div class="transformer-name">' + esc(section.key) +
                    '</div><span class="transformer-pill">' + (enabled ? 'Live' : 'Standby') + '</span></div>' +
                    '<div class="transformer-caption">' + (fields.length ? (fields.length + ' configurable controls') : 'Toggle module state only') +
                    '</div></div><div class="transformer-actions">' +
                    '<button type="button" class="transformer-toggle ' + (enabled ? 'active' : '') +
                    '" data-transformer-toggle="' + esc(section.key) + '" aria-label="Toggle ' + esc(section.key) + '">' +
                    '<span class="transformer-toggle-thumb"></span></button>' +
                    '<span class="transformer-chevron">' + (isExpanded ? '&#8722;' : '&#43;') + '</span>' +
                    '</div></div>';
                if (fields.length) {
                    html += '<div class="transformer-settings ' + (isExpanded ? 'open' : '') + '">' +
                        fields.map((field) => renderField(field, { rawLabel: true })).join('') + '</div>';
                }
                html += '</div>';
            });
            html += '</div></div>';
        });
        el.tfWrap.innerHTML = html || '<div class="empty-state">No transformers available</div>';
    }

    function syncSectionHeader(header, body) {
        const label = header.getAttribute('data-label') || header.textContent.replace(/^[\u25B6\u25BC]\s*/, '').trim();
        const isCollapsed = body.classList.contains('collapsed');
        header.innerHTML = '<span class="section-kicker">' + esc(label) + '</span>' +
            '<span class="section-chevron">' + (isCollapsed ? '&#9656;' : '&#9662;') + '</span>';
    }

    function findSection(key) {
        return schema && schema.sections.find((section) => section.key === key);
    }

    function toggleTransformer(key) {
        const section = findSection(key);
        if (!section || !configDoc) return;
        const enabledField = (section.fields || []).find((field) => field.key === 'Enabled');
        if (!enabledField) return;
        setPath(configDoc, enabledField.path, !getPath(configDoc, enabledField.path));
        renderConfig();
    }

    function toggleTransformerSettings(key) {
        expanded[key] = !expanded[key];
        renderConfig();
    }

    function toggleTransformerCategory(category) {
        expandedCategories[category] = expandedCategories[category] === false;
        renderConfig();
    }

    function buildProjectTree(classes) {
        const root = {};
        classes.forEach((className) => {
            let cur = root;
            className.split('/').forEach((part, index, parts) => {
                if (index === parts.length - 1) {
                    if (!cur.__files) cur.__files = [];
                    cur.__files.push(className);
                } else {
                    if (!cur[part]) cur[part] = {};
                    cur = cur[part];
                }
            });
        });
        return root;
    }

    function renderNode(node, depth) {
        let html = '';
        const indent = '<span class="tree-indent"></span>'.repeat(depth);
        Object.keys(node).filter((key) => key !== '__files').sort().forEach((folder) => {
            const id = 'ptree-' + Math.random().toString(36).slice(2);
            html += '<div class="tree-node tree-folder" data-folder-children="' + id + '">' + indent +
                '<span class="icon">&#9662;</span><span class="folder-icon"><i class="ui-icon far fa-folder-open"></i></span>' +
                '<span class="label">' + esc(folder) + '</span></div>';
            html += '<div class="tree-children" id="' + id + '">' + renderNode(node[folder], depth + 1) + '</div>';
        });
        (node.__files || []).sort().forEach((full) => {
            const active = project.selected[scope] === full ? ' active' : '';
            const name = full.split('/').pop() || full;
            html += '<div class="tree-node tree-class' + active + '" data-class-enc="' + encodeURIComponent(full) + '">' +
                indent + '<span class="tree-indent"></span><span class="icon file-icon"><i class="ui-icon far fa-file-code"></i></span>' +
                '<span class="label">' + esc(name) + '</span></div>';
        });
        return html;
    }

    function renderTree() {
        const classes = project.tree[scope];
        if (!classes.length) {
            el.tree.innerHTML = '<div class="empty-state">No classes available</div>';
            return;
        }
        el.tree.innerHTML = renderNode(buildProjectTree(classes), 0);
    }

    function renderTabs() {
        const tabs = project.tabs[scope];
        const active = project.active[scope];
        el.tabs.innerHTML = tabs.map((tab) => {
            const encoded = encodeURIComponent(tab.className);
            return '<div class="editor-tab' + (tab.className === active ? ' active' : '') + '" data-class-enc="' +
                encoded + '"><span class="editor-tab-icon"><i class="ui-icon far fa-file-code"></i></span>' +
                '<span class="editor-tab-title">' + esc(tab.title) + '</span>' +
                '<button class="editor-tab-close" data-close-enc="' + encoded + '" aria-label="Close tab">&times;</button></div>';
        }).join('');
    }

      function renderCode() {
          const className = project.active[scope];
          if (!className) {
              el.code.innerHTML = '<div class="empty-state">Select a class in the file tree</div>';
              return;
        }
        const tab = project.tabs[scope].find((item) => item.className === className);
        if (!tab) return;
        if (tab.loading) {
            el.code.innerHTML = '<div class="loading">Decompiling ' + esc(tab.title) + '...</div>';
            return;
        }
        if (tab.error) {
            el.code.innerHTML = '<div class="empty-state">' + esc(tab.error) + '</div>';
            return;
        }
        if (!tab.code) {
            el.code.innerHTML = '<div class="empty-state">No code available</div>';
            return;
          }
          const linesHtml = tab.code.split('\n').map((line, index) => {
            return '<tr class="code-line"><td class="line-no">' + (index + 1) +
                '</td><td class="line-text"><span class="line-code">' + renderHighlightedLine(line) + '</span></td></tr>';
          }).join('');
          el.code.innerHTML = '<table class="code-sheet"><tbody>' + linesHtml + '</tbody></table>';
          syncCodeWidth();
      }

      function syncCodeWidth() {
          const sheet = el.code.querySelector('.code-sheet');
          if (!sheet) return;
          const lines = Array.from(sheet.querySelectorAll('.code-line'));
          if (!lines.length) {
              sheet.style.width = '';
              el.code.scrollLeft = 0;
              return;
          }
          let targetWidth = 0;
          lines.forEach((line) => {
              const gutter = line.querySelector('.line-no');
              const text = line.querySelector('.line-text');
              const code = line.querySelector('.line-code');
              if (!gutter || !text || !code) return;
              const textStyle = window.getComputedStyle(text);
              const width =
                  Math.ceil(gutter.getBoundingClientRect().width) +
                  Math.ceil(code.getBoundingClientRect().width) +
                  Math.ceil(parseFloat(textStyle.paddingLeft || '0')) +
                  Math.ceil(parseFloat(textStyle.paddingRight || '0')) + 2;
              targetWidth = Math.max(targetWidth, width);
          });
          if (!targetWidth) {
              sheet.style.width = '';
              return;
          }
          sheet.style.width = Math.ceil(targetWidth) + 'px';
      }

    async function refreshMeta(nextScope) {
        if (!sessionId) {
            project.meta[nextScope] = { available: false, classCount: 0 };
            return;
        }
        try {
            const result = await API.getProjectMeta(sessionId, nextScope);
            project.meta[nextScope] = result.status === 'ok'
                ? { available: !!result.available, classCount: result.classCount || 0 }
                : { available: false, classCount: 0 };
        } catch (_) {
            project.meta[nextScope] = { available: false, classCount: 0 };
        }
    }

    async function refreshTree(nextScope) {
        if (!sessionId) {
            project.tree[nextScope] = [];
            renderTree();
            updateMetaLabel();
            return;
        }
        try {
            const result = await API.getProjectTree(sessionId, nextScope);
            project.tree[nextScope] = result.status === 'ok' && Array.isArray(result.classes) ? result.classes.slice().sort() : [];
        } catch (_) {
            project.tree[nextScope] = [];
        }
        renderTree();
        updateMetaLabel();
    }

    async function loadSource(nextScope, className) {
        const tab = project.tabs[nextScope].find((item) => item.className === className);
        if (!tab || !sessionId) return;
        tab.loading = true;
        tab.error = '';
        renderCode();
        try {
            const result = await API.getProjectSource(sessionId, nextScope, className);
            tab.code = result.status === 'ok' ? (result.code || '') : '';
            tab.error = result.status === 'ok' ? '' : (result.message || 'Failed to load source');
        } catch (e) {
            tab.error = e.message || 'Failed to load source';
        }
        tab.loading = false;
        renderCode();
    }

    function openClass(className) {
        let tab = project.tabs[scope].find((item) => item.className === className);
        if (!tab) {
            tab = {
                className: className,
                title: className.split('/').pop() || className,
                loading: false,
                code: '',
                error: ''
            };
            project.tabs[scope].push(tab);
        }
        project.active[scope] = className;
        renderTabs();
        renderCode();
        if (!tab.code && !tab.error && !tab.loading) {
            loadSource(scope, className);
        }
    }

    async function setScope(nextScope) {
        if (nextScope !== 'input' && nextScope !== 'output') return;
        scope = nextScope;
        el.btnScopeIn.classList.toggle('active', nextScope === 'input');
        el.btnScopeOut.classList.toggle('active', nextScope === 'output');
        await refreshMeta(nextScope);
        await refreshTree(nextScope);
        renderTabs();
        renderCode();
        updateMetaLabel();
    }

    function syncSession(session) {
        if (!session) return;
        running = session.status === 'RUNNING';
        el.progressWrap.classList.toggle('active', running || session.status === 'COMPLETED' || session.status === 'ERROR');
        if (session.inputFileName) uploadState.inputFileName = session.inputFileName;
        if (Array.isArray(session.libraryFiles)) uploadState.libraryFiles = session.libraryFiles.slice();
        if (Array.isArray(session.assetFiles)) uploadState.assetFiles = session.assetFiles.slice();
        syncUploadBoundFields();
        if (configDoc) renderConfig();

        if (running) {
            setStatus('running', session.currentStep || 'Running...');
        } else if (session.status === 'COMPLETED') {
            setStatus('completed', 'Completed');
        } else if (session.status === 'ERROR') {
            setStatus('error', session.error || 'Error');
        } else if (session.inputUploaded) {
            setStatus('ready', 'Ready');
        } else {
            setStatus('idle', 'Idle');
        }

        el.btnObf.disabled = running || !session.inputUploaded;
        el.btnDl.disabled = !session.outputAvailable;
    }

    async function ensureSession() {
        if (sessionId) return sessionId;
        const created = await API.createSession();
        if (created.status !== 'ok' || !created.sessionId) {
            throw new Error(created.message || 'Failed to create session');
        }
        sessionId = created.sessionId;
        syncSession(created.session);
        connectWs();
        return sessionId;
    }

    function reconnectSocket(kind) {
        if (!sessionId) return;
        setTimeout(() => {
            if (!sessionId) return;
            if (kind === 'console') {
                if (consoleWs) consoleWs.close();
                connectConsoleWs();
            } else {
                if (progressWs) progressWs.close();
                connectProgressWs();
            }
        }, 2000);
    }

    function connectConsoleWs() {
        if (!sessionId) return;
        consoleWs = API.connectConsole(sessionId, (data) => {
            if (data.type === 'clear') {
                el.console.innerHTML = '';
                return;
            }
            if (data.type === 'log' && data.message) {
                const level = data.message.includes('ERROR') ? 'error' : (data.message.includes('WARN') ? 'warn' : 'info');
                log(data.message, level);
            }
        });
        consoleWs.onclose = () => reconnectSocket('console');
    }

    function connectProgressWs() {
        if (!sessionId) return;
        progressWs = API.connectProgress(sessionId, async (data) => {
            if (data.progress !== undefined) {
                el.progressBar.style.width = data.progress + '%';
                el.progressText.textContent = data.progress + '%';
            }
            if (data.step) {
                el.statusText.textContent = data.step;
            }
            if (data.status === 'RUNNING') {
                running = true;
            }
            if (data.step === 'Completed') {
                running = false;
                setStatus('completed', 'Completed');
                el.btnObf.disabled = false;
                el.btnDl.disabled = false;
                await refreshMeta('output');
                await refreshTree('output');
                if (scope === 'output') {
                    renderTree();
                    renderTabs();
                    renderCode();
                }
            } else if (data.error) {
                running = false;
                setStatus('error', 'Error');
                el.btnObf.disabled = false;
                log('Obfuscation failed: ' + data.error, 'error');
            }
        });
        progressWs.onclose = () => reconnectSocket('progress');
    }

    function connectWs() {
        connectConsoleWs();
        connectProgressWs();
    }

    async function uploadGeneratedConfig() {
        await ensureSession();
        const file = new File(
            [JSON.stringify(configDoc, null, 2)],
            currentConfigName || 'config.json',
            { type: 'application/json' }
        );
        const result = await API.uploadConfig(sessionId, file);
        syncSession(result.session);
        return result;
    }

    async function handleLocalConfigOpen(event) {
        const file = event.target.files && event.target.files[0];
        if (!file) return;
        try {
            const text = await file.text();
            const parsed = JSON.parse(text);
            const normalized = validateConfigDocument(parsed);
            currentConfigName = file.name || 'config.json';
            setConfigDocument(normalized.document, { fileName: currentConfigName });
            log('Loaded local config: ' + currentConfigName, 'success');
            if (normalized.warnings.length) {
                log('Adjusted imported config values: ' + normalized.warnings.join(', '), 'warn');
            }
        } catch (e) {
            log('Failed to open local config: ' + (e.message || e), 'error');
        }
        el.configFileInput.value = '';
    }

    function downloadCurrentConfig() {
        if (!configDoc) return;
        const blob = new Blob([JSON.stringify(configDoc, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = currentConfigName || 'config.json';
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
        log('Saved local config: ' + (currentConfigName || 'config.json'), 'success');
    }

    function resetLocalConfig() {
        currentConfigName = 'config.json';
        setConfigDocument(clone(schema.defaults), { fileName: currentConfigName });
        log('Config reset to schema defaults', 'info');
    }

    async function uploadInputFile(event) {
        const file = event.target.files && event.target.files[0];
        if (!file) return;
        setStatus('uploading', 'Uploading...');
        log('Uploading input JAR: ' + file.name, 'info');
        try {
            await ensureSession();
            const result = await API.uploadInput(sessionId, file);
            uploadState.inputFileName = result.fileName || file.name;
            syncSession(result.session);
            resetProjectState();
            await refreshMeta('input');
            await refreshMeta('output');
            await refreshTree('input');
            project.tree.output = [];
            await setScope('input');
            el.btnDl.disabled = true;
            log('Uploaded ' + (result.fileName || file.name) + ' (' + (result.classCount || 0) + ' classes)', 'success');
        } catch (e) {
            setStatus('error', 'Upload Failed');
            log('Upload failed: ' + (e.message || e), 'error');
        }
        el.inputFileInput.value = '';
    }

    async function uploadLibraries(event) {
        const files = Array.from(event.target.files || []);
        if (!files.length) return;
        log('Uploading libraries: ' + files.map((file) => file.name).join(', '), 'info');
        try {
            await ensureSession();
            const result = await API.uploadLibraries(sessionId, files);
            if (Array.isArray(result.files)) uploadState.libraryFiles = result.files.slice();
            syncSession(result.session);
            log('Uploaded ' + (result.count || files.length) + ' library file(s)', 'success');
        } catch (e) {
            log('Failed to upload libraries: ' + (e.message || e), 'error');
        }
        el.librariesFileInput.value = '';
    }

    async function uploadAssets(event) {
        const files = Array.from(event.target.files || []);
        if (!files.length) return;
        log('Uploading assets: ' + files.map((file) => file.name).join(', '), 'info');
        try {
            await ensureSession();
            const result = await API.uploadAssets(sessionId, files);
            if (Array.isArray(result.files)) uploadState.assetFiles = result.files.slice();
            syncSession(result.session);
            log('Uploaded ' + (result.count || files.length) + ' asset file(s)', 'success');
        } catch (e) {
            log('Failed to upload assets: ' + (e.message || e), 'error');
        }
        el.assetsFileInput.value = '';
    }

    async function obfuscate() {
        if (running) return;
        if (!sessionId) {
            try {
                await ensureSession();
            } catch (e) {
                log('Failed to create session: ' + (e.message || e), 'error');
                return;
            }
        }
        if (!uploadState.inputFileName) {
            log('Please upload the input JAR before obfuscating', 'warn');
            return;
        }
        try {
            await uploadGeneratedConfig();
        } catch (e) {
            setStatus('error', 'Config Error');
            log('Failed to upload config: ' + (e.message || e), 'error');
            return;
        }

        running = true;
        setStatus('running', 'Running...');
        el.progressWrap.classList.add('active');
        el.progressBar.style.width = '0%';
        el.progressText.textContent = '0%';
        el.btnObf.disabled = true;
        el.btnDl.disabled = true;
        log('Starting obfuscation...', 'info');
        try {
            const result = await API.startObfuscation(sessionId);
            if (result.status !== 'started') {
                throw new Error(result.message || 'Failed to start obfuscation');
            }
        } catch (e) {
            running = false;
            el.btnObf.disabled = false;
            setStatus('error', 'Error');
            log('Failed to start obfuscation: ' + (e.message || e), 'error');
        }
    }

    function bindConfigInteractions() {
        document.querySelectorAll('.config-section-header').forEach((header) => {
            const id = header.getAttribute('data-toggle');
            const body = id ? document.getElementById(id) : null;
            if (body) syncSectionHeader(header, body);
            header.addEventListener('click', () => {
                if (!body) return;
                body.classList.toggle('collapsed');
                syncSectionHeader(header, body);
            });
        });

        el.settingsWrap.addEventListener('change', (event) => {
            const node = event.target.closest('[data-config-path]');
            if (node) onField(node);
        });

        el.tfWrap.addEventListener('change', (event) => {
            const node = event.target.closest('[data-config-path]');
            if (node) onField(node);
        });

        el.tfWrap.addEventListener('click', (event) => {
            const toggle = event.target.closest('[data-transformer-toggle]');
            if (toggle) {
                toggleTransformer(toggle.getAttribute('data-transformer-toggle'));
                return;
            }
            const category = event.target.closest('[data-transformer-category]');
            if (category) {
                toggleTransformerCategory(category.getAttribute('data-transformer-category'));
                return;
            }
            const header = event.target.closest('[data-transformer-header]');
            if (header) toggleTransformerSettings(header.getAttribute('data-transformer-header'));
        });
    }

    function bindEvents() {
        el.configFileInput.addEventListener('change', handleLocalConfigOpen);
        el.inputFileInput.addEventListener('change', uploadInputFile);
        el.librariesFileInput.addEventListener('change', uploadLibraries);
        el.assetsFileInput.addEventListener('change', uploadAssets);
        el.btnSaveConfig.addEventListener('click', downloadCurrentConfig);
        el.btnObf.addEventListener('click', obfuscate);
        el.btnDl.addEventListener('click', () => {
            if (!sessionId) return;
            window.location.href = API.getDownloadUrl(sessionId);
        });
        document.getElementById('btn-clear-console').addEventListener('click', () => {
            el.console.innerHTML = '';
        });
        document.getElementById('btn-reset-config').addEventListener('click', resetLocalConfig);
        el.btnTheme.addEventListener('click', () => {
            log('Aurora dashboard profile is locked to the high-tech light preset.', 'info');
        });
        el.btnScopeIn.addEventListener('click', () => setScope('input'));
        el.btnScopeOut.addEventListener('click', () => setScope('output'));
        el.navBtns.forEach((btn) => btn.addEventListener('click', () => {
            const pane = btn.getAttribute('data-pane');
            if (pane) setActivePane(pane);
        }));
        el.configPanel.addEventListener('wheel', (event) => {
            const panel = el.configPanel;
            if (!panel || panel.scrollHeight <= panel.clientHeight) return;
            if (event.ctrlKey || event.metaKey) return;
            const previous = panel.scrollTop;
            panel.scrollTop += event.deltaY;
            if (panel.scrollTop !== previous) {
                event.preventDefault();
            }
        }, { passive: false });
        el.tree.addEventListener('click', (event) => {
            const folder = event.target.closest('.tree-folder');
            if (folder) {
                const id = folder.getAttribute('data-folder-children');
                const child = id ? document.getElementById(id) : null;
                if (!child) return;
                const hide = child.style.display !== 'none';
                child.style.display = hide ? 'none' : 'block';
                const icon = folder.querySelector('.icon');
                if (icon) icon.innerHTML = hide ? '&#9656;' : '&#9662;';
                return;
            }
            const cls = event.target.closest('.tree-class');
            if (!cls) return;
            const encoded = cls.getAttribute('data-class-enc');
            if (!encoded) return;
            const className = decodeURIComponent(encoded);
            project.selected[scope] = className;
            renderTree();
            openClass(className);
        });
          el.tabs.addEventListener('click', (event) => {
            const close = event.target.closest('[data-close-enc]');
            if (close) {
                const name = decodeURIComponent(close.getAttribute('data-close-enc'));
                const tabs = project.tabs[scope];
                const index = tabs.findIndex((tab) => tab.className === name);
                if (index >= 0) tabs.splice(index, 1);
                if (project.active[scope] === name) {
                    const next = tabs[index] || tabs[index - 1] || null;
                    project.active[scope] = next ? next.className : null;
                }
                renderTabs();
                renderCode();
                return;
            }
              const tab = event.target.closest('.editor-tab');
              if (!tab) return;
              project.active[scope] = decodeURIComponent(tab.getAttribute('data-class-enc'));
              renderTabs();
              renderCode();
          });
          window.addEventListener('resize', () => {
              syncCompact();
              if (!isCompact) {
                  leftPct = clampLeft(leftPct);
                  bottomPct = clampBottom(bottomPct);
              }
                if (!isProjectStacked()) {
                    projectLeftPct = clampProjectLeft(projectLeftPct);
                }
                applySplit();
            });
        setupSplitters();
        bindConfigInteractions();
    }

      async function start() {
          applyAccessTier();
          applySavedTheme();
          restoreSplit();
          leftPct = clampLeft(leftPct);
          bottomPct = clampBottom(bottomPct);
          if (!isProjectStacked()) {
              projectLeftPct = clampProjectLeft(projectLeftPct);
          }
          applySplit();
          syncCompact();
          bindEvents();
        try {
            await ensureSession();
            schema = await API.getSchema();
            if (!schema || !schema.defaults || !Array.isArray(schema.sections)) {
                throw new Error('Invalid config schema');
            }
            buildSchemaFieldMap();
            setConfigDocument(clone(schema.defaults), { fileName: currentConfigName });
            await setScope('input');
            log('Editor ready. Upload a JAR and configure obfuscation locally.', 'info');
        } catch (e) {
            setStatus('error', 'Initialization Failed');
            log('Initialization failed: ' + (e.message || e), 'error');
        }
    }

    document.addEventListener('DOMContentLoaded', start);
})();
