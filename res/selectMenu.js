'use strict';

import {checkboxWithName, div, img, input, label, li, span, text, ul} from './domHelper_v001.min.js';

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
 * @return {HTMLElement}
 * @constructor
 */
export default function SelectMenu(placeholder, inputId, className, items, options) {
	if (!options)
		options = {};
	if (!options.searchBar)
		options.searchBar = true;
	if (!options.sortByValue)
		options.sortByValue = true;

	// Init elements
	const itemsContainer = ul('items');
	const searchInput = input(null, 'Search', null, {type: 'search', oninput: onSearch});
	const searchBox = div('content',
		options.searchBar ? div('searchBar', searchInput) : null,
		itemsContainer,
	);

	const resultBox = input(null, placeholder, inputId, {readOnly: true});
	const clearButton = img('./res/assets/close_icon.svg', 'clear_button', 'clearBtn');
	clearButton.style.display = 'none';

	// Select menu body
	const selectMenu = label('selectMenu noSelect ' + className, null,
		resultBox, clearButton,
		searchBox,
	);

	// Init select menu
	const selectedItems = [];
	selectItem(null);
	if (items)
		createItemsElement(itemsContainer, items);

	selectMenu.onclick = function (e) {
		if (!(e.target instanceof HTMLInputElement) && !(e.target instanceof HTMLLabelElement))
			e.preventDefault();
	};

	resultBox.onclick = function () {
		// Close search box
		if (searchBox.classList.contains('open')) {
			closeSelectMenu();
		}
		// Open search box
		else if (!searchBox.classList.contains('open')) {
			window.addEventListener('mouseup', checkClickOutsideSelectMenu);
			// Have item selected
			if (resultBox.value)
				clearButton.style.display = 'block';
			searchBox.classList.add('open');

			if (options.searchBar) {
				searchInput.focus();
				searchInput.oninput();
			}
		}
	};

	clearButton.onclick = function () {
		selectItem(null);
	};

	function closeSelectMenu() {
		clearButton.style.display = 'none';
		searchBox.classList.remove('open');
		window.removeEventListener('mouseup', checkClickOutsideSelectMenu);
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

	function onItemClick() {
		if (!options.multiple)
			closeSelectMenu();
		selectItem(this);
	}

	function selectItem(itemElement, force) {
		if (itemElement) {
			if (options.multiple) {
				const index = selectedItems.findIndex(i => i[0] === itemElement.itemValue);
				// Add item
				if (itemElement.input.checked || force) {
					itemElement.input.checked = true;
					if (index === -1)
						selectedItems.push([itemElement.itemValue, itemElement.itemName]);
				}
				// Remove item
				else {
					if (index !== -1)
						selectedItems.splice(index, 1);
				}
			} else {
				selectedItems.length = 1;
				selectedItems[0] = [itemElement.itemValue, itemElement.itemName];
			}

			if (options.multiple && options.sortByValue) {
				selectedItems.sort(([a], [b]) => a.localeCompare(b));
			}

			// Show items
			let result = '';
			for (let i of selectedItems) {
				if (result.length > 0)
					result += ', ';
				result += options.showValueName ? i[0] : i[1];
			}
			resultBox.value = result;
		} else {
			// Clear checked
			if (options.multiple) {
				for (const itemElement of itemsContainer.getElementsByTagName('li')) {
					itemElement.input.checked = false;
				}
			}
			selectedItems.length = 0;
			resultBox.value = '';
		}
	}

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
	 */
	function createItemsElement(parent, items) {
		for (let item of items) {
			if (item[1] instanceof Array) {
				// Create group
				const base = ul('group');
				parent.appendChild(span(item[0], 'groupTitle', {onclick: expandGroupToggle}));
				parent.appendChild(base);
				createItemsElement(base, /**@type{[string, Array]}*/item[1]);
			} else {
				// Create item
				if (options.multiple) {
					const checkbox = checkboxWithName(null, item[1], false);
					parent.appendChild(li('item multi', checkbox, {
						itemValue: item[0],
						itemName: item[1],
						input: checkbox.input,
						onclick: onItemClick
					}));
				} else
					parent.appendChild(li('item', text(item[1]), {itemValue: item[0], itemName: item[1], onclick: onItemClick}));
			}
		}
	}

	/**
	 * @param {ItemData[]} itemsData
	 */
	selectMenu.setItems = function (itemsData) {
		itemsContainer.innerHTML = '';
		createItemsElement(itemsContainer, itemsData);
	};

	selectMenu.selectItemByValue = function (values) {
		if (!options.multiple)
			values = [values];

		for (const value of values) {
			for (const itemElement of itemsContainer.getElementsByTagName('li')) {
				if (itemElement.itemValue === value) {
					selectItem(itemElement, true);
					break;
				}
			}
		}
	};

	selectMenu.getSelectedValue = function () {
		if (options.multiple)
			return selectedItems.map(i => i[0]);
		return selectedItems[0][0];
	};

	return selectMenu;
}