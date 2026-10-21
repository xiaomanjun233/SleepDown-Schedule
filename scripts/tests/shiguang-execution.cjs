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
Object.assign(win, { window: win, AbortController, setTimeout: fn => {
  const id = ++timerId; timers.set(id, fn); return id;
}, clearTimeout: id => timers.delete(id) });
const context = vm.createContext(win);
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

  const adapter = fs.readFileSync(path.join(__dirname,
    '../../app/src/main/assets/shiguang_warehouse-main/resources/HNSF/hnsf_01.js'), 'utf8');
  win.location = { href: 'https://jwc.htu.edu.cn/new/student/home' };
  execute(adapter); await drain();
  assert.equal(messages.at(-1).action, 'showAlert');
  win._shiguangNativeCallback(messages.at(-1).callbackId, true, true);
  await drain();
  const yearPrompt = messages.at(-1);
  assert.equal(yearPrompt.action, 'showPrompt');
  assert.equal(JSON.parse(yearPrompt.payload).validatorJsFunction, 'validateYearInput');
  assert.throws(() => vm.runInContext('validateYearInput("2026")', context), /is not defined/);
  assert.equal(win._shiguangValidatePrompt(yearPrompt.callbackId, '2026'), false);
  assert.equal(win._shiguangValidatePrompt(yearPrompt.callbackId, '26'), '请输入四位数字的学年！');
  const ui = fs.readFileSync(path.join(dir, '../EduSchoolSelectionUi.kt'), 'utf8');
  const validation = ui.match(/private fun validateEduBridgePrompt[\s\S]*?val script = """([\s\S]*?)"""/)[1];
  const validateYear = value => vm.runInContext(validation
    .replace('${JSONObject.quote(request.requestId)}', JSON.stringify(yearPrompt.callbackId))
    .replace('${JSONObject.quote(value)}', JSON.stringify(value)), context);
  assert.equal(validateYear('2026'), null);
  assert.equal(validateYear('26'), '请输入四位数字的学年！');
  assert.equal(validateYear('2026"); throw new Error("injected")'), '请输入四位数字的学年！');
  win._shiguangNativeCallback(yearPrompt.callbackId, true, '2026');
  await drain();
  assert.equal(messages.at(-1).action, 'showSingleSelection');
  win._shiguangNativeCallback(messages.at(-1).callbackId, true, -1);
  await drain();
  assert.equal(win.shiguangBridge._evaluateAdapterInput, undefined);
  assert.throws(() => win._shiguangValidatePrompt(yearPrompt.callbackId, '2026'), /输入请求已结束/);

  const promptSource = label => `function validateInput(input) { return input === "2026" ? false : ${JSON.stringify(label)}; }
    window.shiguangBridgePromise.showPrompt("学年", "", "", "validateInput");`;
  execute(promptSource('first')); await drain();
  const firstPrompt = messages.at(-1).callbackId;
  execute(promptSource('second')); await drain();
  const secondPrompt = messages.at(-1).callbackId;
  assert.equal(win._shiguangValidatePrompt(firstPrompt, 'bad'), 'first');
  assert.equal(win._shiguangValidatePrompt(secondPrompt, 'bad'), 'second');
  win._shiguangNativeCallback(firstPrompt, true, null); await drain();
  assert.equal(typeof win.shiguangBridge._evaluateAdapterInput, 'function');
  win._shiguangNativeCallback(secondPrompt, true, null); await drain();
  assert.equal(win.shiguangBridge._evaluateAdapterInput, undefined);

  execute('window.validateGlobalInput = function(input) { return input === "2026" ? false : "全局校验"; };' +
    'window.shiguangBridgePromise.showPrompt("学年", "", "", "validateGlobalInput");');
  await drain();
  const globalPrompt = messages.at(-1).callbackId;
  assert.equal(win._shiguangValidatePrompt(globalPrompt, '2026'), false);
  assert.equal(win._shiguangValidatePrompt(globalPrompt, 'bad'), '全局校验');
  win._shiguangNativeCallback(globalPrompt, true, null); await drain();
  delete win.validateGlobalInput;

  const framePrompt = vm.createContext({ window: {
    fetch: win.fetch, shiguangBridge: win.shiguangBridge, shiguangBridgePromise: win.shiguangBridgePromise
  }, AbortController, setTimeout: () => 0, clearTimeout: () => {} });
  vm.runInContext(template.replace('${JSONObject.quote(source)}', JSON.stringify(promptSource('iframe'))), framePrompt);
  await drain();
  const iframePrompt = messages.at(-1).callbackId;
  assert.equal(win._shiguangValidatePrompt(iframePrompt, 'bad'), 'iframe');
  win._shiguangNativeCallback(iframePrompt, true, null); await drain();
  assert.equal(win.shiguangBridge._evaluateAdapterInput, undefined);

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
  console.log('Passed: isolated adapters, HNSF year validation and semester handoff, per-prompt scope, iframe validation, cleanup, async/string errors, response metadata, bridge rejection and timeout.');
})().catch(error => { console.error(error); process.exitCode = 1; });
