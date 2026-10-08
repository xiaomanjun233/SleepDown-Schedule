// Synthetic adapters only; no school accounts or network access.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const dir = path.join(__dirname, '../../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/importing/shiguang');
const template = fs.readFileSync(path.join(dir, 'ShiguangScriptExecution.kt'), 'utf8').match(/= """([\s\S]*?)"""\.trimIndent/)[1];
const init = fs.readFileSync(path.join(dir, 'ShiguangBridgeProtocol.kt'), 'utf8').match(/ShiguangBridgeInitScript = """([\s\S]*?)"""\.trimIndent/)[1];
const messages = [];
const timers = new Map();
let timerId = 0;
const win = {
  _shiguangNativeBridge: { postMessage: raw => messages.push(JSON.parse(raw)) },
  fetch: async () => ({ url: 'https://school.example/course', status: 200,
    clone: () => ({ arrayBuffer: async () => new ArrayBuffer(0) }), json: async () => ({ ok: true }) })
};
const context = vm.createContext({ window: win, AbortController, setTimeout: fn => {
  const id = ++timerId; timers.set(id, fn); return id;
}, clearTimeout: id => timers.delete(id) });
vm.runInContext(init, context);
const execute = source => vm.runInContext(template.replace('${JSONObject.quote(source)}', JSON.stringify(source)), context);
const drain = async () => { for (let i = 0; i < 20; i++) await Promise.resolve(); };

(async () => {
  const source = 'const PAGE_URL = "page"; async function runImportFlow() { const response = await fetch("/course"); if (response.url !== "https://school.example/course") throw new Error("metadata lost"); window.shiguangBridge.showToast(PAGE_URL); } runImportFlow();';
  assert.equal(execute(source), 'started'); await drain();
  execute(source); await drain();
  assert.equal(messages.filter(it => it.action === 'showToast' && JSON.parse(it.payload).message === 'page').length, 2);
  const frame = vm.createContext({ window: { fetch: win.fetch, shiguangBridge: win.shiguangBridge },
    AbortController, setTimeout: () => 0, clearTimeout: () => {} });
  assert.equal(vm.runInContext(template.replace('${JSONObject.quote(source)}', JSON.stringify(source)), frame), 'started');
  await drain();
  assert.equal(messages.filter(it => it.action === 'showToast' && JSON.parse(it.payload).message === 'page').length, 3);
  execute('throw "真实错误"'); await drain();
  assert(messages.some(it => it.action === 'showToast' && JSON.parse(it.payload).message.includes('真实错误')));
  const waiting = win.shiguangBridgePromise.showAlert('confirm', 'body');
  const callback = messages.at(-1).callbackId;
  const errorResult = waiting.catch(error => error.message);
  win._shiguangNativeCallback(callback, false, '原生错误内容');
  assert.equal(await errorResult, '原生错误内容');
  const timeoutResult = win.shiguangBridgePromise.saveImportedCourses('[]').catch(error => error.message);
  [...timers.values()].forEach(fn => fn());
  assert.match(await timeoutResult, /超时/);
  delete win._shiguangNativeBridge;
  assert.equal(execute(source), 'bridge_unavailable');
  console.log('Passed: repeated lexical scope, forwarded iframe bridge, async/string errors, response metadata, bridge rejection and timeout.');
})().catch(error => { console.error(error); process.exitCode = 1; });
