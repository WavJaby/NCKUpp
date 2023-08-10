'use strict';

// const {div, input, ul, li, text, span, Signal, img} = require('.//domHelper');

/**
 * @constructor
 * @typedef {[string, string]|[string, Array]} Option
 * [optionID, displayName] or [groupName, options]
 */
function SelectMenu(id, placeholder) {
	const clearButton = img('./res/assets/close_icon.svg', 'clearBtn');
	clearButton.style.display = 'none';

	const optionsSignal = new Signal(div());
	const searchInput = input(null, 'Search', null, {type: 'search', oninput: onSearch});
	const searchBox = div('content',
		div('searchBar', searchInput),
		optionsSignal,
	);

	const resultBox = input(null, placeholder, id, {readOnly: true, type: 'secure'});

	// Select menu body
	const selectMenu = label('selectMenu noSelect', null, null,
		resultBox, clearButton,
		searchBox,
	);

	// Init select menu
	setOptionSelect(null);

	selectMenu.onclick = function (e) {
		if (e.target !== searchInput)
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
			searchInput.focus();
			searchInput.oninput();
		}
	};

	clearButton.onclick = function () {
		setOptionSelect(null);
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

	function onOptionClick() {
		closeSelectMenu();
		setOptionSelect(this);
	}

	function setOptionSelect(optionElement) {
		if (optionElement) {
			resultBox.value = optionElement.optionValue;
		} else {
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
		checkItem(optionsSignal.state);

		function checkItem(group) {
			let findItem = false;
			for (const item of group.children) {
				// Check option group
				if (item instanceof HTMLSpanElement && item.classList.contains('groupTitle')) {
					// Not searching, reset group expand
					if (searchValue.length === 0)
						resetGroupExpand(item);
					// Check if option find in group
					if (checkItem(item.nextElementSibling)) {
						item.classList.remove('hide');
						if (searchValue.length !== 0)
							expandGroupElement(item);
						findItem = true;
					} else {
						item.classList.add('hide');
					}
				}
				// Check option
				else if (item instanceof HTMLLIElement && item.classList.contains('option')) {
					if (item.textContent.indexOf(searchValue) !== -1 ||
						item.optionValue.indexOf(searchValue) !== -1) {
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
	 * @param {[string, Array]} options
	 */
	function createOptionsElement(parent, options) {
		for (let option of options) {
			if (option[1] instanceof Array) {
				// Create group
				const base = ul('group');
				parent.appendChild(span(option[0], 'groupTitle', {onclick: expandGroupToggle}));
				parent.appendChild(base);
				createOptionsElement(base, /**@type{[string, Array]}*/option[1]);
			} else {
				// Create item
				parent.appendChild(li('option', text(option[1]), {optionValue: option[0], onclick: onOptionClick}));
			}
		}
	}

	/**
	 * @param {Option[]} options
	 */
	selectMenu.setOptions = function (options) {
		const base = ul('options');
		createOptionsElement(base, options);
		optionsSignal.set(base);
	};

	selectMenu.setValue = function (value) {
		for (const optionElement of optionsSignal.state.getElementsByTagName('li')) {
			if (optionElement.optionValue === value) {
				setOptionSelect(optionElement);
				break;
			}
		}
	};

	return selectMenu;
}