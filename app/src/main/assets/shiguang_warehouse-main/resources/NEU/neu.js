// 文件: neu.js

// 1. 显示校区选择弹窗
async function showCampusSelection() {
    const campuses = ["南湖校区", "浑南校区"];
    try {
        const selectedIndex = await window.AndroidBridgePromise.showSingleSelection(
            "选择你所在的校区",
            JSON.stringify(campuses),
            2
        );
        if (selectedIndex !== -1) {
            return campuses[selectedIndex]; // 返回用户选择的校区
        } else {
            return false; // 用户取消时返回 false
        }
    } catch (error) {
        console.error("显示单选列表弹窗时发生错误:", error);
        AndroidBridge.showToast("Single Selection：显示列表出错！" + error.message);
        return false; // 出现错误时也返回 false
    }
}

// 2. 从课表页面中提取课程数据
async function extractCoursesFromPage() {
    const iframe = document.querySelector('iframe');
    const iframeDoc = iframe.contentDocument || iframe.contentWindow.document;
    const dayCols = iframeDoc.querySelectorAll('.kbappTimetableDayColumnRoot');
    const lessons = [];

    dayCols.forEach((dayCol, dayIndex) => {
        const timeSlots = dayCol.children;
        const day = dayIndex >= 1 ? dayIndex : 7;
        let startSection = 0;
        let endSection = 0;
        
        for (let slot of timeSlots) {
            const flexValue = slot.style.flex;
            const nums = parseInt(flexValue.split(' ')[0]);
            startSection = endSection + 1;
            endSection = startSection + nums - 1;
            
            const courseColumns = slot.querySelectorAll('.kbappTimetableCourseRenderColumn');
            courseColumns.forEach(courseColumn => {
                const divsWithoutClass = courseColumn.querySelector('div[style*="flex"]:not([class])');
                let currentSection = startSection;
                if (divsWithoutClass) {
                    empty_num = parseInt(divsWithoutClass.style.flex.split(' ')[0]);
                    currentSection += empty_num;
                }
                const courseItem = courseColumn.querySelector('.kbappTimetableCourseRenderCourseItemContainer');
                const column_nums = parseInt(courseItem.style.flex.split(' ')[0]);
                const tooltip = courseItem.querySelector('.el-popover');
                
                // 检查tooltip是否存在
                if (tooltip) {
                    // 获取每一行作为一个单独的元素
                    const infoItems = tooltip.querySelectorAll('[class*="CourseItemInfoPopperInfo"]');
                    let name, details;
                    
                    if (infoItems.length > 0) {
                        infoItems.forEach((item, idx) => {
                            if (idx === 0) name = item.textContent.trim();
                            else if (idx === 1) details = parseCourseDetails(item.textContent.trim());
                            else if (idx === 2) return;
                        });
                    }
                    
                    lessons.push({
                        name: name || "", 
                        teacher: details?.teacher || "", 
                        position: details?.position || "", 
                        day: day, 
                        startSection: currentSection, 
                        endSection: currentSection + column_nums - 1,
                        weeks: details?.weeks || []
                    });
                }
            });
        } 
    });

    return { lessons: lessons };
}

// 2.1 解析课程详情字符串，提取周次、教师和地点信息
function parseCourseDetails(detailStr) {
    // 匹配所有周次模式
    const weekPattern = /(\d+-\d+周(?:\([单双]\))?|\d+周(?:\([单双]\))?)/g;
    const weekMatches = detailStr.match(weekPattern);
    
    let weeks = '';
    let remaining = detailStr;
    
    if (weekMatches) {
        // 提取所有周次部分
        weeks = weekMatches.join(',');
        // 从原字符串中移除周次部分
        weekMatches.forEach(match => {
            remaining = remaining.replace(match, '');
        });
    }
    
    // 按空格分割剩余部分
    const parts = remaining.trim().split(/\s+/).filter(p => p);
    
    let teacher = '';
    let position = '';
    if (parts.length > 0) {
        teacher = parts[0];
        if (parts.length > 1) {
            position = parts.slice(1).join(' '); // 修正这一行
        }
    }
    
    // 清理教师名中的多余逗号
    teacher = teacher.replace(/^[,，]/, '').replace(/[,，]$/, '');
    
    return {
        weeks: parseWeeksString(weeks),
        teacher: teacher.trim(),
        position: position.trim()
    };
}

// 2.2将周次文字提取成数组
function parseWeeksString(weeksStr) {
    if (!weeksStr) return [];
    
    const result = [];
    const weekParts = weeksStr.split(/[，,]/).map(part => part.trim());
    
    weekParts.forEach(part => {
        // 匹配单个数字周
        const singleMatch = part.match(/^(\d+)周(?:\(([单双])\))?$/);
        if (singleMatch) {
            const num = parseInt(singleMatch[1]);
            const type = singleMatch[2];
            if (!type || (type === '单' && num % 2 === 1) || (type === '双' && num % 2 === 0)) {
                result.push(num);
            }
            return;
        }
        
        // 匹配范围周
        const rangeMatch = part.match(/^(\d+)-(\d+)周(?:\(([单双])\))?$/);
        if (rangeMatch) {
            const start = parseInt(rangeMatch[1]);
            const end = parseInt(rangeMatch[2]);
            const type = rangeMatch[3];
            
            if (!type) {
                for (let i = start; i <= end; i++) result.push(i);
            } else if (type === '单') {
                for (let i = start; i <= end; i++) {
                    if (i % 2 === 1) result.push(i);
                }
            } else if (type === '双') {
                for (let i = start; i <= end; i++) {
                    if (i % 2 === 0) result.push(i);
                }
            }
        }
    });
    
    return [...new Set(result)].sort((a, b) => a - b);
}

// 3. 导入课程数据
async function SaveCourses(lessons) {
    const testCourses = lessons;

    try {
        const result = await window.AndroidBridgePromise.saveImportedCourses(JSON.stringify(testCourses));
    } catch (error) {
        console.error("导入课程时发生错误:", error);
        AndroidBridge.showToast("导入课程失败: " + error.message);
    }
}

// 4. 根据校区导入对应的时间段
async function importTimeSlotsByCampus(campus) {
    const hunNanTimeSlots = [
        { "number": 1, "startTime": "08:30", "endTime": "09:15" },
        { "number": 2, "startTime": "09:25", "endTime": "10:10" },
        { "number": 3, "startTime": "10:30", "endTime": "11:15" },
        { "number": 4, "startTime": "11:25", "endTime": "12:10" },
        { "number": 5, "startTime": "14:00", "endTime": "14:45" },
        { "number": 6, "startTime": "14:55", "endTime": "15:40" },
        { "number": 7, "startTime": "16:00", "endTime": "16:45" },
        { "number": 8, "startTime": "16:55", "endTime": "17:40" },
        { "number": 9, "startTime": "18:30", "endTime": "19:15" },
        { "number": 10, "startTime": "19:25", "endTime": "20:10" },
        { "number": 11, "startTime": "20:30", "endTime": "21:15" },
        { "number": 12, "startTime": "21:15", "endTime": "22:10" },
    ];
    
    const nanHuTimeSlots = [
        { "number": 1, "startTime": "08:00", "endTime": "08:45" },
        { "number": 2, "startTime": "08:55", "endTime": "09:40" },
        { "number": 3, "startTime": "10:00", "endTime": "10:45" },
        { "number": 4, "startTime": "10:55", "endTime": "11:40" },
        { "number": 5, "startTime": "14:00", "endTime": "14:45" },
        { "number": 6, "startTime": "14:55", "endTime": "15:40" },
        { "number": 7, "startTime": "16:00", "endTime": "16:45" },
        { "number": 8, "startTime": "16:55", "endTime": "17:40" },
        { "number": 9, "startTime": "18:30", "endTime": "19:15" },
        { "number": 10, "startTime": "19:25", "endTime": "20:10" },
        { "number": 11, "startTime": "20:20", "endTime": "21:05" },
        { "number": 12, "startTime": "21:15", "endTime": "22:00" },
    ];
    
    // 根据校区选择对应的时间表
    const timeSlotsToImport = (campus === "南湖校区") ? nanHuTimeSlots : hunNanTimeSlots;

    try {
        const result = await window.AndroidBridgePromise.savePresetTimeSlots(JSON.stringify(timeSlotsToImport));
    } catch (error) {
        console.error("导入时间段时发生错误:", error);
        window.AndroidBridge.showToast("导入时间段失败: " + error.message);
    }
}

// 5. 导入课表配置
async function SaveConfig() {
    // 注意：只传入要修改的字段，其他字段（如 semesterTotalWeeks）会使用 Kotlin 模型中的默认值
    const courseConfigData = {
        "semesterTotalWeeks": 18,
        "defaultClassDuration": 45,
        "defaultBreakDuration": 10,
        "firstDayOfWeek": 7
    };

    try {
        const configJsonString = JSON.stringify(courseConfigData);
        const result = await window.AndroidBridgePromise.saveCourseConfig(configJsonString);
    } catch (error) {
        console.error("导入配置时发生错误:", error);
        AndroidBridge.showToast("导入配置失败: " + error.message);
    }
}

/**
 * 编排这些异步操作，并在用户取消时停止后续执行。
 */
async function runAllDemosSequentially() {
    AndroidBridge.showToast("开始导入课表...");
    

    // 1. 校区选择
    const selectedCampus = await showCampusSelection();
    if (!selectedCampus) {
        console.log("用户取消了校区选择，停止后续执行。");
        AndroidBridge.showToast("已取消导入");
        return; // 用户取消，立即退出函数
    }

    // 3. 从课表页面中提取课程数据
    const PageInfo = await extractCoursesFromPage();
    const lessons = PageInfo.lessons;
    
    // 4. 保存课程数据到数据库
    await SaveCourses(lessons);
    
    // 5. 根据选择的校区导入对应的时间段
    await importTimeSlotsByCampus(selectedCampus);
    
    // 6. 保存底层配置
    await SaveConfig();

    // 发送最终的生命周期完成信号
    AndroidBridge.notifyTaskCompletion();
    AndroidBridge.showToast("课表导入完成！");
}

// 启动所有演示
runAllDemosSequentially();