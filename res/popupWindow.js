import {button, div} from './minjs_v000/domHelper.min.js';

/**
 * @typedef PopupWindowOption
 * @property {HTMLElement} [root] Root element for window. Default document.body
 * @property {int} [windowType] 0: Default, 1: Dialog
 * @property {string} [conformButton]
 * @property {string} [cancelButton]
 * @property {function(conform:boolean)} [onclose]
 */

/**
 * @typedef PopupWindow
 * @property {function(HTMLElement):void} windowSet
 * @property {function():void} windowClear
 * @property {function():void} windowOpen
 * @property {function():void} windowClose
 * @property {function():boolean} isEmpty
 */

PopupWindow.WIN_TYPE_DEFAULT = 0;
PopupWindow.WIN_TYPE_DIALOG = 1;

/**
 * @param {PopupWindowOption} [options]
 * @constructor
 * @return {PopupWindow}
 */
export default function PopupWindow(options) {
	if (!options)
		options = {root: document.body};
	if (!options.root)
		options.root = document.body

	const closeButton = button('closeButton', null, windowClose, div('icon'));
	const functionButtons = options.windowType === PopupWindow.WIN_TYPE_DIALOG ? div('buttons') : null;
	const popupWindowBody = div('popupWindowBody');
	const popupWindow = div('popupWindowOverlay', {onclick: e => e.target === popupWindow && windowClose(false)},
		div('popupWindow', closeButton, popupWindowBody, functionButtons),
	);
	let lastContentElement = null;

	if (options.windowType == null || options.windowType === PopupWindow.WIN_TYPE_DEFAULT) {
		popupWindow.classList.add('default');
	} else if (options.windowType === PopupWindow.WIN_TYPE_DIALOG) {
		popupWindow.classList.add('dialog');
		if (options.conformButton != null)
			functionButtons.appendChild(button('conform', options.conformButton, () => windowClose(true)));
		if (options.cancelButton != null)
			functionButtons.appendChild(button('cancel', options.cancelButton, () => windowClose(false)));
	}

	this.windowSet = windowSet;

	this.windowClear = function () {
		windowSet(null);
	};

	this.windowOpen = function () {
		options.root.appendChild(popupWindow);
		window.addEventListener('keyup', onkeyup);
	};

	this.windowClose = windowClose;

	this.isEmpty = function () {
		return lastContentElement === null;
	}

	function windowSet(content) {
		if (content == null || !(content instanceof HTMLElement)) {
			popupWindowBody.removeChild(lastContentElement);
			lastContentElement = null;
		} else if (lastContentElement === null) {
			popupWindowBody.insertBefore(content, functionButtons);
			lastContentElement = content;
		} else {
			popupWindowBody.replaceChild(content, lastContentElement);
			lastContentElement = content;
		}
	}

	function windowClose(conform) {
		if (options.onclose)
			options.onclose(conform);
		window.removeEventListener('keyup', onkeyup);
		popupWindow.parentElement.removeChild(popupWindow);
	}

	function onkeyup(e) {
		if (e.key === 'Escape')
			windowClose(false);
	}
}