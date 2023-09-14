'use strict';
var imagesExtension = ['.png', '.svg', '.gif', '.webp'];

if (!String.prototype.startsWith) {
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
 * @param url {string}
 * @param [init] {RequestInit}
 * @param [onError] {function(e)} On error
 */
function fetchSync(url, init, onError) {
	var req = new XMLHttpRequest();
	var method = init && init.method ? init.method : 'GET';
	if (onError)
		req.onerror = req.onabort = req.ontimeout = onError;
	req.open(
		method,
		url,
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
		body: req.response || req.responseText,
		type: req.responseType,
		headers: {
			get: function (key) {
				return req.getResponseHeader(key);
			}
		}
	};
}

var requireCache = {};

/**
 * @param url {string}
 * @return any
 */
function require(url) {
	var parsedUrl = new URL(url);
	var cache = requireCache[parsedUrl];
	if (cache)
		return cache;

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
			for (var i = 0; i < document.styleSheets.length; i++)
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
		// noinspection JSUnusedLocalSymbols
		return requireCache[parsedUrl] = eval('(function(){var exports={};' + parseScript(result.body, url, pathEnd) + 'return exports;})')();
	} else
		console.error(contentType);
}

/**
 * @param url {string}
 * @return {Promise<any>}
 */
function async_require(url) {
	var parsedUrl = new URL(url);
	var cache = requireCache[parsedUrl];
	if (cache)
		return new Promise(function (resolve) {
			resolve(cache);
		});

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
					for (var i = 0; i < document.styleSheets.length; i++)
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
				// noinspection JSUnusedLocalSymbols
				return requireCache[parsedUrl] = eval('(function(){var exports={};' + parseScript(i, url, pathEnd) + 'return exports;})')();
			});
		} else
			console.error(contentType);
	});
}

// For IE
function parseScript(script, url, pathEnd) {
	// console.log(url);
	// console.log(url.slice(0, pathEnd + 1));
	// script = script.replace(/\/\*ExcludeStart\*\//g, '/*').replace(/require\('\.\//g, 'require(\'' + url.slice(0, pathEnd + 1));
	var path = url.slice(0, pathEnd + 1);
	script = script
		// .replace(/import\(/g, 'async_require(\'' + path + '\'+');
		.replace(/import\(/g, 'async_require(');

	if (ieVersion === null || ieVersion === undefined || !window.Babel)
		return script;

	var result = Babel.transform(script, {
		sourceMap: false, comments: false, babelrc: false, plugins: [], presets: [
			['env', {
				'onPresetBuild': null,
				'targets': {'browsers': ['ie 6']},
				'forceAllTransforms': false,
				'shippedProposals': false,
				'useBuiltIns': false,
				'modules': 'commonjs',
			}],
		]
	});

	// document.body.innerText = result.code;

	return result.code
		.replace(/= ?require\(/g, '=require(\'' + path + '\'+');
}