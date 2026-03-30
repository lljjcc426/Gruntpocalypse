/**
 * Grunt Web UI - Session API Client
 */
const API = {
    base: '',

    async requestJson(path, options) {
        const res = await fetch(this.base + path, options);
        const text = await res.text();
        let payload = {};
        if (text) {
            try {
                payload = JSON.parse(text);
            } catch (_) {
                payload = { message: text };
            }
        }
        if (!res.ok) {
            const err = new Error(payload.message || payload.error || res.statusText || 'Request failed');
            err.status = res.status;
            err.payload = payload;
            throw err;
        }
        return payload;
    },

    get(path) {
        return this.requestJson(path, { method: 'GET' });
    },

    post(path, body) {
        return this.requestJson(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body || {})
        });
    },

    upload(path, fieldName, files) {
        const formData = new FormData();
        Array.from(files || []).forEach((file) => formData.append(fieldName, file));
        return this.requestJson(path, {
            method: 'POST',
            body: formData
        });
    },

    createSession() {
        return this.post('/api/session/create', {});
    },

    getSchema() {
        return this.get('/schema/config-editor.schema.json');
    },

    uploadConfig(sessionId, file) {
        return this.upload('/api/session/' + encodeURIComponent(sessionId) + '/config', 'file', [file]);
    },

    uploadInput(sessionId, file) {
        return this.upload('/api/session/' + encodeURIComponent(sessionId) + '/input', 'file', [file]);
    },

    uploadLibraries(sessionId, files) {
        return this.upload('/api/session/' + encodeURIComponent(sessionId) + '/libraries', 'files', files);
    },

    uploadAssets(sessionId, files) {
        return this.upload('/api/session/' + encodeURIComponent(sessionId) + '/assets', 'files', files);
    },

    startObfuscation(sessionId) {
        return this.post('/api/session/' + encodeURIComponent(sessionId) + '/obfuscate', {});
    },

    getStatus(sessionId) {
        return this.get('/api/session/' + encodeURIComponent(sessionId) + '/status');
    },

    getLogs(sessionId) {
        return this.get('/api/session/' + encodeURIComponent(sessionId) + '/logs');
    },

    getDownloadUrl(sessionId) {
        return this.base + '/api/session/' + encodeURIComponent(sessionId) + '/download';
    },

    getProjectMeta(sessionId, scope) {
        const sid = encodeURIComponent(sessionId);
        const s = encodeURIComponent(scope || 'input');
        return this.get('/api/session/' + sid + '/project/meta?scope=' + s);
    },

    getProjectTree(sessionId, scope) {
        const sid = encodeURIComponent(sessionId);
        const s = encodeURIComponent(scope || 'input');
        return this.get('/api/session/' + sid + '/project/tree?scope=' + s);
    },

    getProjectSource(sessionId, scope, className) {
        const sid = encodeURIComponent(sessionId);
        const s = encodeURIComponent(scope || 'input');
        const c = encodeURIComponent(className || '');
        return this.get('/api/session/' + sid + '/project/source?scope=' + s + '&class=' + c);
    },

    connectConsole(sessionId, onMessage) {
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const sid = encodeURIComponent(sessionId);
        const ws = new WebSocket(protocol + '//' + location.host + '/ws/console?sessionId=' + sid);
        ws.onmessage = (event) => {
            try {
                onMessage(JSON.parse(event.data));
            } catch (_) {
                onMessage({ type: 'log', message: event.data });
            }
        };
        ws.onerror = (e) => console.error('Console WS error:', e);
        return ws;
    },

    connectProgress(sessionId, onProgress) {
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const sid = encodeURIComponent(sessionId);
        const ws = new WebSocket(protocol + '//' + location.host + '/ws/progress?sessionId=' + sid);
        ws.onmessage = (event) => {
            try {
                onProgress(JSON.parse(event.data));
            } catch (e) {
                console.error('Progress parse error:', e);
            }
        };
        ws.onerror = (e) => console.error('Progress WS error:', e);
        return ws;
    }
};
