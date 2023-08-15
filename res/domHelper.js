'use strict';

const havePushState = typeof window.history.pushState === 'function';
let /**@type {Console}*/ debug = null;

String.prototype.toUnicode = function () {
	return this.replace(/[^\x00-\xFF]/g, function (ch) {
		return '\\u' + ch.charCodeAt(0).toString(16).padStart(4, '0');
	});
};

let urlSearchData = new URLSearchParams(window.location.search);
window.urlHashData = null;
urlHashDataUpdate();
window.pushHistory = function () {
	const newUrl = new URL(window.location);
	const hashStr = JSON.stringify(window.urlHashData);
	newUrl.hash = hashStr === '{}' ? '' : btoa(hashStr.toUnicode());
	newUrl.search = urlSearchData.toString();
	if (debug)
		debug.log('Append history', window.urlHashData, Object.fromEntries(urlSearchData));
	if (havePushState)
		window.history.pushState({}, document.title, newUrl);
	else
		window.location = newUrl;
};

function urlHashDataUpdate() {
	window.urlHashData = window.location.hash.length > 1 ? JSON.parse(atob(window.location.hash.slice(1))) : {};
}

function urlSearchDataUpdate() {
	urlSearchData = new URLSearchParams(window.location.search);
}

function addOption(element, options) {
	for (let i = 0; i < options.length; i++) {
		const option = options[i];
		if (option instanceof Array) {
			addOption(element, option);
			continue;
		}
		if (option instanceof Element || option instanceof Text)
			element.appendChild(option);
		else if (option instanceof StateChanger || option instanceof ShowIfStateChanger)
			option.init(element);
		else if (option instanceof Signal)
			new StateChanger(option).init(element);
		else if (option instanceof Object)
			Object.assign(element, option);
		else if (debug && option)
			debug.warn(element, 'options type error: ', option);
	}
}

/**
 * @param {string | Signal | TextStateChanger} text
 * @param {HTMLElement} element
 */
function parseTextInput(text, element) {
	if (text instanceof Signal)
		element.textContent = new TextStateChanger(text).init(element);
	else if (text instanceof TextStateChanger)
		element.textContent = text.init(element);
	else if (typeof text === 'string' || typeof text === 'number')
		element.textContent = text;
	else if (debug)
		debug.warn(element, 'text type error: ', text);
}

/**
 * @param {string | ClassList} className
 * @param {HTMLElement} element
 */
function parseClassInput(className, element) {
	if (className instanceof ClassList)
		className.init(element);
	else if (typeof className === 'string')
		element.className += className;
	else if (debug)
		debug.warn(element, 'classname type error: ', className);
}

/**
 * @constructor
 * @param {string | boolean | number | object} [initState] Init state data
 */
function Signal(initState) {
	const thisListener = [];
	this.state = initState !== undefined ? initState : null;

	this.addListener = function (listener) {
		thisListener.push(listener);
	};

	this.removeListener = function (listener) {
		const index = thisListener.indexOf(listener);
		if (index !== -1) thisListener.splice(index, 1);
	};

	/**
	 * @param {any} newState
	 * @param {boolean} [forceUpdate]
	 */
	this.set = function (newState, forceUpdate) {
		if (this.state === newState && !forceUpdate) return;
		this.state = newState;
		for (let i = 0; i < thisListener.length; i++)
			thisListener[i](newState);
	};

	this.update = function () {
		for (let i = 0; i < thisListener.length; i++)
			thisListener[i](this.state);
	}
}

/**
 * @param {Signal} signal
 * @param {function(state: any): Element} [renderState]
 */
function State(signal, renderState) {
	if (signal === null || signal === undefined) throw new TypeError('State signal not given');
	return new StateChanger(signal, renderState);
}

/**
 * @param {Signal} signal
 * @param {function(state: any): Element} [renderState]
 * @constructor
 */
function StateChanger(signal, renderState) {
	let lastElement;
	let thisParent;
	this.init = function (parent) {
		thisParent = parent;
		parent.appendChild(lastElement = renderState ? renderState(signal.state) : signal.state ? signal.state : document.createElement('div'));
		signal.addListener(onStateChange);
	};

	function onStateChange(state) {
		// if (debug !== null) debug.trace('[Debug] State change', this);
		const newElement = renderState ? renderState(state) : state;
		thisParent.replaceChild(newElement, lastElement);
		lastElement = newElement;
	}
}

/**
 * @param {Signal} signal
 * @param {function(state: any): string} [toString]
 */
function TextState(signal, toString) {
	if (signal === null || signal === undefined) throw new TypeError('State signal not given');
	return new TextStateChanger(signal, toString);
}

/**
 * @param {Signal} signal
 * @param {function(state: any): string} [toString]
 * @constructor
 */
function TextStateChanger(signal, toString) {
	let element = null;
	signal.addListener(updateText);

	this.init = function (newElement) {
		element = newElement;
		return toString ? toString(signal.state) : signal.state;
	};

	function updateText(state) {
		element.textContent = toString ? toString(state) : state;
	}
}

/**
 * @param {string} className
 * @constructor
 */
function ClassList(...className) {
	const classList = className;
	this.add = this.remove = this.toggle = this.contains = function () {
	};

	this.init = function (element) {
		if (element.classList) {
			for (let i = 0; i < classList.length; i++) {
				element.classList.add(classList[i]);
			}

			this.add = function (names) {
				element.classList.add(names);
			};
			this.remove = function (names) {
				element.classList.remove(names);
			};
			this.toggle = function (name) {
				return element.classList.toggle(name);
			};
			this.contains = function (name) {
				element.classList.contains(name);
			};
		} else {
			element.className += classList.join(' ');

			this.add = function (...className) {
				Array.prototype.push.apply(classList, className);
				element.className = classList.join(' ');
			};

			this.remove = function (...className) {
				for (let i = 0; i < className.length; i++) {
					const index = classList.indexOf(className[i]);
					if (index !== -1)
						classList.splice(index, 1);
				}
				element.className = classList.join(' ');
			};

			this.toggle = function (className) {
				const index = classList.indexOf(className);
				let toggle;
				if (index !== -1) {
					classList.splice(index, 1);
					toggle = false;
				} else {
					classList.push(className);
					toggle = true;
				}
				element.className = classList.join(' ');
				return toggle;
			};

			this.contains = function (className) {
				return classList.indexOf(className) !== -1;
			};
		}
	};
}

/**
 * @param url
 * @param parameter
 * @constructor
 */
function RouterLazyLoad(url, ...parameter) {
	this.url = url;
	this.parameters = parameter;
	this.loding = false;
}

/**
 * @param {string} titlePrefix
 * @param {Object.<string, string>} pageSuffix
 * @param {string} defaultPageId
 * @param {Object<string, RouterLazyLoad|HTMLElement>} routs
 * @param {HTMLElement} footer
 * @constructor
 */
function QueryRouter(titlePrefix, pageSuffix, defaultPageId,
					 routs, footer) {
	const thisI = this;
	const routerRoot = this.element = document.createElement('div');
	routerRoot.className = 'router';
	const loadedPage = {};
	const pageScrollSave = {};
	let lastPage, lastPageId = null;

	window.addEventListener('popstate', function () {
		urlSearchDataUpdate();
		urlHashDataUpdate();
		openPage(null, true);
	});
	this.openPage = openPage;
	this.init = function () {
		urlSearchDataUpdate();
		urlHashDataUpdate();
		openPage(null, true);
	}

	function openPage(pageId, isHistory) {
		if (!pageId)
			pageId = urlSearchData.get('page') || defaultPageId;

		// If user open same page (not from history), skip
		if (lastPageId === pageId && !isHistory)
			return;

		// If page not found, load default page
		if (!pageSuffix[pageId] || !routs[pageId])
			pageId = defaultPageId;

		// Get page
		getAndLoadPage(pageId, isHistory);
	}

	function getAndLoadPage(pageId, isHistory) {
		let page = loadedPage[pageId];
		// Page is loaded
		if (page) {
			pageReadyOpen(page, pageId, isHistory);
			return;
		}

		// Load page
		page = routs[pageId];

		// Lazy load
		if (page instanceof RouterLazyLoad) {
			if (!page.loding) {
				page.loding = true;
				import(page.url).then(function (i) {
					pageReadyOpen(loadedPage[pageId] = i.default(thisI, ...page.parameters), pageId, isHistory);
					page.loding = false;
				});
			}
		} else
			pageReadyOpen(loadedPage[pageId] = page, pageId, isHistory);
	}

	function pageReadyOpen(page, pageId, isHistory) {
		// Page change
		if (lastPageId !== pageId) {
			// Update title
			document.title = titlePrefix + ' ' + pageSuffix[pageId];
			// Add history
			urlSearchData.set('page', pageId);
			if (!isHistory)
				window.pushHistory();

			// Switch page element
			if (lastPage) {
				pageScrollSave[lastPageId] = routerRoot.scrollTop;
				if (lastPage.onPageClose) lastPage.onPageClose();
				routerRoot.replaceChild(page, lastPage);
			}
			// append page element on first open
			else {
				routerRoot.appendChild(page);
				if (footer)
					routerRoot.appendChild(footer);
			}
			lastPage = page;
		}
		// Render page if not render yet
		if (!page.render) {
			page.render = true;
			page.onRender();
		}

		// Restore last scroll position
		routerRoot.scrollTop = pageScrollSave[pageId] || 0;
		if (page.onPageOpen) page.onPageOpen(!!isHistory);
		if (thisI.onPageOpen) thisI.onPageOpen(lastPageId, pageId, page);
		lastPageId = pageId;
	}

	/**
	 * @param pageId
	 * @param lastPageId
	 * @param page
	 */
	this.onPageOpen = null;
}

/**
 * @param {Signal} signal
 * @param {HTMLElement} element
 */
function ShowIf(signal, element) {
	if (signal === null || signal === undefined) throw new TypeError('State signal not given');
	return new ShowIfStateChanger(signal, element);
}

/**
 * @constructor
 * @param {Signal} signal
 * @param {HTMLElement} element
 */
function ShowIfStateChanger(signal, element) {
	const emptyDiv = document.createElement('div');
	let showState = signal.state;
	let parent;
	signal.addListener(function (show) {
		if (showState !== show) {
			showState = show;
			if (show) {
				parent.replaceChild(element, emptyDiv);
				if (element.onRender)
					element.onRender();
			} else {
				if (element.onDestroy)
					element.onDestroy();
				parent.replaceChild(emptyDiv, element);
			}
		}
	});
	this.init = function (parentElement) {
		parent = parentElement;
		if (showState) {
			if (element.onRender)
				element.onRender();
		}
		parentElement.appendChild(showState ? element : emptyDiv);
	};
}

// Export
/**
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLDivElement}
 */
function div(classN, ...options) {
	const element = document.createElement('div');
	if (classN) parseClassInput(classN, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLElement}
 */
function nav(classN, ...options) {
	const element = document.createElement('nav');
	if (classN) parseClassInput(classN, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLUListElement}
 */
function ul(classN, ...options) {
	const element = document.createElement('ul');
	if (classN) parseClassInput(classN, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLLIElement}
 */
function li(classN, ...options) {
	const element = document.createElement('li');
	if (classN) parseClassInput(classN, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param {string} [placeholder]
 * @param {string} [id]
 * @param [options] Options for element
 * @return {HTMLInputElement}
 */
function input(classN, placeholder, id, ...options) {
	const element = document.createElement('input');
	if (classN) parseClassInput(classN, element);
	if (placeholder) element.placeholder = placeholder;
	if (id) element.id = id;
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param {string | Signal | TextStateChanger} text
 * @param {HTMLInputElement} inputElement
 * @param [options] Options for element
 * @return {HTMLLabelElement}
 */
function labelFor(classN, text, inputElement, ...options) {
	const element = document.createElement('label');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (inputElement) {
		if (!inputElement.id)
			inputElement.id = Math.random().toString(16).substring(2);
		element.htmlFor = inputElement.id;
	}
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param {string | Signal | TextStateChanger} text
 * @param [options] Options for element
 * @return {HTMLLabelElement}
 */
function label(classN, text, ...options) {
	const element = document.createElement('label');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param {function(this: GlobalEventHandlers, ev: Event)} [onchange]
 * @param {string} [id]
 * @param [options] Options for element
 * @return {HTMLInputElement}
 */
function checkbox(classN, onchange, id, ...options) {
	const element = document.createElement('input');
	element.type = 'checkbox';
	if (classN) parseClassInput(classN, element);
	element.onchange = onchange;
	if (id) element.id = id;
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param {string} title
 * @param {boolean} [defaultState]
 * @param {function(ev: Event): any} [onchange]
 * @param [options] Options for element
 * @return {HTMLLabelElement & {input: HTMLInputElement}}
 */
function checkboxWithName(classN, title, defaultState, onchange, ...options) {
	const input = document.createElement('input');
	input.type = 'checkbox';
	input.onchange = onchange;
	input.checked = !!defaultState;
	const checkmark = document.createElement('div');
	checkmark.className = 'checkmark';

	const element = document.createElement('label');
	element.className = 'checkboxWithName noSelect';
	if (classN) parseClassInput(classN, element);
	element.appendChild(input);
	element.appendChild(checkmark);
	element.appendChild(document.createTextNode(title));
	element.input = input;
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param {string | Signal | TextStateChanger} [text]
 * @param {function(MouseEvent)} [onClick]
 * @param [options] Options for element
 * @return {HTMLButtonElement}
 */
function button(classN, text, onClick, ...options) {
	const element = document.createElement('button');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (onClick) element.onclick = onClick;
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLTableElement}
 */
function table(classN, ...options) {
	const element = document.createElement('table');
	if (classN) parseClassInput(classN, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLTableSectionElement}
 */
function thead(classN, ...options) {
	const element = document.createElement('thead');
	if (classN) parseClassInput(classN, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLTableSectionElement}
 */
function tbody(classN, ...options) {
	const element = document.createElement('tbody');
	if (classN) parseClassInput(classN, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLTableColElement}
 */
function colgroup(classN, ...options) {
	const element = document.createElement('colgroup');
	if (classN) parseClassInput(classN, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLTableColElement}
 */
function col(classN, ...options) {
	const element = document.createElement('col');
	if (classN) parseClassInput(classN, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLTableRowElement}
 */
function tr(classN, ...options) {
	const element = document.createElement('tr');
	if (classN) parseClassInput(classN, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | Signal | TextStateChanger} [text]
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLTableCellElement}
 */
function th(text, classN, ...options) {
	const element = document.createElement('th');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | Signal | TextStateChanger} [text]
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLTableCellElement}
 */
function td(text, classN, ...options) {
	const element = document.createElement('td');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | Signal | TextStateChanger} [text]
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLHeadingElement}
 */
function h1(text, classN, ...options) {
	const element = document.createElement('h1');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | Signal | TextStateChanger} [text]
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLHeadingElement}
 */
function h2(text, classN, ...options) {
	const element = document.createElement('h2');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | Signal | TextStateChanger} [text]
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLHeadingElement}
 */
function h3(text, classN, ...options) {
	const element = document.createElement('h3');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | Signal | TextStateChanger} [text]
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLHeadingElement}
 */
function h4(text, classN, ...options) {
	const element = document.createElement('h4');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | Signal | TextStateChanger} [text]
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLHeadingElement}
 */
function h5(text, classN, ...options) {
	const element = document.createElement('h5');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | Signal | TextStateChanger} [text]
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLHeadingElement}
 */
function h6(text, classN, ...options) {
	const element = document.createElement('h6');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | Signal | TextStateChanger} [text]
 * @param {string} [href]
 * @param {string | ClassList} [classN] Class Name
 * @param {function(MouseEvent)} [onClick]
 * @param [options] Options for element
 * @return {HTMLAnchorElement}
 */
function a(text, href, classN, onClick, ...options) {
	const element = document.createElement('a');
	if (classN) parseClassInput(classN, element);
	if (href) element.href = href;
	if (text) parseTextInput(text, element);
	if (onClick) element.onclick = onClick;
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | Signal | TextStateChanger} [text]
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLParagraphElement}
 */
function p(text, classN, ...options) {
	const element = document.createElement('p');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string | Signal | TextStateChanger} [text]
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLSpanElement}
 */
function span(text, classN, ...options) {
	const element = document.createElement('span');
	if (classN) parseClassInput(classN, element);
	if (text) parseTextInput(text, element);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string} text
 * @return {Text}
 */
function text(text) {
	if (debug && typeof text !== 'string') {
		const element = document.createTextNode(text);
		debug.warn(element, 'text type error: ', text);
		return element;
	}

	return document.createTextNode(text);
}

/**
 * @return {HTMLBRElement}
 */
function br() {
	return document.createElement('br');
}

/**
 * @param {string} url
 * @param {string} [alt]
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLImageElement}
 */
function img(url, alt, classN, ...options) {
	const element = document.createElement('img');
	if (classN) parseClassInput(classN, element);
	if (url) element.src = url;
	if (alt !== null && alt !== undefined) element.alt = alt;
	else if (debug) debug.warn(element, 'Image alt not given');
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string} svgText
 * @param {string} [viewBox]
 * @param {string} [classN] Class Name
 * @param [options] Options for element
 * @return {SVGSVGElement}
 */
function svg(svgText, viewBox, classN, ...options) {
	const element = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
	element.innerHTML = svgText;
	if (classN) element.setAttributeNS(null, 'class', classN);
	if (viewBox) element.setAttributeNS(null, 'viewBox', viewBox);
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @typedef {Object} StylesheetMethods
 * @property {function()} mount Add style to header
 * @property {function()} unmount Remove style from header
 * @property {function()} enable Enable style
 * @property {function()} disable Disable style
 */
/**
 * @param {string} url stylesheet url
 * @return {HTMLLinkElement & StylesheetMethods}
 */
function mountableStylesheet(url) {
	const element = document.createElement('link');
	element.rel = 'stylesheet';
	element.href = url;
	element.mount = function () {document.head.appendChild(element);};
	element.unmount = function () {document.head.removeChild(element);};
	element.enable = function () {element.disabled = false;};
	element.disable = function () {element.disabled = true;};
	return element;
}

/**
 * @param [options] Options for element
 * @return {HTMLElement}
 */
function footer(...options) {
	const element = document.createElement('footer');
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string} url Url
 * @param {string | ClassList} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLIFrameElement}
 */
function iframe(url, classN, ...options) {
	const element = document.createElement('iframe');
	if (classN) parseClassInput(classN, element);
	if (url) element.src = url;
	if (options.length) addOption(element, options);
	return element;
}

/**
 * @param {string} tagN
 * @param {string} [classN] Class Name
 * @param [options] Options for element
 * @return {HTMLElement}
 */
function dom(tagN, classN, ...options) {
	const element = document.createElement(tagN);
	if (classN) parseClassInput(classN, element);
	if (options.length) addOption(element, options);
	return element;
}

function doomHelperDebug() {
	debug = console;
}

export {
	Signal,
	ShowIf,
	State,
	TextState,
	ClassList,
	QueryRouter, RouterLazyLoad,

	doomHelperDebug,

	div,
	// Nav, list
	nav, ul, li,
	// Input
	labelFor, label, input, checkbox, checkboxWithName, button,
	// Table
	table, thead, tbody, colgroup, col, th, tr, td,
	// Text
	h1, h2, h3, h4, h5, h6,
	a, p, span, text, br,
	// Image
	img, svg,
	// Other
	mountableStylesheet,
	footer,
	iframe,
	dom,
};