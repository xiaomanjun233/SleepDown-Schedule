

// 解析周次字符串，输出拾光课程表需要的数字数组。
function parseWeeks(weekStr) {
    if (!weekStr) return [];

    const weeks = new Set();
    String(weekStr).split(',').forEach(part => {
        const trimmed = part.trim();
        if (!trimmed) return;

        if (trimmed.includes('-')) {
            const [start, end] = trimmed.split('-').map(n => parseInt(n, 10));
            if (!isNaN(start) && !isNaN(end) && start <= end) {
                for (let i = start; i <= end; i++) {
                    weeks.add(i);
                }
            }
            return;
        }

        const week = parseInt(trimmed, 10);
        if (!isNaN(week) && week > 0) {
            weeks.add(week);
        }
    });

    return Array.from(weeks).sort((a, b) => a - b);
}

// 从按周展开的场地字符串中提取去重后的教室名称。
function extractLocationsFromJxcdmc2(jxcdmc2) {
    if (!jxcdmc2) return [];

    const locationSet = new Set();
    String(jxcdmc2).split(",").forEach(item => {
        const trimmed = item.trim();
        if (!trimmed) return;

        const match = trimmed.match(/^(.*?)-(\d+)$/);
        const location = (match ? match[1] : trimmed).trim();
        if (location && location !== "-1") {
            locationSet.add(location);
        }
    });

    return Array.from(locationSet);
}

// 按页面现有逻辑优先级生成课程地点文案。
function resolvePosition(item) {
    const primary = String(item.jxcdmc || "").trim();
    if (primary) {
        return primary;
    }

    if (String(item.bapjxcd || "") === "1") {
        return "不用场地";
    }

    const fallbackLocations = extractLocationsFromJxcdmc2(item.jxcdmc2);
    if (fallbackLocations.length > 0) {
        return fallbackLocations.join("、");
    }

    return "待定";
}

// 解析课表接口返回的数据并转换为课程数组。
function parseCourseList(apiJson) {
    if (!apiJson || apiJson.code !== 0 || !Array.isArray(apiJson.data)) {
        throw new Error("课表接口返回格式不正确");
    }

    const courseMap = new Map();

    apiJson.data.forEach(item => {
        const day = parseInt(item.xq, 10);
        const startSection = parseInt(item.ps, 10);
        const endSection = parseInt(item.pe, 10);
        const weeks = parseWeeks(item.zc);

        if (
            !item.kcmc ||
            isNaN(day) ||
            isNaN(startSection) ||
            isNaN(endSection) ||
            day < 1 ||
            day > 7 ||
            startSection > endSection ||
            weeks.length === 0
        ) {
            return;
        }

        const teacher = (item.teaxms || item.pkr || "").trim() || "未知";
        const position = resolvePosition(item);

        const key = [
            item.kcmc.trim(),
            teacher,
            position,
            day,
            startSection,
            endSection,
            weeks.join(',')
        ].join("__");

        if (!courseMap.has(key)) {
            courseMap.set(key, {
                name: item.kcmc.trim(),
                teacher,
                position,
                day,
                startSection,
                endSection,
                weeks
            });
        }
    });

    return Array.from(courseMap.values()).sort((a, b) =>
        a.day - b.day ||
        a.startSection - b.startSection ||
        a.endSection - b.endSection ||
        a.name.localeCompare(b.name)
    );
}

// 从 week.page 源码中提取学校真实作息时间。
function parseBusinessHoursFromHtml(htmlText) {
    const match = htmlText.match(/var\s+businessHours\s*=\s*\$\.parseJSON\('(\[.*?\])'\);/);
    if (!match || !match[1]) {
        return [];
    }

    let rawData;
    try {
        rawData = JSON.parse(match[1]);
    } catch (error) {
        console.warn("businessHours 解析失败", error);
        return [];
    }

    return rawData
        .map(item => ({
            number: parseInt(item.jcdm, 10),
            startTime: String(item.qssj || "").slice(0, 5),
            endTime: String(item.jssj || "").slice(0, 5)
        }))
        .filter(item => !isNaN(item.number) && item.startTime && item.endTime)
        .sort((a, b) => a.number - b.number);
}

// 从页面脚本中识别总周数上限，作为后续配置接入的线索。
function extractWeekCountFromHtml(htmlText) {
    const loopMatch = htmlText.match(/for\s*\(\s*var\s+i\s*=\s*0\s*;\s*i\s*<\s*(\d+)\s*;\s*i\+\+\s*\)/);
    if (loopMatch) {
        const weekCount = parseInt(loopMatch[1], 10);
        if (!isNaN(weekCount) && weekCount > 0) {
            return weekCount;
        }
    }

    return null;
}

// 读取页面中的学期下拉框选项和值。
function extractSemesterOptions(doc) {
    const selectElem = doc.getElementById("xnxqdm");
    if (!selectElem) {
        return null;
    }

    const semesters = [];
    const semesterValues = [];
    let defaultIndex = 0;

    Array.from(selectElem.querySelectorAll("option")).forEach((option, index) => {
        const label = option.innerText.trim();
        const value = option.value;
        if (!label || !value) return;

        semesters.push(label);
        semesterValues.push(value);
        if (option.selected || option.hasAttribute("selected")) {
            defaultIndex = index;
        }
    });

    if (semesters.length === 0) {
        return null;
    }

    return { semesters, semesterValues, defaultIndex };
}

// 粗略判断当前是否已经处于个人课表页面。
function isProbablySchedulePage() {
    const href = window.location.href;
    return /\/new\/student\/xsgrkb\/week\.page/i.test(href) || document.getElementById("xnxqdm") !== null;
}

// 导入开始前提示用户先进入课表页面。
async function promptUserToStart() {
    return await window.AndroidBridgePromise.showAlert(
        "成都医学院教务导入",
        "请先确保自己已经进入教务系统的课表页面，再继续导入。",
        "我已进入课表页"
    );
}

// 获取课表页 HTML 和文档对象，优先复用当前页面。
async function loadSchedulePageContext() {
    if (isProbablySchedulePage()) {
        return {
            htmlText: document.documentElement.outerHTML,
            doc: document,
            weekCount: extractWeekCountFromHtml(document.documentElement.outerHTML)
        };
    }

    const response = await fetch("/new/student/xsgrkb/week.page", {
        method: "GET",
        credentials: "include"
    });

    if (!response.ok) {
        throw new Error(`无法打开课表页面（HTTP ${response.status}）`);
    }

    const htmlText = await response.text();
    const parser = new DOMParser();
    const doc = parser.parseFromString(htmlText, "text/html");
    return {
        htmlText,
        doc,
        weekCount: extractWeekCountFromHtml(htmlText)
    };
}

// 让用户从页面已有学期中选择一个目标学期。
async function selectSemester(semesterOptions) {
    const selectedIndex = await window.AndroidBridgePromise.showSingleSelection(
        "选择学期",
        JSON.stringify(semesterOptions.semesters),
        semesterOptions.defaultIndex
    );

    if (selectedIndex === null || selectedIndex < 0) {
        return null;
    }

    return {
        label: semesterOptions.semesters[selectedIndex],
        value: semesterOptions.semesterValues[selectedIndex]
    };
}

// 请求指定学期的课程数据。
async function fetchCourseData(xnxqdm) {
    const formData = new URLSearchParams();
    formData.append("xnxqdm", xnxqdm);
    formData.append("zc", "");
    formData.append("d1", "2020-01-01 00:00:00");
    formData.append("d2", "2040-01-01 00:00:00");

    const response = await fetch("/new/student/xsgrkb/getCalendarWeekDatas", {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With": "XMLHttpRequest"
        },
        credentials: "include",
        body: formData.toString()
    });

    if (!response.ok) {
        throw new Error(`课表请求失败（HTTP ${response.status}）`);
    }

    return await response.json();
}

// 保存课程数据到应用。
async function saveCourses(courses) {
    await window.AndroidBridgePromise.saveImportedCourses(JSON.stringify(courses));
}

// 保存课表页面中解析出的作息时间。
async function saveTimeSlots(timeSlots) {
    if (!timeSlots.length) {
        return;
    }
    await window.AndroidBridgePromise.savePresetTimeSlots(JSON.stringify(timeSlots));
}

// 编排导入流程：提示、选学期、请求课程、保存课程与作息时间。
async function runImportFlow() {
    try {
        const confirmed = await promptUserToStart();
        if (!confirmed) {
            AndroidBridge.showToast("已取消导入");
            return;
        }

        AndroidBridge.showToast("正在读取课表页面信息...");
        const pageContext = await loadSchedulePageContext();
        const semesterOptions = extractSemesterOptions(pageContext.doc);

        if (!semesterOptions) {
            throw new Error("未找到学期列表，请先进入教务系统课表页面后再试");
        }

        const selectedSemester = await selectSemester(semesterOptions);
        if (!selectedSemester) {
            AndroidBridge.showToast("已取消导入");
            return;
        }

        AndroidBridge.showToast(`正在获取 ${selectedSemester.label} 的课表...`);
        const apiJson = await fetchCourseData(selectedSemester.value);
        const courses = parseCourseList(apiJson);

        if (courses.length === 0) {
            await window.AndroidBridgePromise.showAlert(
                "提示",
                "该学期没有获取到课程数据，请确认当前登录状态和所选学期是否正确。",
                "确定"
            );
            return;
        }

        const timeSlots = parseBusinessHoursFromHtml(pageContext.htmlText);

        await saveCourses(courses);
        try {
            await saveTimeSlots(timeSlots);
        } catch (error) {
            AndroidBridge.showToast(`课程已导入，作息时间导入失败：${error.message}`);
        }

        if (pageContext.weekCount) {
            console.log(`CMC: 从课表页识别到总周数 ${pageContext.weekCount} 周`);
        }

        AndroidBridge.showToast(`成功导入 ${courses.length} 门课程`);
        AndroidBridge.notifyTaskCompletion();
    } catch (error) {
        console.error("CMC import failed:", error);
        await window.AndroidBridgePromise.showAlert(
            "导入失败",
            error.message || String(error),
            "确定"
        );
    }
}

runImportFlow();
