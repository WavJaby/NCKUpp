'use strict';
var imagesExtension = ['.png', '.svg', '.gif', '.webp'];
var module = {};
var excludeStart = /\/\*ExcludeStart\*\//g;

if (!window.fetch) {
    window.fetch = function (url, init) {
        return new Promise(function (resolve, reject) {
            var req = new XMLHttpRequest();
            req.onreadystatechange = function () {
                if (req.readyState === XMLHttpRequest.DONE)
                    resolve({
                        headers: {
                            get: function (key) {
                                return req.getResponseHeader(key);
                            }
                        },
                        text: function () {
                            return new Promise(function (resolve) {
                                resolve(req.responseText);
                            });
                        },
                        json: function () {
                            return new Promise(function (resolve) {
                                resolve(JSON.parse(req.responseText));
                            });
                        }
                    });
            }
            req.onerror = req.onabort = req.ontimeout = reject;

            var method = init && init.method ? init.method : 'GET';
            req.open(
                method,
                url instanceof URL ? url.href : url,
                true);
            if (method === 'POST' && init.body) {
                var contentType = init.headers ? init.headers['Content-Type'] : null;
                if (contentType)
                    req.setRequestHeader('Content-type', contentType);
                else
                    req.setRequestHeader('Content-type', 'application/x-www-form-urlencoded; charset=UTF-8');
            }
            req.send(init && init.body ? init.body : null);
        });
    }
}

/**
 * @param url {RequestInfo | URL}
 * @param [init] {RequestInit}
 * @param [onError] {function(e)} On error
 */
function fetchSync(url, init, onError) {
    var req = new XMLHttpRequest();
    var method = init && init.method ? init.method : 'GET';
    req.onerror = req.onabort = req.ontimeout = onError;
    req.open(
        method,
        url instanceof URL ? url.href : url,
        false);
    if (method === 'POST' && init.body) {
        var contentType = init.headers ? init.headers['Content-Type'] : null;
        if (contentType)
            req.setRequestHeader('Content-type', contentType);
        else
            req.setRequestHeader('Content-type', 'application/x-www-form-urlencoded; charset=UTF-8');
    }
    req.send(init && init.body ? init.body : null);
    return {
        body: req.response,
        type: req.responseType,
        headers: {
            get: function (key) {
                return req.getResponseHeader(key);
            }
        }
    };
}

/**
 * @param url {string}
 * @param [recursive] {boolean}
 * @return any
 */
function require(url, recursive) {
    var pathEnd = url.lastIndexOf('/');
    if (url.indexOf('.', pathEnd) === -1)
        url += '.js';

    var result = fetchSync(url);
    var contentType = result.headers.get('Content-Type');
    if (contentType.startsWith('image')) {
        var image = new Image();
        if (result.type.length === 0 || result.type === 'text')
            image.src = 'data:' + contentType.slice(0, contentType.indexOf(';')) + ';base64,' + btoa(result.body);
        else if (result.type === 'blob')
            image.src = URL.createObjectURL(result.body);
        return image;
    } else if (contentType.startsWith('application/json'))
        return JSON.parse(result.body);
    else if (contentType.startsWith('text/css')) {
        var style = document.createElement('style');
        style.textContent = result.body;
        return style;
    } else if (contentType.startsWith('application/javascript')) {
        return eval(`(function () {
            var module = {};
            ${result.body.replace(excludeStart, '/*').replace(/require\('\.\//g, 'require(\'' + url.slice(0, pathEnd + 1))}
            return module.exports;
        })`)();
    } else
        console.error(contentType);
}

/**
 * @param url {string}
 * @param [recursive] {boolean}
 * @return {Promise<any>}
 */
function async_require(url, recursive) {
    if (imagesExtension.some(function (i) {
        return url.endsWith(i);
    })) {
        var image = new Image();
        image.src = url;
        return new Promise(function (resolve) {
            image.onload = function () {
                resolve(image);
            }
        });
    }

    return fetch(url).then(function (i) {
        var contentType = i.headers.get('Content-Type');
        if (contentType.startsWith('application/json'))
            return i.json();
        else if (contentType.startsWith('text/css')) {
            var style = document.createElement('style');
            return i.text().then(i => {
                style.textContent = i;
                return style;
            });
        } else if (contentType.startsWith('application/javascript')) {
            return i.text().then(function (i) {
                    return eval(`(function () {
                        var module = {};
                        ${i.replace(excludeStart, '/*').replace(excludeEnd, '*/')}
                        return module.exports;
                    })`)();
                }
            );
        } else
            console.error(contentType);
    });
}