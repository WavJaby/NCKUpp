if (window.ieVersion) {
	var requireToLoad = 4;

	loadScript('./res/require.js', onScriptLoad);
	loadScript('https://unpkg.com/@babel/polyfill@7.12.1/dist/polyfill.min.js', onScriptLoad);
	loadScript('https://unpkg.com/babel-standalone@6.26.0/babel.min.js', function () {
		loadScript('https://unpkg.com/babel-preset-env-standalone@1.6.2/babel-preset-env.min.js', onScriptLoad);
	});
	loadScript('https://inexorabletash.github.io/polyfill/url.js', onScriptLoad);

	if (ieVersion < 10) {
		requireToLoad += 2;
		loadScript('https://inexorabletash.github.io/polyfill/html.js', onScriptLoad);
		loadScript('https://inexorabletash.github.io/polyfill/dom.js', onScriptLoad);
	}

	function onScriptLoad() {
		if (--requireToLoad > 0)
			return;

		try {
			eval(parseScript(fetchSync('./index.js').body, './index.js', 1));
		} catch (e) {
			console.error(e);
		}

		if (ieVersion === 9)
			window.onload();
	}

	function loadScript(src, onload) {
		var script = document.createElement('script');
		script.src = src;
		script.onload = onload;

		if (ieVersion && ieVersion < 9)
			document.appendChild(script);
		else
			document.head.appendChild(script);
	}
}