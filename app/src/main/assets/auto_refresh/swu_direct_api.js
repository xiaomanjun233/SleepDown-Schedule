// SPDX-License-Identifier: MIT
// SleepDown experimental direct-API variant for Southwest University.
// Endpoint names and payload mapping follow the official Shiguang SWU_01 adapter:
// https://github.com/XingHeYuZhuan/shiguang_warehouse/tree/main/resources/SWU
// This variant deliberately skips page parsing and refreshes the current academic term only.

(async function () {
    const TIME_SLOTS = [
        { number: 1, startTime: "08:00", endTime: "08:45" },
        { number: 2, startTime: "08:55", endTime: "09:40" },
        { number: 3, startTime: "10:00", endTime: "10:45" },
        { number: 4, startTime: "10:55", endTime: "11:40" },
        { number: 5, startTime: "12:10", endTime: "12:55" },
        { number: 6, startTime: "13:05", endTime: "13:50" },
        { number: 7, startTime: "14:00", endTime: "14:45" },
        { number: 8, startTime: "14:55", endTime: "15:40" },
        { number: 9, startTime: "15:50", endTime: "16:35" },
        { number: 10, startTime: "16:55", endTime: "17:40" },
        { number: 11, startTime: "17:50", endTime: "18:35" },
        { number: 12, startTime: "19:20", endTime: "20:05" },
        { number: 13, startTime: "20:15", endTime: "21:00" },
        { number: 14, startTime: "21:10", endTime: "21:55" }
    ];

    function currentTerm() {
        const now = new Date();
        const year = now.getFullYear();
        const month = now.getMonth() + 1;
        return {
            academicYear: String(month >= 8 ? year : year - 1),
            semesterCode: month >= 8 || month === 1 ? "3" : "12"
        };
    }

    function apiUrl(path) {
        return window.location.origin + "/jwglxt" + path;
    }

    function parseWeeks(value) {
        const result = [];
        String(value || "").split(/[，,]/).forEach((part) => {
            const match = part.trim().match(/(\d+)(?:-(\d+))?周(?:[（(](单|双)[）)])?/);
            if (!match) return;
            const start = Number(match[1]);
            const end = Number(match[2] || match[1]);
            const parity = match[3] || "";
            for (let week = start; week <= end; week += 1) {
                if (parity === "单" && week % 2 === 0) continue;
                if (parity === "双" && week % 2 !== 0) continue;
                result.push(week);
            }
        });
        return Array.from(new Set(result)).sort((a, b) => a - b);
    }

    function parseCourses(payload) {
        const rows = payload && Array.isArray(payload.kbList) ? payload.kbList : [];
        return rows.map((row) => {
            const sections = String(row.jcs || "").match(/\d+/g) || [];
            const weeks = parseWeeks(row.zcd);
            const day = Number(row.xqj);
            if (!row.kcmc || sections.length === 0 || weeks.length === 0 || day < 1 || day > 7) {
                return null;
            }
            const notes = [row.cxbjmc, row.xkbz, row.zcd]
                .map((item) => String(item || "").trim())
                .filter(Boolean);
            const course = {
                name: String(row.kcmc).trim(),
                teacher: String(row.xm || "").trim(),
                position: String(row.cdmc || "").trim(),
                day,
                startSection: Number(sections[0]),
                endSection: Number(sections[sections.length - 1]),
                weeks
            };
            if (notes.length > 0) course.remark = notes.join(" | ");
            return course;
        }).filter(Boolean).sort((a, b) =>
            a.day - b.day || a.startSection - b.startSection || a.name.localeCompare(b.name)
        );
    }

    async function requestJson(path, body) {
        const response = await fetch(apiUrl(path), {
            method: "POST",
            credentials: "include",
            headers: {
                "content-type": "application/x-www-form-urlencoded;charset=UTF-8",
                "X-Requested-With": "XMLHttpRequest"
            },
            body
        });
        if (!response.ok) {
            throw new Error(`接口返回 ${response.status}，登录可能已失效`);
        }
        return await response.json();
    }

    function semesterStartDate(rows) {
        if (!Array.isArray(rows) || rows.length === 0) return null;
        const first = rows.find((item) => String(item.zs) === "1" || String(item.zsmc) === "1") || rows[0];
        const match = String(first.rq || first.zcrq || first.ksrq || "").match(/\d{4}-\d{2}-\d{2}/);
        return match ? match[0] : null;
    }

    try {
        const confirmed = await window.shiguangBridgePromise.showAlert(
            "西南大学纯接口刷新",
            "将按当前日期自动选择学年学期，并直接读取教务课表接口。",
            "继续"
        );
        if (!confirmed) throw new Error("刷新已取消");

        const term = currentTerm();
        const body = `xnm=${encodeURIComponent(term.academicYear)}&xqm=${term.semesterCode}`;
        window.shiguangBridge.showToast(`正在读取 ${term.academicYear} 学年当前学期课表…`);

        const [coursePayload, calendarPayload] = await Promise.all([
            requestJson("/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151", `${body}&kzlx=ck&xsdm=&kclbdm=`),
            requestJson("/kbcx/xskbcxZccx_cxZcByXnxq.html?gnmkdm=N2154", body).catch(() => null)
        ]);
        const courses = parseCourses(coursePayload);
        if (courses.length === 0) throw new Error("当前学期未找到课程数据");

        await window.shiguangBridgePromise.saveImportedCourses(JSON.stringify(courses));
        const startDate = semesterStartDate(calendarPayload);
        if (startDate) {
            const maxWeek = courses.reduce(
                (maximum, course) => Math.max(maximum, ...course.weeks),
                20
            );
            await window.shiguangBridgePromise.saveCourseConfig(JSON.stringify({
                semesterStartDate: startDate,
                semesterTotalWeeks: maxWeek,
                firstDayOfWeek: 1
            }));
        }
        await window.shiguangBridgePromise.savePresetTimeSlots(JSON.stringify(TIME_SLOTS));
        window.shiguangBridge.showToast(`西南大学课表刷新成功，共 ${courses.length} 门课程`);
        window.shiguangBridge.notifyTaskCompletion();
    } catch (error) {
        const detail = error && error.message ? error.message : String(error);
        window.shiguangBridge.showToast(`西南大学接口刷新失败：${detail}`);
    }
})();
