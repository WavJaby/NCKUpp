'use strict';

import {checkboxWithName, div, img, input, label, li, span, text, ul} from './lib/domHelper_v002.min.js';

/**
 * [itemValue, displayName] or [groupName, ItemData[]]
 * @typedef {[string, string]|[string, ItemData[]]} ItemData
 */

/**
 * @typedef SelectMenuOption
 * @property {boolean} [multiple] Multi selection. Default: false
 * @property {boolean} [showValueName] Show value name at result. Default: false
 * @property {boolean} [searchValue] Search with value. Default: false
 * @property {boolean} [searchBar] Show search bar. Default: true
 * @property {boolean} [sortByValue] Sort selected by value. Default: true
 */

/**
 * @param {string} placeholder
 * @param {string} inputId
 * @param {string} className
 * @param {ItemData[]} [items]
 * @param {SelectMenuOption} [options]
 * @constructor
 */
export default function SelectMenu(placeholder, inputId, className, items, options) {
	if (!options)
		options = {};
	if (options.searchBar == null)
		options.searchBar = true;
	if (options.sortByValue == null)
		options.sortByValue = true;

	const thisInstance = this;
	// Init elements
	const itemsContainer = ul('items');
	const searchInput = input(null, 'Search', null, {type: 'search', oninput: onSearch});
	const searchBox = div('content',
		options.searchBar ? div('searchBar', searchInput) : null,
		itemsContainer,
	);

	const resultBox = input(null, placeholder, null, {readOnly: true});
	const valueOut = input(null, null, inputId, {type: 'hidden', selectMenu: this});
	const clearButton = img('./res/assets/close_icon.svg', 'clear_button', 'clearBtn');
	clearButton.style.display = 'none';
	clearButton.onclick = () => selectItem(null);

	// Select menu body
	const selectMenu = this.element = label('selectMenu noSelect ' + className, null, {
			onmouseenter: () => setClearButtonState(selectedItems.length > 0),
			onmouseleave: () => setClearButtonState(menuOpen && selectedItems.length > 0)
		},
		resultBox, valueOut, clearButton,
		searchBox,
	);

	// Init select menu
	const selectedItems = [];
	let menuOpen = false;
	selectItem(null);
	if (items)
		createItemsElement(itemsContainer, items, false);

	selectMenu.onclick = function (e) {
		if (!(e.target instanceof HTMLInputElement) && !(e.target instanceof HTMLLabelElement))
			e.preventDefault();
	};

	resultBox.onclick = function () {
		// Close search box
		if (searchBox.classList.contains('open'))
			closeSelectMenu();
		// Open search box
		else
			openSelectMenu();
	};

	this.onSelectItemChange = null;

	/**
	 * @param {ItemData[]} itemsData
	 * @param {boolean} [defaultSelected]
	 */
	this.setItems = function (itemsData, defaultSelected) {
		clearItems();
		createItemsElement(itemsContainer, itemsData, !!defaultSelected);
	};

	this.clearItems = clearItems;

	this.selectItemByValue = function (values) {
		if (!options.multiple)
			values = [values];

		const items = itemsContainer.getElementsByTagName('li');
		for (const value of values) {
			for (const item of items) {
				if (item.itemValue === value) {
					selectItem(item, true);
					break;
				}
			}
		}
	};

	this.getSelectedValue = function () {
		if (options.multiple)
			return selectedItems.map(i => i[0]);
		return selectedItems[0][0];
	};

	function clearItems() {
		selectedItems.length = 0;
		resultBox.value = valueOut.value = '';
		while (itemsContainer.firstChild)
			itemsContainer.removeChild(itemsContainer.firstChild);
	}

	function setClearButtonState(state) {
		clearButton.style.display = state ? 'block' : 'none';
		if (state)
			resultBox.classList.add('withClearBtn');
		else
			resultBox.classList.remove('withClearBtn');
	}

	function closeSelectMenu() {
		menuOpen = false;
		setClearButtonState(false);
		searchBox.classList.remove('open');
		window.removeEventListener('mouseup', checkClickOutsideSelectMenu);
	}

	function openSelectMenu() {
		menuOpen = true;
		// Have item selected
		setClearButtonState(selectedItems.length > 0);
		searchBox.classList.add('open');
		window.addEventListener('mouseup', checkClickOutsideSelectMenu);

		if (options.searchBar) {
			searchInput.focus();
			searchInput.oninput();
		}
	}

	function checkClickOutsideSelectMenu(e) {
		let target = e.target;
		// console.log(e);
		while (target !== document.body) {
			// return if found select menu, do not close
			if (target === selectMenu)
				return;
			target = target.parentElement;
		}
		closeSelectMenu();
	}

	function updateOutputValue() {
		if (options.multiple && options.sortByValue)
			selectedItems.sort(([a], [b]) => a.localeCompare(b));

		let result = '';
		for (let i of selectedItems) {
			if (result.length > 0)
				result += ', ';
			result += options.showValueName ? i[0] : i[1];
		}
		resultBox.value = result;
		valueOut.value = options.multiple ? selectedItems.map(i => i[0]) : selectedItems[0][0];
	}

	function selectItem(itemElement, force) {
		if (itemElement) {
			if (options.multiple) {
				const /**@type{HTMLInputElement}*/ checkBox = itemElement.firstElementChild.firstElementChild;
				const index = selectedItems.findIndex(i => i[0] === itemElement.itemValue);
				// Add item
				if (checkBox.checked || force) {
					checkBox.checked = true;
					if (index === -1)
						selectedItems.push([itemElement.itemValue, itemElement.itemName]);
				}
				// Remove item
				else if (index !== -1)
					selectedItems.splice(index, 1);
			} else {
				selectedItems.length = 1;
				selectedItems[0] = [itemElement.itemValue, itemElement.itemName];
			}

			updateOutputValue();
			if (thisInstance.onSelectItemChange)
				thisInstance.onSelectItemChange();
			setClearButtonState(menuOpen && selectedItems.length > 0);
		} else {
			// Clear checked
			if (options.multiple) {
				for (const itemElement of itemsContainer.getElementsByTagName('li')) {
					itemElement.firstElementChild.input.checked = false;
				}
			}
			setClearButtonState(false);
			selectedItems.length = 0;
			resultBox.value = '';
			valueOut.value = '';
			if (thisInstance.onSelectItemChange)
				thisInstance.onSelectItemChange();
		}
	}

	// Group
	function expandGroupToggle() {
		if ((this.expend = !this.expend))
			expandGroupElement(this);
		else
			closeGroupElement(this);
	}

	function resetGroupExpand(groupTitle) {
		if (groupTitle.expend)
			expandGroupElement(groupTitle);
		else
			closeGroupElement(groupTitle);
	}

	function expandGroupElement(groupTitle) {
		groupTitle.classList.add('open');
		groupTitle.nextElementSibling.classList.add('open');
	}

	function closeGroupElement(groupTitle) {
		groupTitle.classList.remove('open');
		groupTitle.nextElementSibling.classList.remove('open');
	}

	function onSearch() {
		const searchValue = this.value;
		checkItem(itemsContainer);

		function checkItem(group) {
			let findItem = false;
			for (const item of group.children) {
				// Check item group
				if (item instanceof HTMLSpanElement && item.classList.contains('groupTitle')) {
					// Not searching, reset group expand
					if (searchValue.length === 0)
						resetGroupExpand(item);
					// Check if item find in group
					if (checkItem(item.nextElementSibling)) {
						item.classList.remove('hide');
						if (searchValue.length !== 0)
							expandGroupElement(item);
						findItem = true;
					} else {
						item.classList.add('hide');
					}
				}
				// Check item
				else if (item instanceof HTMLLIElement && item.classList.contains('item')) {
					if (item.itemName.indexOf(searchValue) !== -1 || (options.searchValue && item.itemValue.indexOf(searchValue) !== -1)) {
						item.classList.remove('hide');
						findItem = true;
					} else {
						item.classList.add('hide');
					}
				}
			}
			return findItem;
		}
	}

	/**
	 * @param {HTMLUListElement} parent
	 * @param {ItemData[]} items
	 * @param {boolean} defaultSelected
	 */
	function createItemsElement(parent, items, defaultSelected) {
		for (let item of items) {
			if (item[1] instanceof Array) {
				// Create group
				const base = ul('group');
				parent.appendChild(span(item[0], 'groupTitle', {onclick: expandGroupToggle}));
				parent.appendChild(base);
				createItemsElement(base, /**@type{[string, Array]}*/item[1], defaultSelected);
			} else {
				// Create item
				if (options.multiple) {
					const checkbox = checkboxWithName(null, item[1], defaultSelected, onCheckBoxClick);
					parent.appendChild(li('item multi', checkbox, {itemValue: item[0], itemName: item[1]}));
					if (defaultSelected)
						selectedItems.push([item[0], item[1]]);
				} else
					parent.appendChild(li('item', text(item[1]), {itemValue: item[0], itemName: item[1], onclick: onItemClick}));
			}
		}
		if (defaultSelected)
			updateOutputValue();
	}

	function onCheckBoxClick() {
		selectItem(this.parentElement.parentElement);
	}

	function onItemClick() {
		closeSelectMenu();
		selectItem(this);
	}
}