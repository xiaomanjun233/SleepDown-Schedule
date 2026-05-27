(() => {
  const DEFAULT_TIME_SLOTS = [
    { number: 1, startTime: '08:00', endTime: '08:45' },
    { number: 2, startTime: '08:50', endTime: '09:35' },
    { number: 3, startTime: '09:50', endTime: '10:35' },
    { number: 4, startTime: '10:40', endTime: '11:25' },
    { number: 5, startTime: '11:30', endTime: '12:15' },
    { number: 6, startTime: '14:05', endTime: '14:50' },
    { number: 7, startTime: '14:55', endTime: '15:40' },
    { number: 8, startTime: '15:45', endTime: '16:30' },
    { number: 9, startTime: '16:40', endTime: '17:25' },
    { number: 10, startTime: '17:30', endTime: '18:15' },
    { number: 11, startTime: '18:30', endTime: '19:15' },
    { number: 12, startTime: '19:20', endTime: '20:05' },
    { number: 13, startTime: '20:10', endTime: '20:55' },
  ];

  const toast = (message) => {
    if (window.AndroidBridge?.showToast) {
      window.AndroidBridge.showToast(message);
    } else {
      console.log(message);
    }
  };

  const normalize = (value) => String(value || '').replace(/\s+/g, ' ').trim();

  const parseWeeks = (value) => {
    const text = normalize(value);
    const weeks = new Set();
    const rangePattern = /(\d{1,2})\s*[-~至]\s*(\d{1,2})\s*周?/g;
    let match;
    while ((match = rangePattern.exec(text)) !== null) {
      const start = Number(match[1]);
      const end = Number(match[2]);
      for (let week = Math.min(start, end); week <= Math.max(start, end); week += 1) {
        weeks.add(week);
      }
    }
    const singlePattern = /第?\s*(\d{1,2})\s*周/g;
    while ((match = singlePattern.exec(text)) !== null) {
      weeks.add(Number(match[1]));
    }
    return [...weeks].filter((week) => week > 0).sort((a, b) => a - b);
  };

  const parseDay = (value) => {
    const text = normalize(value);
    const names = ['一', '二', '三', '四', '五', '六', '日'];
    for (let i = 0; i < names.length; i += 1) {
      if (text.includes(`星期${names[i]}`) || text.includes(`周${names[i]}`)) return i + 1;
    }
    const digit = text.match(/(?:星期|周)\s*([1-7])/);
    return digit ? Number(digit[1]) : 0;
  };

  const parseSections = (value) => {
    const text = normalize(value);
    const range = text.match(/第?\s*(\d{1,2})\s*[-~至]\s*(\d{1,2})\s*节/);
    if (range) return [Number(range[1]), Number(range[2])];
    const single = text.match(/第?\s*(\d{1,2})\s*节/);
    if (single) return [Number(single[1]), Number(single[1])];
    return [0, 0];
  };

  const pick = (item, keys) => {
    for (const key of keys) {
      if (item && item[key] != null && item[key] !== '') return item[key];
    }
    return '';
  };

  const parseCourseItem = (item) => {
    const raw = typeof item === 'string' ? { text: item } : item;
    const text = typeof item === 'string' ? item : JSON.stringify(item);
    const day = Number(pick(raw, ['day', 'weekday', 'xq', 'xqj', 'weekDay', 'week'])) || parseDay(text);
    const sections = parseSections(text);
    const start = Number(pick(raw, ['startSection', 'start', 'jc', 'ksjc', 'startJc', 'startNode'])) || sections[0];
    const end = Number(pick(raw, ['endSection', 'end', 'jsjc', 'endJc', 'endNode'])) || sections[1] || start;
    const name = normalize(pick(raw, ['name', 'courseName', 'kcmc', 'kcm', 'title', 'kcName']) || '未命名课程');
    return {
      name,
      teacher: normalize(pick(raw, ['teacher', 'teacherName', 'xm', 'jsxm', 'teachers'])),
      position: normalize(pick(raw, ['position', 'room', 'classroom', 'jxcd', 'cdmc', 'place', 'location'])),
      day,
      startSection: start,
      endSection: end,
      weeks: Array.isArray(raw.weeks) ? raw.weeks : parseWeeks(text),
    };
  };

  const collectJsonArrays = () => {
    const scripts = [...document.scripts].map((script) => script.textContent || '').join('\n');
    const matches = scripts.match(/\[[\s\S]{0,200000}?(?:course|kcmc|课程|teacher|jxcd|classroom)[\s\S]{0,200000}?\]/gi) || [];
    const arrays = [];
    for (const candidate of matches) {
      try {
        const parsed = JSON.parse(candidate);
        if (Array.isArray(parsed) && parsed.length) arrays.push(parsed);
      } catch (_) {
      }
    }
    return arrays.flat();
  };

  const parseDomCourses = () => {
    const selectors = ['[class*="course"]', '[class*="lesson"]', '[class*="schedule"]', '[class*="timetable"]', 'td', 'tr'];
    const elements = new Set();
    selectors.forEach((selector) => document.querySelectorAll(selector).forEach((element) => elements.add(element)));
    return [...elements]
      .map((element) => normalize(element.innerText || element.textContent))
      .filter((text) => text.length >= 8)
      .map(parseCourseItem)
      .filter((course) => course.day && course.startSection);
  };

  const uniqueCourses = (courses) => {
    const seen = new Set();
    return courses.filter((course) => {
      const key = `${course.name}-${course.day}-${course.startSection}-${course.endSection}-${course.position}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  };

  const main = async () => {
    if (!window.AndroidBridgePromise) {
      toast('当前环境不支持导入桥接，请在 App 内执行。');
      return;
    }
    const courses = uniqueCourses([
      ...collectJsonArrays().map(parseCourseItem),
      ...parseDomCourses(),
    ]).filter((course) => course.name && course.day && course.startSection);

    if (!courses.length) {
      toast('未能从当前页面识别课程表。请登录中南大学教务系统并进入课表页面后再执行导入。');
      return;
    }

    const totalWeeks = Math.max(20, ...courses.flatMap((course) => course.weeks || [0]));
    await window.AndroidBridgePromise.saveCourseConfig(JSON.stringify({
      semesterStartDate: null,
      totalWeeks,
    }));
    await window.AndroidBridgePromise.savePresetTimeSlots(JSON.stringify(DEFAULT_TIME_SLOTS));
    await window.AndroidBridgePromise.saveImportedCourses(JSON.stringify(courses));
    window.AndroidBridge?.notifyTaskCompletion?.();
  };

  main().catch((error) => {
    console.error(error);
    toast(`中南大学课表导入失败：${error.message || error}`);
  });
})();
