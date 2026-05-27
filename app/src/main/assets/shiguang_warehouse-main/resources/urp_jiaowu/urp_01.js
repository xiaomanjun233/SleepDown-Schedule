// 通用 URP 教务拾光课程表适配脚本

/**
 * 验证学年输入（4位数字）
 */
function validateYear(year) {
    if (!year || year.trim().length === 0) return "学年不能为空！";
    const yearRegex = /^\d{4}$/;
    if (!yearRegex.test(year)) return "请输入正确的4位数字学年（例如：2025）";
    return false;
}

/**
 * 解析位图格式的周次 (011100...)
 */
function parseWeekString(weekStr) {
    let weeks = [];
    if (!weekStr) return weeks;
    for (let i = 0; i < weekStr.length; i++) {
        if (weekStr[i] === '1') weeks.push(i + 1);
    }
    return weeks;
}

/**
 * 格式化时间 (0800 -> 08:00)
 */
function formatTime(timeStr) {
    if (timeStr && timeStr.length === 4) {
        return timeStr.substring(0, 2) + ":" + timeStr.substring(2);
    }
    return timeStr;
}

/**
 * 动态获取 API 路径
 */
function getApiUrl() {
    const baseUrl = window.location.origin;
    return `${baseUrl}/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback`;
}

async function promptUserToStart() {
    return await window.AndroidBridgePromise.showAlert(
        "教务系统课表导入",
        "请确保您已进入教务系统课表查询页面后再开始导入",
        "好的，开始导入"
    );
}

/**
 * 获取学年
 */
async function getAcademicYear() {
    return await window.AndroidBridgePromise.showPrompt(
        "学年设置",
        "请输入要导入课程的起始学年（例如 2025-2026 应输入2025）:",
        "", 
        "validateYear"
    );
}

/**
 * 获取学期
 */
async function selectSemester() {
    const semesters = ["1（第一学期）", "2（第二学期）"];
    return await window.AndroidBridgePromise.showSingleSelection(
        "选择学期", 
        JSON.stringify(semesters),
        -1 
    );
}

/**
 * 网络请求和数据解析
 */
async function fetchAndParseJwData(academicYear, semesterIndex) {
    try {
        const semesterValue = parseInt(semesterIndex) + 1; 
        const endYear = parseInt(academicYear) + 1;
        const planCode = `${academicYear}-${endYear}-${semesterValue}-1`;
        
        const apiUrl = getApiUrl();
        console.log("正在通过动态地址获取教务数据:", apiUrl);

        AndroidBridge.showToast("正在获取教务数据...");
        
        const response = await fetch(apiUrl, {
            "headers": { "content-type": "application/x-www-form-urlencoded; charset=UTF-8" },
            "body": `&planCode=${planCode}`,
            "method": "POST",
            "credentials": "include"
        });

        const data = await response.json();
        
        if (!data) throw new Error("服务器未返回任何数据");
        
        // 严格遵循 dateList 结构解析
        if (!data.dateList || !Array.isArray(data.dateList)) {
            console.error("教务返回数据异常:", data);
            throw new Error("未能获取到课程列表，请确认是否已登录或页面正确");
        }

        // 解析时间段 (jcsjbs)
        const timeSlots = (data.jcsjbs || []).map(item => ({
            number: parseInt(item.jc),
            startTime: formatTime(item.kssj),
            endTime: formatTime(item.jssj)
        }));

        // 解析课程
        let courses = [];
        data.dateList.forEach(plan => {
            if (plan && plan.selectCourseList && Array.isArray(plan.selectCourseList)) {
                plan.selectCourseList.forEach(c => {
                    const teacher = (c.attendClassTeacher || "").replace(/\* /g, "").trim();
                    if (c.timeAndPlaceList && Array.isArray(c.timeAndPlaceList)) {
                        c.timeAndPlaceList.forEach(tp => {
                            courses.push({
                                name: c.courseName,
                                teacher: teacher,
                                position: (tp.teachingBuildingName || "") + (tp.classroomName || ""),
                                day: parseInt(tp.classDay),
                                startSection: parseInt(tp.classSessions),
                                endSection: parseInt(tp.classSessions) + parseInt(tp.continuingSession) - 1,
                                weeks: parseWeekString(tp.classWeek),
                                isCustomTime: false
                            });
                        });
                    }
                });
            }
        });

        if (courses.length === 0) {
            throw new Error("该学期暂无排课数据");
        }

        return { courses, timeSlots };
    } catch (e) {
        console.error("解析失败详情:", e);
        AndroidBridge.showToast("同步失败: " + e.message);
        return null;
    }
}

/**
 * 保存数据到应用
 */
async function saveToApp(result) {
    const courseSuccess = await window.AndroidBridgePromise.saveImportedCourses(JSON.stringify(result.courses));
    if (!courseSuccess) return false;

    if (result.timeSlots && result.timeSlots.length > 0) {
        await window.AndroidBridgePromise.savePresetTimeSlots(JSON.stringify(result.timeSlots));
    }
    
    await window.AndroidBridgePromise.saveCourseConfig(JSON.stringify({
        semesterTotalWeeks: 20 
    }));
    
    return true;
}

/**
 * 流程控制
 */
async function runImportFlow() {
    const alertResult = await promptUserToStart();
    if (!alertResult) return;

    const academicYear = await getAcademicYear();
    if (academicYear === null) {
        AndroidBridge.showToast("导入已取消");
        return;
    }

    const semesterIndex = await selectSemester();
    if (semesterIndex === null) {
        AndroidBridge.showToast("导入已取消");
        return;
    }

    const result = await fetchAndParseJwData(academicYear, semesterIndex);
    if (!result || result.courses.length === 0) return;

    if (await saveToApp(result)) {
        AndroidBridge.showToast(`成功导入 ${result.courses.length} 个课程时段`);
        AndroidBridge.notifyTaskCompletion(); 
    }
}

// 启动
runImportFlow();