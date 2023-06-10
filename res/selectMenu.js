'use strict';

/*ExcludeStart*/
const {div, input, ul, li, text, span, Signal} = require('./domHelper');
const module = {};
/*ExcludeEnd*/


/**
 * @typedef {[string, string]|[string, Array]} Option
 * [optionID, displayName] or [groupName, options]
 */

module.exports = function (id, placeholder) {
    const selectItemName = span(placeholder, 'empty selectItemName');
    const selectMenuBox = div('selectBtn', selectItemName);

    const optionsSignal = new Signal(div());
    const searchInput = input(null, 'Search', null, {type: 'search', oninput: onSearch});
    const contentWindow = div('content',
        div('searchBar', searchInput),
        optionsSignal,
    );

    // Select menu body
    const selectMenu = div('selectMenu noSelect', {id: id, value: ''},
        selectMenuBox,
        contentWindow,
    );

    selectMenuBox.onclick = () => {
        window.addEventListener('mouseup', checkClickOutsideSelectMenu);
        contentWindow.classList.toggle('open');
        searchInput.focus();
    };

    function closeSelectMenu() {
        contentWindow.classList.remove('open');
        window.removeEventListener('mouseup', checkClickOutsideSelectMenu);
    }

    function checkClickOutsideSelectMenu(e) {
        let target = e.target;
        // console.log(e);
        while (target !== document.body) {
            // return of find select menu, don't need to close
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
        selectItemName.classList.remove('empty');
        selectItemName.textContent = optionElement.textContent;
        selectMenu.value = optionElement.optionValue;
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
    function createOptionsGroupElement(parent, options) {
        const base = ul('group');
        parent.appendChild(span(options[0], 'groupTitle', {onclick: expandGroupToggle}));
        parent.appendChild(base);
        for (const option of options[1]) {
            // Is Group
            if (option[1] instanceof Array) {
                createOptionsGroupElement(base, /**@type{[string, Array]}*/option);
            }
            // Option
            else {
                base.appendChild(li('option', text(option[1]), {optionValue: option[0], onclick: onOptionClick}));
            }
        }
    }

    /**
     * @param {Option[]} options
     */
    selectMenu.setOptions = function (options) {
        const base = ul('options');
        for (const option of options) {
            // Create group
            if (option[1] instanceof Array) {
                createOptionsGroupElement(base, option);
            }
            // Create option
            else {
                base.appendChild(li('option', text(option[1]), {optionValue: option[0], onclick: onOptionClick}));
            }
        }
        optionsSignal.set(base);
    };

    selectMenu.setValue = function (value) {
        for (const optionElement of optionsSignal.state.getElementsByTagName('li')) {
            if(optionElement.optionValue === value) {
                setOptionSelect(optionElement);
                break;
            }
        }
    };

    return selectMenu;
};