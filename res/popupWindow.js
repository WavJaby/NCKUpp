import {button, div} from './lib/domHelper_v002.min.js';

/**
 * @typedef PopupWindowOption
 * @property {HTMLElement} [root] Root element for window. Default document.body
 */

/**
 * @param {PopupWindowOption} [options]
 * @constructor
 */
export default function PopupWindow(options) {
	const closeButton = button('closeButton', null, windowClose, div('icon'));
	const popupWindowBody = div('popupWindowBody', closeButton, div());
	const popupWindow = div('popupWindow', popupWindowBody, {onclick: e => e.target === popupWindow && windowClose()});

	if (!options)
		options = {root: document.body};

	/**
	 * @param {HTMLElement} content
	 */
	this.setWindowContent = function (content) {
		popupWindowBody.replaceChild(content, popupWindowBody.lastChild);
	}

	this.windowClose = windowClose;
	this.windowOpen = function () {
		options.root.appendChild(popupWindow);
		window.addEventListener('keyup', onkeyup);
	};

	function windowClose() {
		options.root.removeChild(popupWindow);
		window.removeEventListener('keyup', onkeyup);
	}

	function onkeyup(e) {
		if (e.key === 'Escape')
			windowClose();
	}
}