// Real loopback HTTP fixture + production script isolation/Bridge JS; no school or model access.
const assert = require('node:assert/strict');
const { spawn, spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const readline = require('node:readline');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const nativeDir = path.join(root, 'app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/importing/shiguang');
const wrapper = fs.readFileSync(path.join(nativeDir, 'ShiguangScriptExecution.kt'), 'utf8').match(/= """([\s\S]*?)"""\.trimIndent/)[1];
const init = fs.readFileSync(path.join(nativeDir, 'ShiguangBridgeProtocol.kt'), 'utf8').match(/ShiguangBridgeInitScript = """([\s\S]*?)"""\.trimIndent/)[1];
const source = fs.readFileSync(path.join(root, 'scripts/fixtures/ai-edu-smoke-reference.js'), 'utf8');
const python = process.platform === 'win32' ? spawnSync('py', ['-3', '-c', 'import sys; print(sys.executable)'],
    { encoding: 'utf8', windowsHide: true }) : { status: 0, stdout: 'python3' };
assert.equal(python.status, 0, '需要 Python 3');
const server = spawn(python.stdout.trim(),
    [path.join(root, 'scripts/ai-edu-smoke-server.py'), '--port', '0'],
    { windowsHide: true, stdio: ['ignore', 'pipe', 'inherit'] });

(async () => {
    const endpoint = await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(Error('本地测试服务启动超时')), 10000);
        server.once('error', error => { clearTimeout(timer); reject(error); });
        server.once('exit', code => { clearTimeout(timer); reject(Error(`服务提前退出 ${code}`)); });
        readline.createInterface({ input: server.stdout }).once('line', line => {
            clearTimeout(timer); resolve(JSON.parse(line));
        });
    });
    const page = await (await fetch(endpoint.url)).text();
    assert(!page.includes(endpoint.runCode), '页面不能直接泄漏课程与运行码');
    assert((await (await fetch(endpoint.url + '/portal.js')).text()).includes('/api/semester'));
    const expected = await (await fetch(endpoint.url + '/api/semester')).json();
    assert.equal((await fetch(endpoint.url + '/../../AGENTS.md')).status, 404);
    const events = [];
    let complete;
    const finished = new Promise(resolve => { complete = resolve; });
    const window = {
        fetch: (url, options) => fetch(new URL(url, endpoint.url), options),
        _shiguangNativeBridge: { postMessage(raw) {
            const message = JSON.parse(raw);
            events.push(message);
            if (message.callbackId) queueMicrotask(() => window._shiguangNativeCallback(message.callbackId, true, true));
            if (message.action === 'notifyTaskCompletion') complete();
        } }
    };
    window.window = window;
    Object.assign(window, { AbortController, setTimeout, clearTimeout });
    const context = vm.createContext(window);
    vm.runInContext(init, context);
    const rendered = wrapper.replace('${JSONObject.quote(executionToken)}', JSON.stringify('smoke-task'))
        .replace('${JSONObject.quote(source)}', JSON.stringify(source));
    assert.equal(vm.runInContext(rendered, context), 'started');
    let timeout;
    try { await Promise.race([finished, new Promise((_, reject) => { timeout = setTimeout(() => reject(Error('Bridge 完成超时')), 10000); })]); }
    finally { clearTimeout(timeout); }
    const saves = events.filter(event => ['saveCourseConfig', 'savePresetTimeSlots', 'saveImportedCourses', 'notifyTaskCompletion'].includes(event.action));
    assert.deepEqual(saves.map(event => event.action), ['saveCourseConfig', 'savePresetTimeSlots', 'saveImportedCourses', 'notifyTaskCompletion']);
    assert(saves.every(event => event.executionToken === 'smoke-task'));
    assert.deepEqual(JSON.parse(JSON.parse(saves[0].payload).configJsonString), expected.config);
    assert.deepEqual(JSON.parse(JSON.parse(saves[1].payload).timeSlotsJsonString), expected.timeSlots);
    assert.deepEqual(JSON.parse(JSON.parse(saves[2].payload).coursesJsonString), expected.courses);
    console.log('AI edu smoke passed: loopback API → production isolated JS → ordered Bridge payloads, 2 courses/4 periods/18 weeks.');
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(() => server.kill());
