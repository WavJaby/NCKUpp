/*ExcludeStart*/
const module = {};
const {div, span, linkStylesheet, h1} = require('../domHelper');
/*ExcludeEnd*/

module.exports = function (loginState) {
    console.log('Preference adjust Init');
    // static element
    const styles = linkStylesheet('./res/pages/preferenceAdjust.css');

    function onRender() {
        console.log('Course schedule Render');
        styles.mount();
    }

    function onPageOpen() {
        console.log('Course schedule Open');
        // close navLinks when using mobile devices
        window.navMenu.remove('open');
        styles.enable();
        loginState.addListener(onLoginState);
        onLoginState(loginState.state);
        window.addEventListener('mousemove', onMouseMove);
        window.addEventListener('touchmove', onMouseMove);
        window.addEventListener('mouseup', onMouseUp);
        window.addEventListener('touchend', onMouseUp);
        window.addEventListener('touchcancel', onMouseUp);
    }

    function onPageClose() {
        console.log('Course schedule Close');
        styles.disable();
        loginState.removeListener(onLoginState);
        window.removeEventListener('mousemove', onMouseMove);
        window.removeEventListener('touchmove', onMouseMove);
        window.removeEventListener('mouseup', onMouseUp);
        window.removeEventListener('touchend', onMouseUp);
        window.removeEventListener('touchcancel', onMouseUp);
    }

    /**
     * @param {LoginData} state
     */
    function onLoginState(state) {
        if (state && state.login) {
            window.fetchApi('/preferenceAdjust', 'Get preference').then(i => {
                if (i.success)
                    renderAdjustList(i.data);
            });
        } else {
            if (state)
                window.askForLoginAlert();
            renderAdjustList(null);
        }
    }


    const adjustItemTitle = h1(null, 'title');
    const adjustItemHolder = div('adjustItemHolder');
    /**@type{HTMLDivElement}*/
    const adjustListBody = div('body');
    const adjustList = div('adjustList',
        adjustItemTitle,
        adjustItemHolder,
        adjustListBody,
    );
    /**@type{HTMLDivElement}*/
    let movingItem = null, grabbingItem = null;
    let startGrabbingOffsetX, startGrabbingOffsetY;

    function renderAdjustList(state) {
        if (!state) {
            adjustItemTitle.textContent = '';
            adjustListClearItems();
            return;
        }

        console.log(state)

        adjustItemTitle.textContent = state.name;
        const items = state.items.map(i => div(null,
            span('[' + i.sn + '] '),
            span(i.name + ' '),
            span(toTimeStr(i.time) + ' '),
            span(i.credits + '學分 '),
            span(i.require ? '必修' : '選修'),
        ));
        adjustListUpdateItems(items);
    }

    const dayOfWeek = ['星期一', '星期-二', '星期三', '星期四', '星期五', '星期六', '星期日'];

    function toTimeStr(time) {
        if (time === null)
            return '未定';
        time = time.split(',');
        return '(' + dayOfWeek[time[0]] + ')' + (time.length === 2 ? time[1] : time[1] + '~' + time[2]);
    }

    function adjustListUpdateItems(items) {
        adjustListClearItems();
        for (const item of items) {
            adjustListBody.appendChild(
                div('itemHolder', div('item noSelect', {onmousedown: onGrabItem, ontouchstart: onGrabItem}, item))
            )
        }
    }

    function adjustListClearItems() {
        while (adjustListBody.firstChild)
            adjustListBody.removeChild(adjustListBody.firstChild);
    }

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

    return div('preferenceAdjust',
        {onRender, onPageOpen, onPageClose},
        adjustList
    );
};