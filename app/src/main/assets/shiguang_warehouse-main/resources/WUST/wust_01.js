// 武汉科技大学强智教务适配器。
// 页面结构和作息来源：https://github.com/xsddszrc/wust-schedule-ics

(function () {
    "use strict";

    const HUANGJIAHU_TIME_SLOTS = [
        { number: 1, startTime: "08:20", endTime: "09:05" },
        { number: 2, startTime: "09:15", endTime: "10:00" },
        { number: 3, startTime: "10:20", endTime: "11:05" },
        { number: 4, startTime: "11:15", endTime: "12:00" },
        { number: 5, startTime: "14:00", endTime: "14:45" },
        { number: 6, startTime: "14:55", endTime: "15:40" },
        { number: 7, startTime: "16:00", endTime: "16:45" },
        { number: 8, startTime: "16:55", endTime: "17:40" },
        { number: 9, startTime: "18:40", endTime: "19:25" },
        { number: 10, startTime: "19:35", endTime: "20:20" },
        { number: 11, startTime: "20:40", endTime: "21:25" },
        { number: 12, startTime: "21:35", endTime: "22:20" }
    ];

    const QINGSHAN_TIME_SLOTS = HUANGJIAHU_TIME_SLOTS.map(function (slot) {
        const morning = {
            1: ["08:00", "08:45"],
            2: ["08:55", "09:40"],
            3: ["10:00", "10:45"],
            4: ["10:55", "11:40"]
        }[slot.number];
        return morning
            ? { number: slot.number, startTime: morning[0], endTime: morning[1] }
            : Object.assign({}, slot);
    });

    function normalizeText(value) {
        return String(value || "")
            .replace(/\u00a0/g, " ")
            .replace(/[０-９]/g, function (char) {
                return String.fromCharCode(char.charCodeAt(0) - 0xFEE0);
            })
            .replace(/[，、]/g, ",")
            .replace(/[－–—~～至到]/g, "-")
            .replace(/[（）]/g, function (char) { return char === "（" ? "(" : ")"; })
            .replace(/\s+/g, " ")
            .trim();
    }

    function showMessage(message) {
        if (window.AndroidBridge && typeof window.AndroidBridge.showToast === "function") {
            window.AndroidBridge.showToast(message);
        } else {
            console.log(message);
        }
    }

    async function showError(title, message) {
        if (window.AndroidBridgePromise && typeof window.AndroidBridgePromise.showAlert === "function") {
            await window.AndroidBridgePromise.showAlert(title, message, "确定");
        } else {
            alert(title + "\n" + message);
        }
    }

    function findScheduleDocument() {
        if (document.querySelector("#kbtable")) return document;
        const frames = Array.from(document.querySelectorAll("iframe"));
        for (const frame of frames) {
            try {
                const frameDocument = frame.contentDocument || frame.contentWindow.document;
                if (frameDocument && frameDocument.querySelector("#kbtable")) return frameDocument;
            } catch (error) {
                // 跨域 iframe 无法读取，继续检查其他 frame。
            }
        }
        return null;
    }

    async function loadScheduleDocument() {
        const current = findScheduleDocument();
        if (current) return current;

        const response = await fetch("/jsxsd/xskb/xskb_list.do", {
            method: "GET",
            credentials: "include",
            redirect: "follow"
        });
        if (!response.ok) throw new Error("课表页面请求失败（" + response.status + "）");
        const html = await response.text();
        const parsed = new DOMParser().parseFromString(html, "text/html");
        if (!parsed.querySelector("#kbtable")) {
            throw new Error("未找到课表表格，请先完成登录并进入“学期理论课表”页面");
        }
        return parsed;
    }

    function parseWeeks(value) {
        const text = normalizeText(value)
            .replace(/\[[^\]]*\]/g, "")
            .replace(/\(周\)/g, "")
            .replace(/周/g, "")
            .replace(/\s/g, "");
        const weeks = new Set();
        text.split(/[;,；]/).forEach(function (part) {
            if (!part) return;
            const oddOnly = /单/.test(part);
            const evenOnly = /双/.test(part);
            const ranges = part.match(/\d+(?:-\d+)?/g) || [];
            ranges.forEach(function (rangeText) {
                const bounds = rangeText.split("-").map(function (item) { return parseInt(item, 10); });
                const start = bounds[0];
                const end = bounds.length > 1 ? bounds[1] : start;
                if (!start || !end || start > end) return;
                for (let week = start; week <= end; week += 1) {
                    if (oddOnly && week % 2 === 0) continue;
                    if (evenOnly && week % 2 !== 0) continue;
                    weeks.add(week);
                }
            });
        });
        return Array.from(weeks).sort(function (left, right) { return left - right; });
    }

    function parsePeriods(value) {
        const text = normalizeText(value).replace(/\s/g, "");
        const bracket = text.match(/\[([^\]]+)\]/);
        if (!bracket) return [];
        const numbers = (bracket[1].match(/\d+/g) || [])
            .map(function (item) { return parseInt(item, 10); })
            .filter(function (item) { return item > 0; });
        if (numbers.length === 0) return [];
        const start = numbers[0];
        const end = numbers[numbers.length - 1];
        if (start > end) return [];
        const periods = [];
        for (let period = start; period <= end; period += 1) periods.push(period);
        return periods;
    }

    function titledText(container, title) {
        const node = container.querySelector(
            'font[title="' + title + '"], span[title="' + title + '"], div[title="' + title + '"]'
        );
        return normalizeText(node ? node.textContent : "");
    }

    function courseNameFromBlock(container) {
        const clone = container.cloneNode(true);
        Array.from(clone.querySelectorAll("font[title], span[title], div[title]")).forEach(function (node) {
            node.remove();
        });
        const lines = clone.innerHTML
            .replace(/<br\s*\/?\s*>/gi, "\n")
            .split(/\n+/)
            .map(function (line) {
                const holder = document.createElement("div");
                holder.innerHTML = line;
                return normalizeText(holder.textContent).replace(/[●★○]/g, "").trim();
            })
            .filter(function (line) { return line && line !== "-"; });
        return lines[0] || "";
    }

    function parseCourseBlock(blockHtml, weekday) {
        const container = document.createElement("div");
        container.innerHTML = blockHtml;
        const courseName = courseNameFromBlock(container);
        const weekPeriod = titledText(container, "周次(节次)");
        const weeks = parseWeeks(weekPeriod);
        const periods = parsePeriods(weekPeriod);
        if (!courseName || weekday < 1 || weekday > 7 || weeks.length === 0 || periods.length === 0) {
            return null;
        }
        return {
            name: courseName,
            teacher: titledText(container, "老师") || titledText(container, "教师") || "未知教师",
            position: titledText(container, "教室") || "待定",
            day: weekday,
            startSection: periods[0],
            endSection: periods[periods.length - 1],
            weeks: weeks
        };
    }

    function parseWeekday(courseDiv) {
        const parts = String(courseDiv.id || "").split("-");
        const fromId = parts.length >= 3 ? parseInt(parts[parts.length - 2], 10) : 0;
        if (fromId >= 1 && fromId <= 7) return fromId;
        const cell = courseDiv.closest("td");
        const cellIndex = cell ? cell.cellIndex : -1;
        return cellIndex >= 1 && cellIndex <= 7 ? cellIndex : 0;
    }

    function mergeCoursePeriods(courses) {
        const merged = new Map();
        courses.forEach(function (course) {
            const key = [
                course.name,
                course.teacher,
                course.position,
                course.day,
                course.weeks.join(",")
            ].join("|");
            if (!merged.has(key)) {
                merged.set(key, Object.assign({}, course));
                return;
            }
            const existing = merged.get(key);
            existing.startSection = Math.min(existing.startSection, course.startSection);
            existing.endSection = Math.max(existing.endSection, course.endSection);
        });
        return Array.from(merged.values()).sort(function (left, right) {
            return left.day - right.day || left.startSection - right.startSection || left.name.localeCompare(right.name);
        });
    }

    function parseCourses(scheduleDocument) {
        const table = scheduleDocument.querySelector("#kbtable");
        const courses = [];
        Array.from(table.querySelectorAll("div.kbcontent")).forEach(function (courseDiv) {
            const weekday = parseWeekday(courseDiv);
            const html = String(courseDiv.innerHTML || "").trim();
            if (!weekday || !html || html === "&nbsp;") return;
            const blocks = html.split(/<br\s*\/?\s*>\s*[-—]{10,}\s*<br\s*\/?\s*>/i);
            blocks.forEach(function (block) {
                const course = parseCourseBlock(block, weekday);
                if (course) courses.push(course);
            });
        });
        return mergeCoursePeriods(courses);
    }

    function chooseTimeSlots(courses) {
        // 教务页可能同时列出两个校区选项，因此只根据实际课程地点判断；
        // 无法判断时与原项目保持一致，使用黄家湖作息。
        const qingshan = courses.some(function (course) {
            return /青山校区|青山/.test(normalizeText(course.position));
        });
        return {
            campusName: qingshan ? "青山校区" : "黄家湖校区",
            slots: qingshan ? QINGSHAN_TIME_SLOTS : HUANGJIAHU_TIME_SLOTS
        };
    }

    async function saveToApp(courses, timeSlots) {
        if (!window.AndroidBridgePromise) throw new Error("SleepDown 导入桥接不可用");
        let totalWeeks = 0;
        courses.forEach(function (course) {
            course.weeks.forEach(function (week) { totalWeeks = Math.max(totalWeeks, week); });
        });
        const configSaved = await window.AndroidBridgePromise.saveCourseConfig(JSON.stringify({
            semesterTotalWeeks: totalWeeks || 20
        }));
        const slotsSaved = await window.AndroidBridgePromise.savePresetTimeSlots(JSON.stringify(timeSlots));
        const coursesSaved = await window.AndroidBridgePromise.saveImportedCourses(JSON.stringify(courses));
        if (!configSaved || !slotsSaved || !coursesSaved) throw new Error("课程数据交给应用时失败");
    }

    async function runImport() {
        try {
            showMessage("正在读取武汉科技大学课表…");
            const scheduleDocument = await loadScheduleDocument();
            const courses = parseCourses(scheduleDocument);
            if (courses.length === 0) {
                throw new Error("没有解析到课程，请确认当前学期有课且课表已加载完成");
            }
            const timeProfile = chooseTimeSlots(courses);
            await saveToApp(courses, timeProfile.slots);
            showMessage("已解析 " + courses.length + " 个课程时段，并采用" + timeProfile.campusName + "作息");
            if (!window.AndroidBridge || typeof window.AndroidBridge.notifyTaskCompletion !== "function") {
                throw new Error("导入完成回调不可用");
            }
            window.AndroidBridge.notifyTaskCompletion();
        } catch (error) {
            console.error("WUST import failed", error);
            await showError("武汉科技大学导入失败", error && error.message ? error.message : String(error));
        }
    }

    runImport();
})();
