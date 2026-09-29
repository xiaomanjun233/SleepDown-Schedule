// Synthetic pages only; run with node scripts/tests/ai-edu-routing.cjs.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root,
  'app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/importing/AiEduRouting.kt'), 'utf8');
const script = source.match(/val AiEduFingerprintScript = """([\s\S]*?)"""\.trimIndent/)[1];
function page({ route = '/kb', selectors = {}, tables = [], password = false, jquery = true, frames = [] } = {}) {
  const doc = {
    scripts: [{ src: 'https://school.example/static/app.js?token=never-send-this' }],
    querySelector: key => selectors[key] || null,
    querySelectorAll: key => key === 'table' ? tables : key === 'input[type="password"]'
      ? (password ? [{ getClientRects: () => [1] }] : []) : []
  };
  const win = { document: doc, location: { pathname: route, href: 'https://school.example' + route },
    frames, jQuery: jquery ? {} : undefined };
  for (const object of [win, doc]) for (const key of ['cookie', 'localStorage', 'sessionStorage']) {
    Object.defineProperty(object, key, { get() { throw new Error('Sensitive state must not be read'); } });
  }
  return win;
}
const capture = win => JSON.parse(vm.runInNewContext(script, { window: win, URL })).candidates;
const zf = { '#shcPDF': { getAttribute: () => 'list' }, '#kblist_table': {} };
const zfPage = page({ route: '/jwglxt/kbcx/index', selectors: zf });
const result = capture(zfPage);
assert.equal(result[0].tool, 'ZHENGFANG');
assert.equal(result[0].strong, true);
assert.equal(result[0].documentKey, capture(zfPage)[0].documentKey);
assert.notEqual(result[0].documentKey, capture(page({ route: '/jwglxt/kbcx/index', selectors: zf }))[0].documentKey);
assert.equal(JSON.stringify(result).includes('never-send-this'), false);
assert.deepEqual(capture(page({ route: '/jwglxt/kbcx/index' })), [], 'Vendor route without parser structure is insufficient');
assert.deepEqual(capture(page({ selectors: zf, jquery: false })), [], 'The shipped ZF parser requires jQuery');
assert.deepEqual(capture(page({ selectors: zf, password: true })), [], 'Login forms are not course pages');
assert.equal(capture(page({ tables: [{ innerText: '星期一\n课程\n教师\n1-16[1-2]\n教室' }] }))[0].strong, false);
assert.equal(capture(page({ route: '/jwweb/kb', tables: [{ innerText: '星期一\n1-16[1-2]' }] }))[0].tool, 'QINGGUO');
assert.equal(capture(page({ route: '/student/course/table', selectors: {
  'td[id*="_"] .class_div p': {}, 'th[id^="0_"]': {}
} }))[0].tool, 'URP');
assert.equal(capture(page({ route: '/admin/xsd/pkgl/xskb', selectors: {
  '#xhid': {}, '#xqdm': {}, '#xnxq1': {}
} }))[0].tool, 'CHAOXING');
const inaccessible = {};
Object.defineProperty(inaccessible, 'document', { get() { throw new Error('cross origin'); } });
const nested = capture(page({ frames: [inaccessible, zfPage] }));
assert.deepEqual(nested[0].framePath, [1]);
assert.equal(capture(page({ frames: Array.from({ length: 100 }, () => zfPage) })).length, 11);
console.log('PASS: 4 system families, missing dependencies, login gating, ambiguous tables, frame bounds, navigation identity, sensitive-state exclusion');

const runnerSource = fs.readFileSync(path.join(root,
  'app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/importing/AiEduPageRouting.kt'), 'utf8');
const runner = runnerSource.match(/val script = """([\s\S]*?)"""\.trimIndent/)[1];
function execute(target, framePath, documentKey) {
  const body = '(async function(){window.fixtureExecuted = true;})()';
  const rendered = runner.replace('${candidate.framePath}', JSON.stringify(framePath))
    .replace('${JSONObject.quote(candidate.documentKey)}', JSON.stringify(documentKey))
    .replace('$invocation', framePath.length ? `target.eval(${JSON.stringify(body)});` : body + ';');
  return vm.runInNewContext(rendered, { window: target });
}
zfPage.shiguangBridge = { showToast() {} };
zfPage.shiguangBridgePromise = { saveImportedCourses() {} };
assert.equal(execute(zfPage, [], result[0].documentKey), true);
assert.equal(zfPage.fixtureExecuted, true);
delete zfPage.fixtureExecuted;
assert.equal(execute(zfPage, [], 'stale-document'), false);
assert.equal(zfPage.fixtureExecuted, undefined);
const child = page({ route: '/jwglxt/kbcx/index', selectors: zf });
const childCandidate = capture(child)[0];
child.eval = code => vm.runInNewContext(code, { window: child });
zfPage.frames = [child];
assert.equal(execute(zfPage, [0], childCandidate.documentKey), true);
assert.equal(child.fixtureExecuted, true);
assert.equal(child.shiguangBridgePromise, zfPage.shiguangBridgePromise);
assert.equal(execute(zfPage, [1], childCandidate.documentKey), false);
console.log('PASS: trusted adapter dispatch, stale/missing-frame rejection, existing native promise bridge reuse');

const captureSource = fs.readFileSync(path.join(root,
  'app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/importing/EduPageCapture.kt'), 'utf8');
const tableFunctions = captureSource.match(/  function textOf\(node\)[\s\S]*?(?=  function semanticHtmlOf)/)[0];
const cell = (text, rowSpan = 1, colSpan = 1) => ({ tagName: 'TD', innerText: text, rowSpan, colSpan });
const table = { querySelectorAll: () => [{ children: [cell(''), cell('高等数学', 2), cell('物理', 1, 2)] }] };
const compactTable = vm.runInNewContext(tableFunctions + '\ntableText(table, 0)', { table });
assert.equal(compactTable, 'Table 1\n[空] | [rowspan=2] 高等数学 | [colspan=2] 物理');
console.log('PASS: production table extraction preserves empty weekday slots and merged rows/columns');
