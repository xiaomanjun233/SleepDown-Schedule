package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.serialization.json.JsonPrimitive

/** One task-file probe. Results/logs are bounded; cancellation never resurrects a removed slot. */
internal val EduProbeRunnerScript = """
(function(key, run) {
  const state = {pending:true}, controller = new AbortController();
  const sensitive = /password|token|ticket|cookie|secret|authorization|credential/i;
  const clean = (key, value) => sensitive.test(key) ? '[登录凭据已隐藏]' : value;
  const logs = []; let requests = 0, timer;
  const finish = value => {
    if (window[key] !== state || !state.pending) return;
    clearTimeout(timer); Object.assign(state, value, {pending:false, requests:requests, logs:logs});
  };
  const text = value => JSON.stringify(value == null ? null : value, clean);
  const report = error => finish({error:String(error && error.message || error).slice(0,300)});
  const console = {};
  for (const level of ['log','info','warn','error','debug']) console[level] = (...args) => {
    if (logs.length < 20) logs.push({level:level, text:args.map(v=>text(v)).join(' ').slice(0,1000)});
  };
  const fetch = (input, options) => {
    const url = new URL(typeof input === 'string' ? input : input.url, location.href);
    const method = String(options && options.method || input && input.method || 'GET').toUpperCase();
    if (url.origin !== location.origin || !['GET','POST'].includes(method)) throw Error('探测仅支持当前教务的同源 GET/POST 查询');
    requests++;
    return window.fetch(input, Object.assign({}, options || {}, {credentials:'same-origin', signal:controller.signal}))
      .then(r=>{if(new URL(r.url).origin!==location.origin)throw Error('接口跳转到了其他地址，请核对登录态');return r;});
  };
  state.cancel = () => { controller.abort(); finish({error:'探测已取消'}); };
  window[key] = state;
  timer = setTimeout(()=>{controller.abort();finish({error:'探测超过20秒，请缩小查询范围'});},20000);
  Promise.resolve().then(()=>run.call(window,window,fetch,console))
    .then(value=>{const json=text(value);if(json.length>120000)throw Error('探测结果超过120000字符，请只返回需要核对的字段');finish({result:JSON.parse(json)});})
    .catch(report);
  return true;
})
""".trimIndent()

internal fun eduViewportScrollScript(direction: String, selector: String): String {
    require(direction in setOf("up", "down", "left", "right", "top", "bottom")) { "滚动方向需为 up/down/left/right/top/bottom" }
    require(selector.length <= 500)
    return """
        (()=>{const selector=${JsonPrimitive(selector)}, direction=${JsonPrimitive(direction)};
        const node=selector ? document.querySelector(selector) : document.scrollingElement;
        if(!node)return JSON.stringify({error:'没有找到滚动区域，请先读取页面或用探测脚本核对选择器'});
        const width=node.clientWidth, height=node.clientHeight;
        let x=node.scrollLeft,y=node.scrollTop;
        if(direction==='up')y-=height*0.8;if(direction==='down')y+=height*0.8;
        if(direction==='left')x-=width*0.8;if(direction==='right')x+=width*0.8;
        if(direction==='top')y=0;if(direction==='bottom')y=node.scrollHeight;
        node.scrollTo({left:x,top:y,behavior:'instant'});
        return JSON.stringify({x:node.scrollLeft,y:node.scrollTop,width:width,height:height,
          scrollWidth:node.scrollWidth,scrollHeight:node.scrollHeight,selector:selector});})()
    """.trimIndent()
}
