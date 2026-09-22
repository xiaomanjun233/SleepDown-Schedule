// Run with: node scripts/tests/auto-refresh-session-scripts.cjs
// All sessions and API responses are synthetic; this never contacts a school.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const sessionSource = fs.readFileSync(path.join(root,
    'app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/schedule/autorefresh/AutoRefreshWebSession.kt'), 'utf8');
const template = sessionSource.match(/val script = """([\s\S]*?)"""\.trimIndent\(\)/)[1];

function storage(values = {}) {
    const result = { ...values };
    Object.defineProperty(result, 'getItem', { value: key => values[key] ?? null });
    return result;
}

function capture(school, local, session = {}, globals = {}) {
    const keyLine = sessionSource.split('\n').find(line => line.includes('-> listOf(') && line.includes(`"${school}"`));
    const keys = JSON.parse(`[${keyLine.match(/listOf\((.*)\)/)[1]}]`);
    // Render the four Kotlin substitutions, then execute the actual production JS template.
    const script = template
        .replace('${JSONArray(keys)}', JSON.stringify(keys))
        .replace('${schoolId == "HUAT"}', String(school === 'HUAT'))
        .replace('${schoolId in setOf("AEPU", "BBGU")}', String(['AEPU', 'BBGU'].includes(school)))
        .replace('${schoolId == "YIBINU"}', String(school === 'YIBINU'));
    return JSON.parse(vm.runInNewContext(script, {
        localStorage: storage(local), sessionStorage: storage(session), window: globals
    }));
}

const syntheticJwt = 'eyJhbGciOiJub25lIn0.eyJzdWIiOiJmaXh0dXJlIn0.fixture';
for (const school of ['AEPU', 'BBGU']) {
    assert.equal(capture(school, { 'school-specific-session': syntheticJwt }).values['session:token'], syntheticJwt);
    assert.deepEqual(capture(school, { 'school-specific-session': 'ordinary-text' }).values, {});
}
assert.equal(capture('HUAT', {}, {}, { userToken: 'fixture-huat-session' }).values['session:token'], 'fixture-huat-session');
assert.deepEqual(capture('CQU', { cqu_edu_ACCESS_TOKEN: 'fixture-cqu-session', unrelated: 'do-not-copy' }).values,
    { 'local:cqu_edu_ACCESS_TOKEN': 'fixture-cqu-session' });
console.log('PASS: JWT fallback, HUAT window token, CQU storage allowlist');

async function verifySwuProtocol(loggedIn) {
    const source = fs.readFileSync(path.join(root, 'app/src/main/assets/shiguang_warehouse-main/resources/SWU/swu.js'), 'utf8');
    const staged = [];
    const requests = [];
    let resolveDone;
    const done = new Promise(resolve => { resolveDone = resolve; });
    const timeout = setTimeout(() => resolveDone('timeout'), 1500);
    const window = {
        location: { origin: 'https://jw.swu.edu.cn', pathname: '/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html' },
        shiguangBridge: {
            showToast: message => { if (message.includes('失败')) resolveDone('rejected'); },
            notifyTaskCompletion: () => resolveDone('verified')
        },
        shiguangBridgePromise: {
            showAlert: async () => true,
            showSingleSelection: async (_, __, schoolSelectedIndex) => schoolSelectedIndex,
            saveImportedCourses: async json => { staged.push(...JSON.parse(json)); },
            saveCourseConfig: async () => {},
            savePresetTimeSlots: async () => {}
        }
    };
    class DOMParser {
        parseFromString() {
            return { querySelectorAll: selector => !loggedIn ? [] : selector === '#xnm option'
                ? [{ value: '2025', textContent: '2025-2026', selected: false },
                    { value: '2026', textContent: '2026-2027', selected: true }]
                : [{ value: '12', textContent: '第二学期', selected: false },
                    { value: '3', textContent: '第一学期', selected: true }] };
        }
    }
    const fetch = async (url, options) => {
        requests.push({ url, body: options.body });
        if (url.includes('cxXskbcxIndex')) return { ok: true, text: async () => 'fixture-academic-options' };
        if (url.includes('cxZcByXnxq')) return { ok: true, json: async () => [{ zs: 1, rq: '2026-09-01' }] };
        assert.ok(url.includes('cxXsgrkb'));
        assert.match(options.body, /xnm=2026&xqm=3/);
        return { ok: true, text: async () => JSON.stringify({ kbList: [
            { kcmc: 'Fixture course', xqj: 1, jcs: '1-2', zcd: '1-5周(单)' }
        ], qsxqj: 1 }) };
    };
    vm.runInNewContext(source, { window, fetch, DOMParser, console: { log() {}, warn() {}, error() {} } });
    const result = await done;
    clearTimeout(timeout);
    assert.equal(result, loggedIn ? 'verified' : 'rejected');
    assert.equal(staged.length, loggedIn ? 1 : 0);
    if (loggedIn) assert.deepEqual(staged[0].weeks, [1, 3, 5]);
    else assert.equal(requests.length, 1, 'Login HTML must not cause timetable requests or staged data');
}

(async () => {
    await verifySwuProtocol(true);
    await verifySwuProtocol(false);
    console.log('PASS: official SWU protocol uses school-selected term; login HTML cannot complete validation');
})().catch(error => { console.error(error.message); process.exitCode = 1; });
