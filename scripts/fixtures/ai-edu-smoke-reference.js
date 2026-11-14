// Synthetic smoke test only. Never register this as a real school adapter.
(async function () {
    const reply = await fetch('/api/semester', { credentials: 'same-origin', cache: 'no-store' });
    if (!reply.ok) throw new Error('测试课程接口不可用');
    const data = await reply.json();
    if (data.synthetic !== true || !data.runCode || data.courses.length !== 2) throw new Error('测试数据不完整');
    await window.shiguangBridgePromise.saveCourseConfig(JSON.stringify(data.config));
    await window.shiguangBridgePromise.savePresetTimeSlots(JSON.stringify(data.timeSlots));
    await window.shiguangBridgePromise.saveImportedCourses(JSON.stringify(data.courses));
    window.shiguangBridge.notifyTaskCompletion();
})();
