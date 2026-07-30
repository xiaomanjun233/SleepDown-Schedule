// SleepDown 通用教务导入自检脚本。
// 只生成导入预览；用户在预览页确认前，不会写入或覆盖任何课表。

(function () {
    "use strict";

    const TEST_COURSES = [
        {
            name: "导入自检 · 连续周课程",
            teacher: "测试教师 A",
            position: "测试教室 101",
            day: 1,
            startSection: 1,
            endSection: 2,
            weeks: Array.from({ length: 18 }, function (_, index) { return index + 1; })
        },
        {
            name: "导入自检 · 单周课程",
            teacher: "测试教师 B",
            position: "测试实验室",
            day: 3,
            startSection: 5,
            endSection: 6,
            weeks: [1, 3, 5, 7, 9, 11, 13, 15, 17]
        },
        {
            name: "导入自检 · 双周课程",
            teacher: "测试教师 C",
            position: "测试教室 202",
            day: 5,
            startSection: 9,
            endSection: 10,
            weeks: [2, 4, 6, 8, 10, 12, 14, 16, 18]
        },
        {
            // 使用另一组受支持的字段名，顺便验证兼容映射。
            courseName: "导入自检 · 离散周课程",
            teachers: ["测试教师 D", "测试教师 E"],
            classroom: "测试教室 303",
            dayOfWeek: 7,
            sections: [11, 12],
            weekList: [2, 6, 10, 14, 18]
        }
    ];

    const TEST_TIME_SLOTS = [
        { number: 1, startTime: "08:00", endTime: "08:45" },
        { number: 2, startTime: "08:55", endTime: "09:40" },
        { number: 3, startTime: "10:00", endTime: "10:45" },
        { number: 4, startTime: "10:55", endTime: "11:40" },
        { number: 5, startTime: "14:00", endTime: "14:45" },
        { number: 6, startTime: "14:55", endTime: "15:40" },
        { number: 7, startTime: "16:00", endTime: "16:45" },
        { number: 8, startTime: "16:55", endTime: "17:40" },
        { number: 9, startTime: "19:00", endTime: "19:45" },
        { number: 10, startTime: "19:55", endTime: "20:40" },
        { number: 11, startTime: "20:50", endTime: "21:35" },
        { number: 12, startTime: "21:45", endTime: "22:30" }
    ];

    function requireBridgeMethod(name) {
        const bridge = window.AndroidBridgePromise;
        if (!bridge || typeof bridge[name] !== "function") {
            throw new Error("导入桥接不可用：" + name);
        }
        return bridge;
    }

    async function runImportSelfCheck() {
        try {
            const bridge = requireBridgeMethod("saveCourseConfig");
            requireBridgeMethod("savePresetTimeSlots");
            requireBridgeMethod("saveImportedCourses");

            const configSaved = await bridge.saveCourseConfig(JSON.stringify({
                semesterTotalWeeks: 18
            }));
            const slotsSaved = await bridge.savePresetTimeSlots(JSON.stringify(TEST_TIME_SLOTS));
            const coursesSaved = await bridge.saveImportedCourses(JSON.stringify(TEST_COURSES));

            if (!configSaved || !slotsSaved || !coursesSaved) {
                throw new Error("桥接接口返回失败");
            }

            if (window.AndroidBridge && typeof window.AndroidBridge.showToast === "function") {
                window.AndroidBridge.showToast("自检数据已生成，请核对导入预览");
            }
            if (!window.AndroidBridge || typeof window.AndroidBridge.notifyTaskCompletion !== "function") {
                throw new Error("导入完成回调不可用");
            }
            window.AndroidBridge.notifyTaskCompletion();
        } catch (error) {
            const message = "教务导入自检失败：" + (error && error.message ? error.message : String(error));
            console.error(message, error);
            if (window.AndroidBridge && typeof window.AndroidBridge.showToast === "function") {
                window.AndroidBridge.showToast(message);
            }
        }
    }

    runImportSelfCheck();
})();
