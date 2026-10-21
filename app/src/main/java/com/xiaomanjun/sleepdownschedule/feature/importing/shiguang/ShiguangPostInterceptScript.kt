package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

internal val ShiguangPostInterceptScript = """
(function() {
    if (!window.WebPostService || window._postInterceptInjected) return;
    window._postInterceptInjected = true;
    var idHeader = 'X-WebView-Post-Id';
    var idParam = '_webview_post_id';

    function capture(url, method, body, contentType, allowCookies, navigation) {
        var destination = new URL(url, window.location.href);
        if (!navigation && destination.origin !== window.location.origin) return null;
        destination.hash = '';
        method = (method || 'GET').toUpperCase();
        if (method === 'GET' || method === 'HEAD') return null;
        if (window.URLSearchParams && body instanceof URLSearchParams) {
            body = body.toString();
            contentType = contentType || 'application/x-www-form-urlencoded;charset=UTF-8';
        }
        // FormData, Blob and streams retain their native encoding and multipart boundaries.
        if (typeof body !== 'string') return null;
        contentType = contentType || 'text/plain;charset=UTF-8';
        var id = 'post_' + Date.now() + '_' + Math.random().toString(36).slice(2);
        return window.WebPostService.register(id, body, contentType || '', destination.href, method, allowCookies) ? id : null;
    }

    var oldOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        this._sleepdownMethod = method;
        this._sleepdownUrl = url;
        this._sleepdownContentType = '';
        return oldOpen.apply(this, arguments);
    };
    var oldHeader = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
        if (name.toLowerCase() === 'content-type') this._sleepdownContentType = value;
        return oldHeader.apply(this, arguments);
    };
    var oldSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(body) {
        try {
            var id = capture(this._sleepdownUrl, this._sleepdownMethod, body, this._sleepdownContentType, true, false);
            if (id) oldHeader.call(this, idHeader, id);
        } catch (_) {}
        return oldSend.apply(this, arguments);
    };

    if (window.fetch) {
        var oldFetch = window.fetch;
        window.fetch = function(input, init) {
            try {
                var options = Object.assign({}, init || {});
                var request = window.Request && input instanceof Request ? input : null;
                var headers = new Headers(options.headers || (request && request.headers) || {});
                var method = options.method || (request && request.method) || 'GET';
                var credentials = options.credentials || (request && request.credentials) || 'same-origin';
                var id = capture(request ? request.url : input, method, options.body,
                    headers.get('Content-Type'), credentials !== 'omit', false);
                if (id) {
                    headers.set(idHeader, id);
                    options.headers = headers;
                    return oldFetch.call(this, input, options);
                }
            } catch (_) {}
            return oldFetch.apply(this, arguments);
        };
    }

    document.addEventListener('submit', function(event) {
        try {
            var form = event.target;
            if (!form || form.tagName.toLowerCase() !== 'form' || event.defaultPrevented) return;
            var button = event.submitter;
            var method = button && button.hasAttribute('formmethod') ? button.formMethod : form.method;
            var encoding = button && button.hasAttribute('formenctype') ? button.formEnctype : form.enctype;
            if (method.toLowerCase() !== 'post' || encoding !== 'application/x-www-form-urlencoded') return;
            var charset = (form.acceptCharset || document.characterSet || 'UTF-8').toLowerCase();
            if (charset !== 'utf-8' && charset !== 'utf8') return;
            if (form.querySelector('input[type="file"]')) return;
            var actionOwner = button && button.hasAttribute('formaction') ? button : form;
            var actionAttribute = actionOwner === form ? 'action' : 'formaction';
            var originalAction = actionOwner.getAttribute(actionAttribute);
            var url = new URL(originalAction || window.location.href, window.location.href);
            var entries = new FormData(form);
            if (button && button.form === form && button.name) entries.append(button.name, button.value);
            var body = new URLSearchParams(entries).toString();
            var id = capture(url.href, method, body, 'application/x-www-form-urlencoded;charset=UTF-8', true, true);
            if (!id) return;
            var query = url.search.slice(1).split('&').filter(function(part) {
                return part && part.split('=')[0] !== idParam;
            });
            query.push(idParam + '=' + id);
            url.search = query.join('&');
            actionOwner.setAttribute(actionAttribute, url.href);
            setTimeout(function() {
                if (originalAction === null) actionOwner.removeAttribute(actionAttribute);
                else actionOwner.setAttribute(actionAttribute, originalAction);
            }, 0);
        } catch (_) {}
    }, true);
})();
""".trimIndent()
