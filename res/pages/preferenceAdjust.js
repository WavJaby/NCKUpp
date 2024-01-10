'use strict';

import {button, div, h1, input, mountableStylesheet, span} from '../minjs_v000/domHelper.min.js';
import {fetchApi} from '../lib/lib.js';
import {courseSearch} from './courseSearch.js';

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
	let lastTab = null;
	let actionKey = null, removeKey = null;

	const adjustListTabButtons = div('tabsBtn');
	const adjustListTabs = div('tabs');

	function onRender() {
		console.log('Preference adjust Render');
		styles.mount();
	}

	function onPageOpen() {
		console.log('Preference adjust Open');
		styles.enable();
		// onLoginState(loginState.state);
		loginState.addListener(onLoginState);
	}

	function onPageClose() {
		console.log('Preference adjust Close');
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
		window.pageLoading.set(true);
		fetchApi('/preferenceAdjust', 'Get preference').then(i => {
			window.pageLoading.set(false);
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
		lastTab = null;
		while (adjustListTabButtons.firstChild)
			adjustListTabButtons.removeChild(adjustListTabButtons.firstChild);
		while (adjustListTabs.firstChild) {
			adjustListTabs.firstChild.adjustList.disable();
			adjustListTabs.removeChild(adjustListTabs.firstChild);
		}

		if (!state)
			return;

		// console.log(state);
		actionKey = state.action;
		removeKey = state.remove;

		const tabs = state.tabs;
		for (const tab of tabs) {
			console.log(tab)
			let expectA9Reg = null;
			// Multiple A9 reg
			if (tab.expectA9Reg != null && tab.expectA9RegVal != null) {
				const inputVal = input('value', null, null, {value: tab.expectA9RegVal, type: 'number'});
				expectA9Reg = div('expectA9Reg',
					span('第三階段通識期望抽中科目數: ', 'description'),
					inputVal,
					button('saveValBtn', '儲存', saveExpectA9RegVal, {input: inputVal, mode: tab.expectA9Reg}),
				);
			}

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
			adjustList.setItems(items);
			const currentTab = adjustListTabs.appendChild(div('tab', {adjustList: adjustList},
				h1(tab.name, 'title noSelect'),
				expectA9Reg,
				button('showInSearch', '在課程搜尋中顯示', showInSearch, {serialIds: tab.items.map(i => i.sn)}),
				adjustList.element,
			));
			adjustListTabButtons.appendChild(button(null, tab.name, onTabSelect, {tab: currentTab}));

			if (!lastTab) {
				lastTab = currentTab;
				lastTab.classList.add('show');
				adjustList.enable();
			}
		}
	}

	function showInSearch() {
		const rawQuery = {serial: this.serialIds.join(',')};
		courseSearch(router, rawQuery);
	}

	function saveExpectA9RegVal() {
		// console.log(this.input.value);
		fetchApi('/preferenceAdjust', 'Update preference', {
			method: 'POST',
			body: {mode: this.mode, expectA9RegVal: this.input.value}
		}).then(response => {
			if (response.success) {
				window.messageAlert.addSuccess('設定通識期望抽中科目數成功', response.msg, 5000);
			} else
				window.messageAlert.addError('設定通識期望抽中科目數失敗', response.msg, 5000);
		});
	}

	function removeCourse() {
		const course = this.courseData;
		const typeKey = this.typeKey;
		const suffix = '[' + course.sn + '] ' + course.name;
		const deleteConform = confirm('是否要刪除 ' + suffix);
		if (!deleteConform)
			return;

		fetchApi('/preferenceAdjust', 'Update preference', {
			method: 'POST',
			body: {mode: removeKey, type: typeKey, removeItem: course.key}
		}).then(response => {
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
		if (lastTab === this)
			return;
		if (lastTab) {
			lastTab.classList.remove('show');
			lastTab.adjustList.disable();
		}
		lastTab = this.tab;
		lastTab.classList.add('show');
		lastTab.adjustList.enable();
	}

	function saveItemOrder() {
		saveItemOrderCountdownStop();
		saveItemOrderButton.classList.remove('show');
		const orderKeys = [];
		const lastAdjustList = lastTab.adjustList;
		lastAdjustList.updateItemIndex();
		const items = lastAdjustList.getItems();
		for (const item of items)
			orderKeys.push(item.key);
		const itemsStr = orderKeys.join(',');
		const typeKey = lastAdjustList.typeKey;
		fetchApi('/preferenceAdjust', 'Update preference', {
			method: 'POST',
			body: {mode: actionKey, type: typeKey, modifyItems: itemsStr}
		}).then(response => {
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
	const adjustItemHolder = div('adjustItemHolder');
	/**@type{HTMLDivElement}*/
	const adjustListBody = div('body');
	const element = this.element = div('adjustList',
		adjustItemHolder,
		adjustListBody,
	);
	/**@type{HTMLDivElement}*/
	let movingItem = null, grabbingItem = null;
	let startGrabbingOffsetX, startGrabbingOffsetY;

	this.enable = function () {
		window.addEventListener('mousemove', onMouseMove);
		window.addEventListener('touchmove', onMouseMove);
		window.addEventListener('mouseup', onMouseUp);
		window.addEventListener('touchend', onMouseUp);
		window.addEventListener('touchcancel', onMouseUp);
	};

	this.disable = function () {
		window.removeEventListener('mousemove', onMouseMove);
		window.removeEventListener('touchmove', onMouseMove);
		window.removeEventListener('mouseup', onMouseUp);
		window.removeEventListener('touchend', onMouseUp);
		window.removeEventListener('touchcancel', onMouseUp);
	};

	this.setItems = function (items) {
		this.clearItems();
		let i = 0;
		for (const item of items) {
			adjustListBody.appendChild(
				div('itemHolder', div('item noSelect', {
					onmousedown: onGrabItem,
					onpointerdown: onGrabItem,
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