'use strict';
var imagesExtension = ['.png', '.svg', '.gif', '.webp'];
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
				return eval('(function(){var module={};' + parseScript(i, url, pathEnd) + 'return module.exports;})')();
			});
		} else
			console.error(contentType);
	});
}

// TODO: make IE support
function parseScript(script, url, pathEnd) {
	script = script.replace(excludeStart, '/*').replace(/require\('\.\//g, 'require(\'' + url.slice(0, pathEnd + 1));
	if (ieVersion === null || ieVersion === undefined)
		return script;
	else {
		// fu IE
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
							return 'var ' + i + '=' + moduleName + '.' + i;
						return 'var ' + i.substring(j + 1) + '=' + moduleName + '.' + i.substring(0, j);
					}).join(';');
					newScript = script.substring(0, start) + 'var ' + moduleName + script.substring(end + 1, requireEnd + 1) + variables + script.substring(requireEnd);
					end -= newScript.length - script.length;
					script = newScript;
				}
			} else break;
		}

		// const, let
		script = script.replace(/const/g, 'var').replace(/let/g, 'var');

		// function ...
		var funcRegx = /\.\.\.(\w+)\) ?\{/g;
		var result;
		while ((result = funcRegx.exec(script)) !== null) {
			if (result.index === funcRegx.lastIndex) {
				funcRegx.lastIndex++;
			}
			var parameters = '_v1,_v2,_v3,_v4,_v5,_v6,_v7,_v8,_v9,_v10,_v11,_v12,_v13,_v14,_v15,_v16,_v17,_v18,_v19,_v20){var ' +
				result[1] + '=[_v1,_v2,_v3,_v4,_v5,_v6,_v7,_v8,_v9,_v10,_v11,_v12,_v13,_v14,_v15,_v16,_v17,_v18,_v19,_v20];' +
				'for(var i=0;i<' + result[1] + '.length;i++){if(!' + result[1] + '[i]){' + result[1] + '.length=i;break;}}';
			script = script.substring(0, result.index) + parameters + script.substring(result.index + result[0].length);
		}

		// object auto key
		end = -1;
		while ((start = script.indexOf('= {', end + 1)) !== -1) {
			var objs = [];
			var valStart = -1;
			var isWord;
			var j;
			for (j = start + 3; j < script.length; j++) {
				var charCode = script.charCodeAt(j);
				var charCode1 = script.charCodeAt(j + 1);
				// skip comment
				if (charCode === 0x2F && charCode1 === 0x2A) {
					while (script.charCodeAt(j++) !== 0x2A || script.charCodeAt(j) !== 0x2F) ;
				}
				// if (isWord && !(charCode > 0x40 && charCode < 0x5B || charCode > 0x60 && charCode < 0x7B))
				//     isWord = false;
				if (valStart === -1 && (charCode > 0x40 && charCode < 0x5B || charCode > 0x60 && charCode < 0x7B)) {
					valStart = j;
					isWord = true;
				} else if (valStart !== -1 && (charCode === 0x2C || charCode === 0x28 || charCode === 0x7D)) {
					var val = script.substring(valStart, j);
					// add key
					if (val.indexOf(':') === -1) {
						if (charCode === 0x28) {
							// skip function body
							while (j < script.length && (script.charCodeAt(j++) !== 0x7D || (script.charCodeAt(j) !== 0x2C && script.charCodeAt(j) !== 0x3B))) ;
							if (script.charCodeAt(j) === 0x3B)
								objs.push(val + ':function ' + script.substring(valStart, j - 1));
							else
								objs.push(val + ':function ' + script.substring(valStart, j));
						} else
							objs.push(val + ':' + val);
					} else
						objs.push(val);
					valStart = -1;
				}
				if (script.charCodeAt(j) === 0x3B) {
					break;
				}
			}
			end = j;
			newScript = script.substring(0, start) + '={' + objs.join(',') + '}' + script.substring(end);
			end -= newScript.length - script.length;
			script = newScript;
		}

		var objRegx = /\{(([\w:]+[, ]*)+)}/g;
		var result;
		while ((result = objRegx.exec(script)) !== null) {
			if (result.index === funcRegx.lastIndex) {
				funcRegx.lastIndex++;
			}
			var args = result[1].split(/, ?/g);
			for (let i = 0; i < args.length; i++) {
				if (args[i].indexOf(':') === -1)
					args[i] = args[i] + ':' + args[i];
			}

			// console.log(args.join(','));
			script = script.substring(0, result.index + 1) + args.join(',') + script.substring(result.index + result[1].length + 1);
		}


		// => function
		for (var j = 0; j < 10; j++) {
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
					if (script.charCodeAt(i) !== 0x7B) {
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
			if (start === 0)
				break;
		}

		// comma at end of function argument
		script = script.replace(/,[ \r\n]*\)/g, ')');

		// document.body.innerText = script;

		return script;
	}
}