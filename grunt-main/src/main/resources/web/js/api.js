/**
 * Grunt Web UI - Session API Client
 */
const API = {
    base: '',
    sessionProfile: 'RESEARCH',
    routes: {
        createSession: '/api/session/create',
        status: (sessionId) => '/api/session/' + encodeURIComponent(sessionId) + '/status',
        logs: (sessionId) => '/api/session/' + encodeURIComponent(sessionId) + '/logs',
        config: (sessionId) => '/api/session/' + encodeURIComponent(sessionId) + '/config',
        input: (sessionId) => '/api/session/' + encodeURIComponent(sessionId) + '/input',
        libraries: (sessionId) => '/api/session/' + encodeURIComponent(sessionId) + '/libraries',
        assets: (sessionId) => '/api/session/' + encodeURIComponent(sessionId) + '/assets',
        obfuscate: (sessionId) => '/api/session/' + encodeURIComponent(sessionId) + '/obfuscate',
        download: (sessionId) => '/api/session/' + encodeURIComponent(sessionId) + '/download',
        projectMeta: (sessionId, scope) => '/api/session/' + encodeURIComponent(sessionId) + '/project/meta?scope=' + encodeURIComponent(scope || 'input'),
        projectTree: (sessionId, scope) => '/api/session/' + encodeURIComponent(sessionId) + '/project/tree?scope=' + encodeURIComponent(scope || 'input'),
        projectSource: (sessionId, scope, className) =>
            '/api/session/' + encodeURIComponent(sessionId) + '/project/source?scope=' + encodeURIComponent(scope || 'input') + '&class=' + encodeURIComponent(className || ''),
        consoleWs: (sessionId) => '/ws/console?sessionId=' + encodeURIComponent(sessionId),
        progressWs: (sessionId) => '/ws/progress?sessionId=' + encodeURIComponent(sessionId)
    },

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
        return this.post(this.routes.createSession, { profile: this.sessionProfile });
    },

    getSchema() {
        return this.get('/schema/config-editor.schema.json');
    },

    uploadConfig(sessionId, file) {
        return this.upload(this.routes.config(sessionId), 'file', [file]);
    },

    uploadInput(sessionId, file) {
        return this.upload(this.routes.input(sessionId), 'file', [file]);
    },

    uploadLibraries(sessionId, files) {
        return this.upload(this.routes.libraries(sessionId), 'files', files);
    },

    uploadAssets(sessionId, files) {
        return this.upload(this.routes.assets(sessionId), 'files', files);
    },

    startObfuscation(sessionId) {
        return this.post(this.routes.obfuscate(sessionId), {});
    },

    getStatus(sessionId) {
        return this.get(this.routes.status(sessionId));
    },

    getLogs(sessionId) {
        return this.get(this.routes.logs(sessionId));
    },

    getDownloadUrl(sessionId) {
        return this.base + this.routes.download(sessionId);
    },

    getProjectMeta(sessionId, scope) {
        return this.get(this.routes.projectMeta(sessionId, scope));
    },

    getProjectTree(sessionId, scope) {
        return this.get(this.routes.projectTree(sessionId, scope));
    },

    getProjectSource(sessionId, scope, className) {
        return this.get(this.routes.projectSource(sessionId, scope, className));
    },

    connectConsole(sessionId, onMessage) {
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const ws = new WebSocket(protocol + '//' + location.host + this.routes.consoleWs(sessionId));
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
        const ws = new WebSocket(protocol + '//' + location.host + this.routes.progressWs(sessionId));
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
