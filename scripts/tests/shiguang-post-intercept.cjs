// Run with: node scripts/tests/shiguang-post-intercept.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.resolve(__dirname,
  '../../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/importing/shiguang/ShiguangPostInterceptScript.kt'), 'utf8');
const script = source.match(/= """([\s\S]*?)"""\.trimIndent/)[1];
const registered = [];
const sent = [];
const timers = [];
let accept = true;
let submit;
class Xhr {
  constructor() { this.headers = {}; }
  open() {}
  setRequestHeader(name, value) { this.headers[name] = value; }
  send(body) { this.sentBody = body; }
}
class Form extends FormData {
  constructor(form) { super(); if (form) form.entries.forEach(([key, value]) => this.append(key, value)); }
}
const window = {
  location: new URL('https://school.test/page'), URLSearchParams, Request,
  WebPostService: { register: (...args) => { registered.push(args); return accept; } },
  fetch: async (...args) => { sent.push(args); return {}; }
};
vm.runInNewContext(script, {
  window, XMLHttpRequest: Xhr, URL, URLSearchParams, Headers, Request, FormData: Form,
  document: { characterSet: 'UTF-8', addEventListener: (_, handler) => { submit = handler; } },
  setTimeout: action => timers.push(action)
});

(async () => {
  const request = new Request('https://school.test/submit', { method: 'POST', headers: { Authorization: 'test-only' } });
  const options = { body: 'value=1', credentials: 'omit' };
  await window.fetch(request, options);
  assert.equal(sent.at(-1)[1].headers.get('Authorization'), 'test-only');
  assert.equal(registered.at(-1)[5], false);
  assert.equal(request.headers.has('X-WebView-Post-Id'), false);
  assert.equal(options.headers, undefined);

  const formData = new Form();
  formData.append('file', new Blob(['binary']), 'test.txt');
  const xhr = new Xhr(); xhr.open('POST', '/upload'); xhr.send(formData);
  assert.equal(xhr.sentBody, formData);
  assert.equal(xhr.headers['X-WebView-Post-Id'], undefined);
  const before = registered.length;
  await window.fetch('https://other.test/submit', { method: 'POST', body: 'private' });
  assert.equal(registered.length, before);

  await window.fetch(new URL('https://school.test/save'), { method: 'POST', body: new URLSearchParams({ course: '数学' }) });
  assert.equal(registered.at(-1)[3], 'https://school.test/save');
  assert.equal(registered.at(-1)[1], 'course=%E6%95%B0%E5%AD%A6');
  assert.match(registered.at(-1)[2], /^application\/x-www-form-urlencoded/);

  const attributes = { action: '/submit?token=a%2Bb&value=one+two#section' };
  const original = attributes.action;
  const form = {
    tagName: 'FORM', method: 'post', enctype: 'application/x-www-form-urlencoded', entries: [['course', '数学']],
    querySelector: () => null, getAttribute: name => attributes[name] ?? null,
    setAttribute: (name, value) => { attributes[name] = value; }, removeAttribute: name => { delete attributes[name]; }
  };
  submit({ target: form });
  assert.equal(registered.at(-1)[3], 'https://school.test/submit?token=a%2Bb&value=one+two');
  assert.match(attributes.action, /token=a%2Bb&value=one\+two&_webview_post_id=[^#]+#section/);
  timers.splice(0).forEach(action => action());
  assert.equal(attributes.action, original);

  accept = false;
  const declined = new Xhr(); declined.open('POST', '/save'); declined.send('value=2');
  assert.equal(declined.headers['X-WebView-Post-Id'], undefined);
  assert.equal(declined.sentBody, 'value=2');
  console.log('Shiguang POST bridge regression checks passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
