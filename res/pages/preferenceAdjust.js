'use strict';

import {button, div, h1, mountableStylesheet, span} from '../domHelper_v001.min.js';
import {fetchApi} from '../lib.js';

/**
 * @param {QueryRouter} router
 * @param {Signal} loginState
 * @return {HTMLDivElement}
 */
export default function (router, loginState) {
	console.log('Preference adjust Init');
	// static element
	const styles = mountableStylesheet('./res/pages/preferenceAdjust.css');
	const saveItemOrderButton = button('saveOrderBtn', null, saveItemOrder);
	const saveItemOrderDelayTime = 5000;
	const dayOfWeek = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
	let saveItemOrderCountdown = null, saveItemOrderTimeLeft = 0;
	let preferenceAdjustLoading = false;
	let /**@type{AdjustList}*/lastSelectTab = null;
	let actionKey = null, removeKey = null;

	const adjustListTabButtons = div('tabsBtn');
	const adjustListTabs = div('tabs');

	function onRender() {
		console.log('Course schedule Render');
		styles.mount();
	}

	function onPageOpen() {
		console.log('Course schedule Open');
		styles.enable();
		onLoginState(loginState.state);
		loginState.addListener(onLoginState);
	}

	function onPageClose() {
		console.log('Course schedule Close');
		styles.disable();
		loginState.removeListener(onLoginState);
	}

	/**
	 * @param {LoginData} state
	 */
	function onLoginState(state) {
		if (state && state.login) {
			updatePreferenceAdjust();
		} else {
			if (state)
				window.askForLoginAlert();
			renderAdjustList(null);
		}
	}

	function updatePreferenceAdjust() {
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
	}

	function toTimeStr(time) {
		if (time === null)
			return '未定';
		time = time.split(',');
		return '(' + dayOfWeek[time[0]] + ')' + (time.length === 2 ? time[1] : time[1] + '~' + time[2]);
	}

	function renderAdjustList(state) {
		lastSelectTab = null;
		while (adjustListTabButtons.firstChild)
			adjustListTabButtons.removeChild(adjustListTabButtons.firstChild);
		while (adjustListTabs.firstChild)
			adjustListTabs.removeChild(adjustListTabs.firstChild);

		if (!state)
			return;

		// console.log(state);
		actionKey = state.action;
		removeKey = state.remove;
		const tabs = state.tabs;
		for (const tab of tabs) {
			const adjustList = new AdjustList();
			adjustList.typeKey = tab.type;
			const items = tab.items.map(i => div('courseBlock', {key: i.key},
				span('[' + i.sn + '] '),
				span(i.name + ' '),
				span(toTimeStr(i.time) + ' '),
				span(i.credits + '學分 '),
				span(i.require ? '必修' : '選修'),
				button('removeBtn', '移除', removeCourse, {courseData: i, typeKey: tab.type}),
			));
			adjustList.onchange = adjustListOnChange;
			adjustList.ongrab = saveItemOrderCountdownStop;
			adjustList.setTitle(tab.name);
			adjustList.setItems(items);
			adjustListTabButtons.appendChild(button(null, tab.name, onTabSelect, {adjustList: adjustList}));
			adjustListTabs.appendChild(adjustList.element);

			if (!lastSelectTab) {
				lastSelectTab = adjustList;
				adjustList.show();
			}
		}
	}

	function removeCourse() {
		const course = this.courseData;
		const typeKey = this.typeKey;
		const suffix = '[' + course.sn + '] ' + course.name;
		const deleteConform = confirm('是否要刪除 ' + suffix);
		if (!deleteConform)
			return;

		const form = 'mode=' + removeKey + '&type=' + typeKey + '&removeItem=' + course.key;
		fetchApi('/preferenceAdjust', 'Update preference', {method: 'POST', body: form}).then(response => {
			updatePreferenceAdjust();
			if (response.success) {
				window.messageAlert.addSuccess('志願排序刪除成功', response.msg, 5000);
			} else
				window.messageAlert.addError('志願排序刪除失敗', response.msg, 5000);
		});
	}

	function onTabSelect() {
		if (saveItemOrderCountdown) {
			return;
		}
		if (lastSelectTab)
			lastSelectTab.hide();
		lastSelectTab = this.adjustList;
		lastSelectTab.show();
	}

	function saveItemOrder() {
		saveItemOrderCountdownStop();
		saveItemOrderButton.classList.remove('show');
		const orderKeys = [];
		lastSelectTab.updateItemIndex();
		const items = lastSelectTab.getItems();
		for (const item of items)
			orderKeys.push(item.key);
		const itemsStr = orderKeys.join(',');
		const typeKey = lastSelectTab.typeKey;
		const form = 'mode=' + actionKey + '&type=' + typeKey + '&modifyItems=' + itemsStr;
		fetchApi('/preferenceAdjust', 'Update preference', {method: 'POST', body: form}).then(response => {
			if (response.success) {
				window.messageAlert.addSuccess('志願排序儲存成功', response.msg, 5000);
			} else {
				window.messageAlert.addError('志願排序儲存失敗', response.msg, 5000);
				updatePreferenceAdjust();
			}
		});
	}

	function adjustListOnChange() {
		clearTimeout(saveItemOrderCountdown);
		saveItemOrderTimeLeft = saveItemOrderDelayTime;
		saveItemOrderButton.textContent = '點我儲存, ' + (saveItemOrderTimeLeft / 1000) + '秒後自動儲存';
		saveItemOrderButton.classList.add('show');
		saveItemOrderCountdown = setInterval(() => {
			saveItemOrderTimeLeft -= 1000;
			if (saveItemOrderTimeLeft <= 0) {
				saveItemOrder();
				return;
			}
			saveItemOrderButton.textContent = '點我儲存, ' + (saveItemOrderTimeLeft / 1000) + '秒後自動儲存';
		}, 1000);
	}

	function saveItemOrderCountdownStop() {
		clearTimeout(saveItemOrderCountdown);
		saveItemOrderButton.classList.remove('show');
		saveItemOrderCountdown = null;
	}

	return div('preferenceAdjust',
		{onRender, onPageOpen, onPageClose},
		adjustListTabButtons,
		adjustListTabs,
		saveItemOrderButton,
	);
};

/**
 * @constructor
 */
function AdjustList() {
	const thisInstance = this;
	const adjustItemTitle = h1(null, 'title noSelect');
	const adjustItemHolder = div('adjustItemHolder');
	/**@type{HTMLDivElement}*/
	const adjustListBody = div('body');
	const element = this.element = div('adjustList',
		adjustItemTitle,
		adjustItemHolder,
		adjustListBody,
	);
	/**@type{HTMLDivElement}*/
	let movingItem = null, grabbingItem = null;
	let startGrabbingOffsetX, startGrabbingOffsetY;

	this.show = function () {
		window.addEventListener('mousemove', onMouseMove);
		window.addEventListener('touchmove', onMouseMove);
		window.addEventListener('mouseup', onMouseUp);
		window.addEventListener('touchend', onMouseUp);
		window.addEventListener('touchcancel', onMouseUp);
		element.classList.add('show');
	};

	this.hide = function () {
		element.classList.remove('show');
		window.removeEventListener('mousemove', onMouseMove);
		window.removeEventListener('touchmove', onMouseMove);
		window.removeEventListener('mouseup', onMouseUp);
		window.removeEventListener('touchend', onMouseUp);
		window.removeEventListener('touchcancel', onMouseUp);
	};

	this.setTitle = function (title) {
		adjustItemTitle.textContent = title;
	};

	this.setItems = function (items) {
		this.clearItems();
		let i = 0;
		for (const item of items) {
			adjustListBody.appendChild(
				div('itemHolder', div('item noSelect', {
					onmousedown: onGrabItem,
					ontouchstart: onGrabItem
				}, item), {index: i++})
			);
		}
	};

	this.updateItemIndex = function () {
		let i = 0;
		for (const item of adjustListBody.children) {
			item.index = i++;
		}
	}

	this.clearItems = function () {
		while (adjustListBody.firstChild)
			adjustListBody.removeChild(adjustListBody.firstChild);
	};

	this.getItems = function () {
		const items = new Array(adjustListBody.childElementCount);
		let i = 0;
		for (const itemHolder of adjustListBody.children) {
			items[i++] = itemHolder.firstElementChild.firstElementChild;
		}
		return items;
	}

	/**@type{function(): any}*/
	this.ongrab = null;
	/**@type{function(): any}*/
	this.onchange = null;

	function onGrabItem(e) {
		if (e.target instanceof HTMLButtonElement)
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

		for (const itemHolder of adjustListBody.children) {
			// Set item holder height
			itemHolder.style.height = itemHolder.offsetHeight + 'px';
			// Set item position
			itemHolder.firstElementChild.style.top = itemHolder.offsetTop + 'px';
		}

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
				if (change < 0)
					adjustListBody.insertBefore(grabbingItemHolder, switchGrabbingItemHolder);
				else
					adjustListBody.insertBefore(switchGrabbingItemHolder, grabbingItemHolder);
				const switchGrabbingItem = switchGrabbingItemHolder.firstElementChild;

				// Animation
				grabbingItem.style.top = grabbingItemHolder.offsetTop + 'px';
				switchGrabbingItem.style.top = switchGrabbingItemHolder.offsetTop + 'px';
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
					break;
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
			for (const itemHolder of adjustListBody.children) {
				itemHolder.style.height = null;
				itemHolder.firstElementChild.style.top = null;
			}

			// Reset adjust list body to static state
			if (movingItem === null) {
				adjustListBody.classList.remove('adjusting');
				adjustListBody.style.width = null;
			}
		}, 100);
	}
}