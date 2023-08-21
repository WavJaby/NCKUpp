'use strict';

import {button, div, h1, mountableStylesheet, span} from '../domHelper_v001.min.js';
import {fetchApi} from '../lib.js';

/**
 * @param {QueryRouter} router
 * @param loginState
 * @return {HTMLDivElement}
 */
export default function (router, loginState) {
	console.log('Preference adjust Init');
	// static element
	const styles = mountableStylesheet('./res/pages/preferenceAdjust.css');
	const adjustList = new AdjustList();
	const saveItemOrderButton = button('saveOrderBtn');
	const saveItemOrderDelayTime = 5000;
	let saveItemOrderTimeout = null, saveItemOrderTimeLeft = 0;
	let preferenceAdjustLoading = false;

	function onRender() {
		console.log('Course schedule Render');
		styles.mount();
	}

	function onPageOpen() {
		console.log('Course schedule Open');
		// close navLinks when using mobile devices
		window.navMenu.remove('open');
		styles.enable();
		onLoginState(loginState.state);
		loginState.addListener(onLoginState);
		adjustList.addListeners();
	}

	function onPageClose() {
		console.log('Course schedule Close');
		styles.disable();
		loginState.removeListener(onLoginState);
		adjustList.removeListeners();
	}

	/**
	 * @param {LoginData} state
	 */
	function onLoginState(state) {
		if (state && state.login) {
			if (preferenceAdjustLoading)
				return;
			preferenceAdjustLoading = true;
			fetchApi('/preferenceAdjust', 'Get preference').then(i => {
				if (i.success)
					renderAdjustList(i.data);
				else
					window.messageAlert.addError('Error', i.msg, 3000);
				preferenceAdjustLoading = false;
			});
		} else {
			if (state)
				window.askForLoginAlert();
			renderAdjustList(null);
		}
	}

	const dayOfWeek = ['星期一', '星期-二', '星期三', '星期四', '星期五', '星期六', '星期日'];

	function toTimeStr(time) {
		if (time === null)
			return '未定';
		time = time.split(',');
		return '(' + dayOfWeek[time[0]] + ')' + (time.length === 2 ? time[1] : time[1] + '~' + time[2]);
	}

	function renderAdjustList(state) {
		if (!state) {
			adjustList.setTitle(null);
			adjustList.clearItems();
			return;
		}

		console.log(state);
		adjustList.setTitle(state.name);
		const items = state.items.map(i => div(null, {key: i.key},
			span('[' + i.sn + '] '),
			span(i.name + ' '),
			span(toTimeStr(i.time) + ' '),
			span(i.credits + '學分 '),
			span(i.require ? '必修' : '選修'),
		));
		adjustList.updateItem(items);
	}

	function saveItemOrder() {
		saveItemOrderButton.classList.remove('show');
		const orderKeys = [];
		const items = adjustList.getItems();
		for (const item of items)
			orderKeys.push('data_item[]=' + item.key);
		const postData = orderKeys.join('&');
		console.log(postData);
	}

	adjustList.onchange = function () {
		clearTimeout(saveItemOrderTimeout);
		saveItemOrderTimeLeft = saveItemOrderDelayTime;
		saveItemOrderButton.textContent = '在' + (saveItemOrderTimeLeft / 1000) + '秒後儲存';
		saveItemOrderButton.classList.add('show');
		saveItemOrderTimeout = setInterval(() => {
			saveItemOrderTimeLeft -= 1000;
			if (saveItemOrderTimeLeft === 0) {
				clearTimeout(saveItemOrderTimeout);
				saveItemOrder();
				return;
			}

			saveItemOrderButton.textContent = '在' + (saveItemOrderTimeLeft / 1000) + '秒後儲存';
		}, 1000);
	};

	adjustList.ongrab = function () {
		saveItemOrderButton.classList.remove('show');
		clearTimeout(saveItemOrderTimeout);
	};

	return div('preferenceAdjust',
		{onRender, onPageOpen, onPageClose},
		adjustList.element,
		saveItemOrderButton,
	);
};

/**
 * @constructor
 */
function AdjustList() {
	const thisInstance = this;
	const adjustItemTitle = h1(null, 'title');
	const adjustItemHolder = div('adjustItemHolder');
	/**@type{HTMLDivElement}*/
	const adjustListBody = div('body');
	this.element = div('adjustList',
		adjustItemTitle,
		adjustItemHolder,
		adjustListBody,
	);
	/**@type{HTMLDivElement}*/
	let movingItem = null, grabbingItem = null;
	let startGrabbingOffsetX, startGrabbingOffsetY;

	this.addListeners = function () {
		window.addEventListener('mousemove', onMouseMove);
		window.addEventListener('touchmove', onMouseMove);
		window.addEventListener('mouseup', onMouseUp);
		window.addEventListener('touchend', onMouseUp);
		window.addEventListener('touchcancel', onMouseUp);
	};

	this.removeListeners = function () {
		window.removeEventListener('mousemove', onMouseMove);
		window.removeEventListener('touchmove', onMouseMove);
		window.removeEventListener('mouseup', onMouseUp);
		window.removeEventListener('touchend', onMouseUp);
		window.removeEventListener('touchcancel', onMouseUp);
	};

	this.setTitle = function (title) {
		adjustItemTitle.textContent = title;
	};

	this.updateItem = function (items) {
		this.clearItems();
		let i = 0;
		for (const item of items) {
			adjustListBody.appendChild(
				div('itemHolder', div('item noSelect', {onmousedown: onGrabItem, ontouchstart: onGrabItem}, item), {index: i++})
			);
		}
	};

	this.clearItems = function () {
		while (adjustListBody.firstChild)
			adjustListBody.removeChild(adjustListBody.firstChild);
	};

	this.getItems = function () {
		const items = new Array(adjustListBody.childElementCount);
		let i = 0;
		for (const element of adjustListBody.children) {
			items[i++] = element.firstElementChild.firstElementChild;
		}
		return items;
	}

	/**@type{function(): any}*/
	this.ongrab = null;
	/**@type{function(): any}*/
	this.onchange = null;

	function onGrabItem(e) {
		let pointerX, pointerY;
		if (e instanceof TouchEvent) {
			const touches = e.changedTouches;
			const touch = touches[0];
			pointerX = touch.clientX;
			pointerY = touch.clientY;
		} else {
			pointerX = e.clientX;
			pointerY = e.clientY;
		}
		startGrabbingOffsetX = this.offsetLeft - pointerX + adjustListBody.offsetLeft;
		startGrabbingOffsetY = this.offsetTop - pointerY;
		if (thisInstance.ongrab)
			thisInstance.ongrab();

		const grabbingItemHolder = this.parentElement;

		// Clone item to move
		movingItem = this.cloneNode(true);
		if (adjustListBody.getBoundingClientRect) {
			const bound = grabbingItemHolder.getBoundingClientRect();
			movingItem.style.width = bound.width + 'px';
			movingItem.style.height = bound.height + 'px';
		} else {
			movingItem.style.width = grabbingItemHolder.offsetWidth + 'px';
			movingItem.style.height = grabbingItemHolder.offsetHeight + 'px';
		}
		movingItem.style.left = (this.offsetLeft + adjustListBody.offsetLeft) + 'px';
		movingItem.style.top = (this.offsetTop) + 'px';
		if (adjustItemHolder.firstElementChild)
			adjustItemHolder.removeChild(adjustItemHolder.firstElementChild);
		adjustItemHolder.appendChild(movingItem);

		// Set original item style
		if (grabbingItem)
			grabbingItem.classList.remove('grabbing');
		grabbingItem = this;
		grabbingItem.classList.add('grabbing');

		// Set adjust list body to adjusting state
		const bodyWidth = adjustListBody.getBoundingClientRect
			? adjustListBody.getBoundingClientRect().width
			: adjustListBody.offsetWidth;
		adjustListBody.style.width = bodyWidth + 'px';
		adjustListBody.classList.add('adjusting');
	}

	function onMouseMove(e) {
		if (!movingItem)
			return;

		let pointerX, pointerY;
		if (e instanceof TouchEvent) {
			const touches = e.changedTouches;
			const touch = touches[0];
			pointerX = touch.clientX;
			pointerY = touch.clientY;
		} else {
			pointerX = e.clientX;
			pointerY = e.clientY;
		}

		movingItem.style.left = (pointerX + startGrabbingOffsetX) + 'px';
		movingItem.style.top = (pointerY + startGrabbingOffsetY) + 'px';

		const grabbingItemHolder = grabbingItem.parentElement;
		const change = movingItem.offsetTop - grabbingItemHolder.offsetTop;
		if (Math.abs(change) > grabbingItemHolder.offsetHeight * 0.9) {
			let switchGrabbingItemHolder;
			if (change < 0)
				switchGrabbingItemHolder = grabbingItemHolder.previousElementSibling;
			else
				switchGrabbingItemHolder = grabbingItemHolder.nextElementSibling;

			if (switchGrabbingItemHolder && switchGrabbingItemHolder !== adjustItemHolder) {
				const switchGrabbingItem = switchGrabbingItemHolder.firstElementChild;
				const nextPosition = grabbingItemHolder.offsetTop;

				// Animation
				switchGrabbingItem.style.top = switchGrabbingItem.offsetTop + 'px';
				// clearTimeout(switchGrabbingItem['animationTimeout']);
				setTimeout(() => {
					switchGrabbingItem.style.top = nextPosition + 'px';
				}, 5);
				// switchGrabbingItem['animationTimeout'] = setTimeout(() => {
				//     switchGrabbingItem.style.top = null;
				// }, 110);

				if (change < 0)
					grabbingItemHolder.parentElement.insertBefore(grabbingItemHolder, switchGrabbingItemHolder);
				else
					grabbingItemHolder.parentElement.insertBefore(switchGrabbingItemHolder, grabbingItemHolder);
			}
		}
	}

	function onMouseUp() {
		if (!movingItem)
			return;
		const movingItemFinal = movingItem;
		movingItem = null;

		// Check if item change
		if (thisInstance.onchange) {
			let changed = false;
			let i = 0;
			for (const element of adjustListBody.children) {
				if (element.index !== i) {
					changed = true;
					element.index = i;
				}
				++i;
			}
			if (changed)
				thisInstance.onchange();
		}

		// Animation
		movingItemFinal.classList.add('moveBack');
		movingItemFinal.style.top = (grabbingItem.parentElement.offsetTop) + 'px';
		movingItemFinal.style.left = (grabbingItem.parentElement.offsetLeft + adjustListBody.offsetLeft) + 'px';
		setTimeout(() => {
			grabbingItem.classList.remove('grabbing');
			if (movingItemFinal.parentElement === adjustItemHolder)
				adjustItemHolder.removeChild(movingItemFinal);

			// Reset item style
			for (const child of adjustListBody.children) {
				child.firstElementChild.style.top = null;
			}

			// Reset adjust list body to static state
			if (movingItem === null) {
				adjustListBody.classList.remove('adjusting');
				adjustListBody.style.width = null;
			}
		}, 105);
	}
}