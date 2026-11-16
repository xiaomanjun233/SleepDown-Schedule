// Run with: node scripts/tests/xauat-adapter.cjs [path/to/xauat.js]
// Runs the real XAUAT adapter in a vm with a mocked browser and bridge. The fixture is a
// desensitized snapshot of one real 2026-2027-1 undergraduate timetable; no network or account.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const sourcePath = process.argv[2] || path.resolve(__dirname,
    '../../app/src/main/assets/shiguang_warehouse-main/resources/XAUAT/xauat.js');
const source = fs.readFileSync(sourcePath, 'utf8');
const fixture = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../fixtures/xauat-2026-2027-1.json'), 'utf8'));
const clone = value => JSON.parse(JSON.stringify(value));
const DAY = 86400000;
const utc = s => Date.UTC(+s.slice(0, 4), +s.slice(5, 7) - 1, +s.slice(8, 10));

class FakeOption {
    constructor(item) { this.item = item; this.textContent = item.name; }
    getAttribute(name) { return name === 'value' ? this.item.id : null; }
    hasAttribute(name) { return name === 'selected' && this.item.selected === true; }
}

async function runAdapter(mutate = {}) {
    const data = clone(fixture);
    if (mutate.data) mutate.data(data);
    const calls = { alerts: [], toasts: [], selects: [], saved: {}, order: [], done: false };
    const respond = (body, status = 200) => ({
        ok: status < 400, status, text: async () => (typeof body === 'string' ? body : JSON.stringify(body))
    });
    const fetchMock = async (url, options) => {
        const custom = mutate.fetch && mutate.fetch(url, options, respond);
        if (custom) return custom;
        if (url.startsWith('/student/for-std/course-table/get-data')) return respond(data.getData);
        if (url === '/student/ws/schedule-table/datum') return respond(data.datum);
        if (url.startsWith('/student/ws/semester/get/')) return respond(data.semester);
        if (url === '/student/ws/schedule-table/timetable-layout') return respond(data.layout);
        if (url === '/student/for-std/course-table') return respond('<html></html>');
        return respond('', 404);
    };
    const bridgePromise = {
        showAlert: async (title, message) => { calls.alerts.push([title, message]); return true; },
        showSingleSelection: async (title, items, preset) => {
            calls.selects.push({ items: JSON.parse(items), preset });
            return 'pick' in mutate ? mutate.pick : preset;
        },
        saveCourseConfig: async json => { calls.order.push('config'); calls.saved.config = JSON.parse(json); },
        savePresetTimeSlots: async json => { calls.order.push('slots'); calls.saved.slots = JSON.parse(json); },
        saveImportedCourses: async json => { calls.order.push('courses'); calls.saved.courses = JSON.parse(json); }
    };
    const options = data.semesters.map(item => new FakeOption(item));
    const sandbox = {
        window: {
            shiguangBridge: { showToast: message => calls.toasts.push(message), notifyTaskCompletion: () => { calls.done = true; } },
            shiguangBridgePromise: bridgePromise
        },
        fetch: fetchMock,
        console: { log() {}, warn() {}, error() {} },
        setTimeout, clearTimeout, AbortController,
        DOMParser: class {
            parseFromString() {
                const select = options.length && !mutate.noSemesterSelect ? { querySelectorAll: () => options } : null;
                return { querySelector: () => select };
            }
        }
    };
    vm.createContext(sandbox);
    vm.runInContext(source, sandbox);
    // The adapter starts itself and is not awaitable: wait for completion or a terminal alert.
    for (let i = 0; i < 400; i++) {
        await new Promise(resolve => setTimeout(resolve, 5));
        if (calls.done || calls.alerts.some(a => a[0] === '导入失败' || a[0] === '无课程数据') || calls.toasts.includes('导入已取消')) break;
    }
    return calls;
}

// Same arithmetic as the app: scheduleCalendarWeekStart + (week - 1) * 7 + weekday offset.
function appDate(config, week, day) {
    const start = utc(config.semesterStartDate);
    const startDay = new Date(start).getUTCDay();
    const sundayFirst = config.firstDayOfWeek === 7;
    const weekStart = start - (sundayFirst ? startDay : (startDay + 6) % 7) * DAY;
    return new Date(weekStart + ((week - 1) * 7 + (sundayFirst ? day % 7 : day - 1)) * DAY).toISOString().slice(0, 10);
}
const hhmm = n => String(Math.floor(n / 100)).padStart(2, '0') + ':' + String(n % 100).padStart(2, '0');

(async () => {
    const lessonNames = new Map(fixture.datum.result.lessonList.map(l => [l.id, l.courseName]));
    const units = new Map(fixture.layout.result.courseUnitList.map(u => [u.indexNo, u]));
    const expectedOccurrences = new Set(fixture.datum.result.scheduleList.map(i =>
        [lessonNames.get(i.lessonId), i.date, hhmm(i.startTime), hhmm(i.endTime)].join('|')));

    const main = await runAdapter();
    assert.ok(main.done, 'import completes');
    assert.equal(main.selects[0].items[main.selects[0].preset], '2026-2027-1', 'default semester is the page-selected one, not the first listed');
    assert.deepEqual(main.order, ['config', 'slots', 'courses'], 'bridge order: config, slots, courses');
    assert.deepEqual(main.saved.config, { semesterStartDate: '2026-08-30', semesterTotalWeeks: 18, firstDayOfWeek: 7 });
    assert.equal(main.saved.slots.length, 12);
    assert.deepEqual([main.saved.slots[0].startTime, main.saved.slots[11].endTime], ['08:30', '21:05']);
    assert.ok(main.saved.courses.every(c => c.isCustomTime === false), 'every row sits on the XAUAT bell grid');

    // Every dated occurrence must be reproducible from (week, weekday) with the app's own week math.
    const produced = new Set();
    for (const c of main.saved.courses) for (const week of c.weeks) {
        produced.add([c.name, appDate(main.saved.config, week, c.day),
            units.get(c.startSection).startTime && hhmm(units.get(c.startSection).startTime),
            hhmm(units.get(c.endSection).endTime)].join('|'));
    }
    assert.deepEqual([...expectedOccurrences].filter(k => !produced.has(k)), [], 'no occurrence lost or shifted');
    assert.deepEqual([...produced].filter(k => !expectedOccurrences.has(k)), [], 'no occurrence invented');

    // Per-week room/teacher: the Monday English class swaps room and substitute teacher between weeks.
    const english = main.saved.courses.filter(c => c.name === '大学英语1' && c.day === 1)
        .map(c => `${c.weeks.join(',')}|${c.position}|${c.teacher}`).sort();
    assert.deepEqual(english, [
        '12,15,18|草堂8-309-310(机房)|教师E',
        '5,7,8,10,11,13,14,16,17|草堂13-313|教师E',
        '9|草堂8-309-310(机房)|教师G'
    ]);
    // Concurrent lab rooms collapse into one stable row per slot and are announced to the user.
    const lab = main.saved.courses.filter(c => c.name === 'C语言程序设计实验');
    assert.equal(lab.length, 2);
    assert.ok(lab.every(c => c.weeks.join() === '11,12,13,14,15,16'));
    assert.deepEqual(lab.map(c => c.position.split('/').length).sort(), [2, 3]);
    assert.ok(main.alerts.some(a => a[0] === '部分课程有多个教室' && a[1].includes('C语言程序设计实验')));
    // Weekend make-up classes keep their own weeks; Sunday is day 7 in a Sunday-first week.
    assert.deepEqual(main.saved.courses.filter(c => c.day === 7).map(c => c.weeks.join()).sort(), ['8,9,10', '8,9,10,11']);
    assert.ok(main.saved.courses.some(c => c.name === '大学英语1' && c.day === 6 && c.position === ''), 'make-up class without a room stays blank');

    // Server weekIndex that disagrees with the semester start date must abort, not import shifted weeks.
    let result = await runAdapter({ data: d => { d.semester.startDate = '2026-09-06'; } });
    assert.ok(!result.done && result.order.length === 0);
    assert.ok(result.alerts.some(a => a[0] === '导入失败' && a[1].includes('不一致')));

    // Monday-first semester: no Sunday offset, firstDayOfWeek 1.
    result = await runAdapter({ data: d => {
        d.semester.startDate = '2026-08-31'; d.semester.weekStartOnSunday = false;
        d.datum.result.scheduleList = d.datum.result.scheduleList
            .map(i => ({ ...i, weekIndex: Math.floor((utc(i.date) - utc('2026-08-31')) / DAY / 7) + 1 }))
            .filter(i => i.weekIndex >= 1);
    } });
    assert.ok(result.done);
    assert.equal(result.saved.config.firstDayOfWeek, 1);
    const mondayProduced = new Set();
    for (const c of result.saved.courses) for (const week of c.weeks) {
        mondayProduced.add([c.name, appDate(result.saved.config, week, c.day), hhmm(units.get(c.startSection).startTime)].join('|'));
    }
    const mondayExpected = new Set(fixture.datum.result.scheduleList.filter(i => utc(i.date) >= utc('2026-08-31'))
        .map(i => [lessonNames.get(i.lessonId), i.date, hhmm(i.startTime)].join('|')));
    assert.deepEqual([...mondayExpected].filter(k => !mondayProduced.has(k)), []);

    // weekIndex absent: derive weeks from the semester start; start date inside week 1 resolves the same week.
    result = await runAdapter({ data: d => d.datum.result.scheduleList.forEach(i => { delete i.weekIndex; }) });
    assert.deepEqual(result.saved.courses, main.saved.courses);
    result = await runAdapter({ data: d => { d.semester.startDate = '2026-08-31'; } });
    assert.deepEqual(result.saved.courses, main.saved.courses);

    // Layout: request failure aborts; a missing layout id falls back to exact clocks without slots.
    result = await runAdapter({ fetch: (url, _o, respond) => url.includes('timetable-layout') ? respond('', 500) : null });
    assert.ok(!result.done && result.alerts.some(a => a[0] === '导入失败'));
    result = await runAdapter({ data: d => { d.getData.timeTableLayoutId = null; } });
    assert.ok(result.done && !result.order.includes('slots'));
    assert.ok(result.saved.courses.every(c => c.isCustomTime && /^\d\d:\d\d$/.test(c.customStartTime)));

    // Off-grid clock keeps its real time; malformed rows are skipped; customPlace and lesson teachers are fallbacks.
    result = await runAdapter({ data: d => {
        const list = d.datum.result.scheduleList;
        const tuesday = list.find(i => i.weekday === 2); tuesday.startTime = 1900; tuesday.endTime = 2000;
        list.push({ ...list[0], date: null }, { ...list[0], startTime: 1000, endTime: 900 });
        list[1].room = null; list[1].customPlace = '操场';
        list[2].personName = null;
    } });
    assert.ok(result.done);
    assert.deepEqual(result.saved.courses.filter(c => c.isCustomTime).map(c => c.customStartTime), ['19:00']);
    assert.ok(result.saved.courses.some(c => c.position === '操场'));
    assert.ok(result.saved.courses.every(c => c.teacher), 'teacher falls back to the lesson assignment');

    // Weekday that contradicts the date aborts.
    result = await runAdapter({ data: d => { const i = d.datum.result.scheduleList[0]; i.weekday = i.weekday % 7 + 1; } });
    assert.ok(!result.done && result.alerts.some(a => a[1].includes('星期与日期不一致')));

    // Empty schedule, user cancel, logged-out page, short term floor.
    result = await runAdapter({ data: d => { d.datum.result.scheduleList = []; } });
    assert.ok(!result.done && result.alerts.some(a => a[0] === '无课程数据') && result.order.length === 0);
    result = await runAdapter({ pick: null });
    assert.ok(!result.done && result.order.length === 0 && result.toasts.includes('导入已取消'));
    result = await runAdapter({ noSemesterSelect: true });
    assert.ok(!result.done && result.alerts.some(a => a[1].includes('未找到学期选择框')));
    result = await runAdapter({ data: d => { d.datum.result.scheduleList = d.datum.result.scheduleList.filter(i => i.weekIndex <= 5); } });
    assert.equal(result.saved.config.semesterTotalWeeks, 16, 'short term keeps the 16-week floor');

    console.log('PASS: XAUAT weeks, Sunday-first start, bells, per-week rooms/teachers and edge cases');
})().catch(error => { console.error(error); process.exit(1); });
