const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/importing/EduDevToolsScripts.kt'), 'utf8');
const runner = source.match(/internal val EduProbeRunnerScript = """\r?\n([\s\S]*?)\r?\n"""\.trimIndent\(\)/)[1];
const tick = () => new Promise(resolve => setTimeout(resolve, 2));
async function settled(page, key) {
  const deadline = Date.now() + 1000;
  while (page[key]?.pending && Date.now() < deadline) await tick();
  assert.equal(page[key]?.pending, false, 'Probe did not settle');
  return JSON.parse(JSON.stringify(page[key]));
}
function environment(fetch) {
  const page = { location: { href: 'https://fixture.invalid/portal', origin: 'https://fixture.invalid' },
    URL, AbortController, setTimeout, clearTimeout, fetch };
  page.window = page;
  const context = vm.createContext(page);
  return { page, start(key, body) { vm.runInContext(`${runner}(${JSON.stringify(key)}, async function(window, fetch, console) {\n${body}\n})`, context); } };
}

test('probe awaits same-origin data and returns bounded, redacted logs without importing courses', async () => {
  const calls = [];
  const env = environment(async (url, options) => { calls.push([url, options]); return {url:'https://fixture.invalid/api/semester', json:async()=>({courses:2, token:'fixture-secret'})}; });
  env.start('result', "const data=await (await fetch('/api/semester')).json();console.log(data);return data;");
  const result = await settled(env.page, 'result');
  assert.equal(result.result.courses, 2);
  assert.equal(result.result.token, '[登录凭据已隐藏]');
  assert.equal(result.requests, 1);
  assert.equal(calls[0][1].credentials, 'same-origin');
  assert.equal(result.logs.length, 1);
  assert.ok(!JSON.stringify(result).includes('fixture-secret'));
});

test('cross-origin and unsupported HTTP methods are rejected before native fetch', async () => {
  let calls = 0;
  const env = environment(async()=>{calls++;throw Error('unexpected fetch');});
  for (const [key, body] of [['cross', "return await fetch('https://other.invalid/api');"], ['method', "return await fetch('/api', {method:'DELETE'});"]]) {
    env.start(key, body);
    assert.match((await settled(env.page, key)).error, /同源 GET\/POST/);
  }
  assert.equal(calls, 0);
});

test('cancel aborts in-flight work and late results cannot recreate a deleted task slot', async () => {
  let signal, finish;
  const env = environment((_url, options)=>{signal=options.signal;return new Promise(resolve=>{finish=resolve;});});
  env.start('cancelled', "return await fetch('/api');");
  await tick();
  env.page.cancelled.cancel();
  assert.equal(signal.aborted, true);
  assert.match(env.page.cancelled.error, /取消/);
  delete env.page.cancelled;
  finish({url:'https://fixture.invalid/api'});
  await tick();
  assert.equal(env.page.cancelled, undefined);
});

test('oversized results fail clearly and logs have a fixed count limit', async () => {
  const env = environment(async()=>{throw Error('unused');});
  env.start('large', "for(let i=0;i<30;i++)console.log('row',i);return 'x'.repeat(120001);");
  const result = await settled(env.page, 'large');
  assert.match(result.error, /120000/);
  assert.equal(result.logs.length, 20);
});

test('scroll inspects one explicit container and never performs a batch capture', () => {
  const script = source.match(/internal fun eduViewportScrollScript[\s\S]*?return """\r?\n([\s\S]*?)\r?\n\s*"""\.trimIndent\(\)/)[1]
    .replace('${JsonPrimitive(selector)}', JSON.stringify('#courses')).replace('${JsonPrimitive(direction)}', JSON.stringify('down'));
  const node = {clientWidth:100,clientHeight:200,scrollWidth:100,scrollHeight:1000,scrollLeft:0,scrollTop:0,
    scrollTo({left,top}) {this.scrollLeft=left;this.scrollTop=top;}};
  const result = JSON.parse(vm.runInNewContext(script, {document:{querySelector: selector=>{assert.equal(selector,'#courses');return node;}}}));
  assert.equal(result.y, 160);
  assert.equal(result.selector, '#courses');
});
