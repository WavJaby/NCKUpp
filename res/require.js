'use strict';
var imagesExtension = ['.png', '.svg', '.gif', '.webp'];
var module = {};
var excludeStart = /\/\*ExcludeStart\*\//g;

// fu IE
if (String.prototype.startsWith === undefined) {
    String.prototype.startsWith = function (input) {
        if (this.length < input.length) return false;
        for (var i = 0; i < input.length; i++)
            if (this.charAt(i) !== input.charAt(i))
                return false;
        return true;
    }
}

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
 * @return any
 */
function require(url) {
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
        style.add = function () {
            document.head.appendChild(this);
            for (let i = 0; i < document.styleSheets.length; i++)
                if (document.styleSheets[i].ownerNode === style) {
                    style.rules = document.styleSheets[i].cssRules;
                    break;
                }
        };
        style.remove = function () {
            document.head.removeChild(this);
        }
        return style;
    } else if (contentType.startsWith('application/javascript')) {
        return eval('(function(){var module={};' + parseScript(result.body, url, pathEnd) + 'return module.exports;})')();
    } else
        console.error(contentType);
}

/**
 * @param url {string}
 * @return {Promise<any>}
 */
function async_require(url) {
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

    var pathEnd = url.lastIndexOf('/');
    if (url.indexOf('.', pathEnd) === -1)
        url += '.js';

    return fetch(url).then(function (i) {
        var contentType = i.headers.get('Content-Type');
        if (contentType.startsWith('application/json'))
            return i.json();
        else if (contentType.startsWith('text/css')) {
            var style = document.createElement('style');
            return i.text().then(function (i) {
                style.textContent = i;
                style.add = function () {
                    document.head.appendChild(this);
                    for (let i = 0; i < document.styleSheets.length; i++)
                        if (document.styleSheets[i].ownerNode === style) {
                            style.rules = document.styleSheets[i].cssRules;
                            break;
                        }
                };
                style.remove = function () {
                    document.head.removeChild(this);
                }
                return style;
            });
        } else if (contentType.startsWith('application/javascript')) {
            return i.text().then(function (i) {
                    return eval('(function(){var module={};' + parseScript(i, url, pathEnd) + 'return module.exports;})')();
                }
            );
        } else
            console.error(contentType);
    });
}

// TODO: make IE support
function parseScript(script, url, pathEnd) {
    if (ieVersion === null || ieVersion === undefined)
        return script.replace(excludeStart, '/*').replace(/require\('\.\//g, 'require(\'' + url.slice(0, pathEnd + 1));
    else {
        // fu IE
        script = script.replace(excludeStart, '/*').replace(/require\('\.\//g, 'require(\'' + url.slice(0, pathEnd + 1));
        var start, end = -1, newScript;
        while (true) {
            var moduleName = '__module__';
            if ((start = script.indexOf('const {', end + 1)) !== -1 && (end = script.indexOf('}', start + 7)) !== -1) {
                var requireEnd = script.indexOf(';', end);
                if (requireEnd !== -1) {
                    var variables = script.substring(start + 7, end).split(',').map(function (i) {
                        i = i.trim();
                        if (i.length === 0) return '';
                        var j = i.indexOf(':');
                        if (j === -1)
                            return 'window.' + i + '=' + moduleName + '.' + i;
                        return 'window.' + i.substring(j + 1) + '=' + moduleName + '.' + i.substring(0, j);
                    }).join(';');
                    newScript = script.substring(0, start) + 'var ' + moduleName + script.substring(end + 1, requireEnd + 1) + variables + script.substring(requireEnd);
                    end -= newScript.length - script.length;
                    script = newScript;
                }
            } else break;
        }

        // function
        for (let j = 0; j < 10; j++) {
            var i, bracketsCount = 0, inStr = -1, hasHeaderBrackets, entry, code;
            end = 0;
            start = 0;
            while (true) {
                if ((entry = script.indexOf('=>', end)) !== -1) {
                    hasHeaderBrackets = false;
                    for (i = entry - 1; i > -1; i--) {
                        code = script.charCodeAt(i);
                        if (code === 0x29) {
                            hasHeaderBrackets = true;
                            continue;
                        }
                        if (code === 0x28 || !hasHeaderBrackets && code === 0x2C) {
                            start = hasHeaderBrackets ? i : i + 1;
                            break;
                        }
                    }
                    i = entry + 2;
                    while ((code = script.charCodeAt(i)) === 0x20 || code === 0x0D || code === 0x0A) i++;
                    var bodyStart = i;
                    if(script.charCodeAt(i) !== 0x7B) {
                        for (; i < script.length; i++) {
                            code = script.charCodeAt(i);
                            // string
                            if (code === 0x22 || code === 0x27) {
                                if (inStr)
                                    inStr = -1;
                                else
                                    inStr = code;
                                continue;
                            }
                            // bracket
                            if (code === 0x28 || code === 0x7B) {
                                bracketsCount++;
                                continue;
                            }
                            if (bracketsCount > 0) {
                                if (code === 0x29 || code === 0x7D)
                                    bracketsCount--;
                                continue;
                            }

                            if (code === 0x29 || code === 0x7D || code === 0x2C || code === 0x3B) {
                                end = i;
                                break;
                            }
                        }
                        newScript = script.substring(0, start) +
                            (hasHeaderBrackets ? 'function ' + script.substring(start, entry) : ('function (' + script.substring(start, entry) + ')')) +
                            '{return ' + script.substring(bodyStart, end) + '}' +
                            script.substring(end);
                    } else {
                        newScript = script.substring(0, start) +
                            (hasHeaderBrackets ? 'function ' + script.substring(start, entry) : ('function (' + script.substring(start, entry) + ')')) +
                            script.substring(bodyStart);
                    }
                    end -= newScript.length - script.length;
                    script = newScript;
                } else
                    break;
            }
            if(start === 0)
                 break;
        }
        return script;
    }
}