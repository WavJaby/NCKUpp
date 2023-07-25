/*ExcludeStart*/
const module = {};
const {checkboxWithName, div, button, table, Signal, text, span, ShowIf, checkbox, label, linkStylesheet, th, h1} = require('../domHelper');
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

        } else {
            if (state)
                window.askForLoginAlert();
        }
    }

    const items = [text('123'), text('123456'), text('123456789')];
    const adjustItemHolder = div('adjustItemHolder');
    const adjustListBody = div('body',
        items.map((i, index) =>
            div('itemHolder', div('item noSelect', {onmousedown: onGrabItem, ontouchstart: onGrabItem}, i))
        )
    );
    const adjustList = div('adjustList',
        h1('Test'),
        adjustItemHolder,
        adjustListBody
    );
    /**@type{HTMLDivElement}*/
    let movingItem = null, grabbingItem = null;
    let startGrabbingOffsetX, startGrabbingOffsetY;

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
        startGrabbingOffsetY = this.offsetTop - pointerY + adjustListBody.offsetTop;

        const grabbingItemHolder = this.parentElement;

        // Clone item to move
        movingItem = this.cloneNode(true);
        if (adjustListBody.getBoundingClientRect) {
            const bound = grabbingItemHolder.getBoundingClientRect();
            movingItem.style.width = bound.width + 'px';
            movingItem.style.height = bound.height + 'px';
            console.log(movingItem.style.width)
        } else {
            movingItem.style.width = grabbingItemHolder.offsetWidth + 'px';
            movingItem.style.height = grabbingItemHolder.offsetHeight + 'px';
        }
        movingItem.style.left = (this.offsetLeft + adjustListBody.offsetLeft) + 'px';
        movingItem.style.top = (this.offsetTop + adjustListBody.offsetTop) + 'px';
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
                requestAnimationFrame(() => {
                    switchGrabbingItem.style.top = nextPosition + 'px';
                });
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
        movingItemFinal.style.top = (grabbingItem.parentElement.offsetTop + adjustListBody.offsetTop) + 'px';
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
        }, 100);
    }

    return div('preferenceAdjust',
        {onRender, onPageOpen, onPageClose},
        adjustList
    );
};