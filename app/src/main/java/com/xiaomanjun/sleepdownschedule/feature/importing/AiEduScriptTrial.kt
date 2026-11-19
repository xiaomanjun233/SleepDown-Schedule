package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangImportSession
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal class EduScriptTrialFailure(message: String) : IllegalArgumentException(message)

internal fun JsonObject.toEduScriptDraft(
    config: ScheduleConfigEntity,
    periods: List<PeriodEntity>,
    redact: (String) -> String
): ImportDraft {
    fun fail(code: String, detail: String): Nothing {
        val observations = listOf("calls", "toasts", "logs").mapNotNull { field ->
            get(field)?.jsonArray?.takeLast(5)?.joinToString("；") { it.jsonPrimitive.content }?.takeIf(String::isNotBlank)
        }
        val diagnostic = (listOf(detail) + observations).joinToString(" | ").take(1_200)
        throw EduScriptTrialFailure("$code：${redact(diagnostic)}")
    }
    fun field(name: String) = get(name)?.jsonPrimitive?.contentOrNull
    field("error")?.let { fail(field("code") ?: "SCRIPT_RUNTIME", it) }
    val courses = field("courses") ?: fail("MISSING_COURSES", "未调用 saveImportedCourses 提交课程")
    return try {
        ShiguangImportSession().apply {
            begin(config, periods, allowImportedBellTimes = true)
            stageCourses(courses)
            field("config")?.let(::stageCourseConfig)
            field("slots")?.let(::stageTimeSlots)
            field("combo")?.let(::stageComboSchedule)
        }.complete()
    } catch (error: Exception) {
        fail("BRIDGE_VALIDATION", error.message.orEmpty().take(380))
    }
}

internal fun eduScriptTrialSource(key: String, source: String): String =
    "$EduScriptTrialRunner(${JsonPrimitive(key)}, ${JsonPrimitive(source)})"

// Trial bridge scoped to the generated script. Ordinary warehouse adapters still use the native
// bridge with interactive prompts. A trial neither changes the webpage's bridge nor writes a schedule.
private val EduScriptTrialRunner = """
    ((key, source) => {
      const page = window;
      const result = { done: false, calls: [], logs: [], toasts: [] };
      const shorten = value => String(value ?? '').slice(0, 240);
      let active = true, pending = 0, returned = false, notified = false, idleTimer;
      let bridgeError = null;
      const finish = (code, error) => {
        if (!active) return;
        active = false;
        clearTimeout(idleTimer);
        if (error) { result.code = code; result.error = shorten(error.message || error); }
        result.done = true;
      };
      const ready = () => {
        clearTimeout(idleTimer);
        if (!active || pending !== 0 || (!returned && !notified)) return;
        idleTimer = setTimeout(() => finish('BRIDGE_PAYLOAD', bridgeError), notified ? 0 : 500);
      };
      const startRequest = () => {
        pending++;
        clearTimeout(idleTimer);
        return () => { pending--; ready(); };
      };
      const capture = (list, value, limit) => {
        if (active && list.length < limit) list.push(shorten(value));
      };
      const stage = (name, field, shape) => async json => {
        if (!active) return false;
        try {
          if (typeof json !== 'string') throw new Error('参数应为 JSON.stringify(...)');
          const value = JSON.parse(json);
          if (!shape(value)) throw new Error('数据结构错误或为空');
          result[field] = json;
          capture(result.calls, name, 20);
          ready();
          return true;
        } catch (error) {
          bridgeError = new Error(name + ': ' + error.message);
          throw bridgeError;
        }
      };
      const array = value => Array.isArray(value) && value.length > 0;
      const object = value => value && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length > 0;
      const getSelection = async (title, json, defaultIndex) => {
        const options = JSON.parse(json);
        if (!Array.isArray(options) || options.some(option => typeof option !== 'string')) {
          throw new Error('showSingleSelection 需要字符串选项数组');
        }
        capture(result.calls, 'showSingleSelection', 20);
        return Math.min(options.length - 1, Math.max(0, Number.parseInt(defaultIndex, 10) || 0));
      };
      const api = {
        isAvailable: () => true,
        saveImportedCourses: stage('saveImportedCourses', 'courses', array),
        saveCourseConfig: stage('saveCourseConfig', 'config', object),
        savePresetTimeSlots: stage('savePresetTimeSlots', 'slots', array),
        saveComboSchedule: stage('saveComboSchedule', 'combo', object),
        showSingleSelection: getSelection,
        showAlert: async (title, message) => { capture(result.toasts, message || title, 12); return true; },
        showPrompt: async () => { throw new Error('先通过对话向用户确认所需值，试跑期间不能打开输入框'); },
        showToast: message => capture(result.toasts, message, 12),
        notifyTaskCompletion: () => { notified = true; ready(); }
      };
      const calls = new Proxy(page.console, {
        get(target, prop) {
          if (prop === 'log' || prop === 'warn' || prop === 'error' || prop === 'info') {
            return (...args) => capture(result.logs, prop + ': ' + args.map(shorten).join(' '), 16);
          }
          const value = target[prop];
          return typeof value === 'function' ? value.bind(target) : value;
        }
      });
      const fetch = async (...args) => {
        const end = startRequest();
        try { return await page.fetch(...args); } finally { end(); }
      };
      const xhr = typeof page.XMLHttpRequest === 'function' && new Proxy(page.XMLHttpRequest, {
        construct(target, args) {
          const request = Reflect.construct(target, args);
          const send = request.send;
          request.send = function (...values) {
            const end = startRequest();
            request.addEventListener('loadend', end, { once: true });
            try { return send.apply(request, values); }
            catch (error) { request.removeEventListener('loadend', end); end(); throw error; }
          };
          return request;
        }
      });
      const aliases = {
        shiguangBridgePromise: api, shiguangBridge: api,
        AndroidBridgePromise: api, AndroidBridge: api,
        console: calls, fetch, XMLHttpRequest: xhr
      };
      const scope = new Proxy(Object.create(null), {
        get(_, name) {
          if (name === 'window' || name === 'self' || name === 'globalThis') return scope;
          if (Object.prototype.hasOwnProperty.call(aliases, name)) return aliases[name];
          const value = page[name];
          return typeof value === 'function' && !value.prototype ? value.bind(page) : value;
        },
        set(_, name, value) { aliases[name] = value; return true; }
      });
      Object.defineProperty(page, key, {
        configurable: true,
        value: { result, dispose: () => finish(), snapshot: () => JSON.stringify(result) }
      });
      try {
        const AsyncFunction = Object.getPrototypeOf(async function() {}).constructor;
        const scriptBody = source.trim().replace(/;+\s*$/, '');
        const names = ['window', 'self', 'globalThis', ...Object.keys(aliases)];
        let execute;
        try { execute = new AsyncFunction(...names, '"use strict"; return (\n' + scriptBody + '\n);'); }
        catch (_) { execute = new AsyncFunction(...names, '"use strict";\n' + source); }
        execute.call(scope, scope, scope, scope, ...Object.values(aliases)).then(
          () => { returned = true; ready(); },
          error => finish(bridgeError === error ? 'BRIDGE_PAYLOAD' : 'SCRIPT_RUNTIME', error)
        );
      } catch (error) {
        finish('SCRIPT_SYNTAX', error);
      }
      return 'started';
    })
""".trimIndent()
