'use strict';

function addOption(element, options) {
    for (let i = 0; i < options.length; i++) {
        const option = options[i];
        if (option instanceof Element || option instanceof Text)
            element.appendChild(option);
        // Show if
        else if (option instanceof Function)
            element.appendChild(option(element));
        else if (option instanceof Array)
            element[option[0]] = option[1];
        else
            Object.assign(element, option);
    }
}

/**
 * @param [init] {string | boolean} Init data
 * @param [state] {any[]} State
 * @return Signal
 * */
function Signal(init, state) {
    let thisListener;
    let thisData;
    let thisElement;
    const hasState = state !== null && state !== undefined;
    const isBool = hasState && typeof init === 'boolean';
    thisData = init;

    this.__setElement = function (element) {
        thisElement = element;
        return isBool ? state[thisData ? 1 : 0] : hasState ? state[thisData] : thisData;
    }

    this.addListener = function (listener) {
        if (!thisListener) thisListener = [listener];
        else thisListener.push(listener);
    }

    this.removeListener = function (listener) {
        const index = thisListener.indexOf(listener);
        if (index !== -1) thisListener.splice(index, 1);
    }

    this.set = function (data) {
        if (data === thisData) return;

        thisData = data;
        if (thisElement)
            thisElement.textContent = isBool ? state[thisData ? 1 : 0] : hasState ? state[thisData] : thisData;
        if (thisListener)
            for (const listener of thisListener)
                listener(thisData);
    }

    this.get = function () {
        return thisData;
    }

    this.getState = function () {
        return isBool ? state[thisData ? 1 : 0] : hasState ? state[thisData] : thisData
    }
}

/**
 * @param defaultPage
 * @param Routs
 * */
function QueryRouter(defaultPage, Routs) {
    const routerRoot = div(null);
    let lastState, lastPage;
    (routerRoot.openPage = function (newPage) {
        if (lastPage === newPage) return;
        lastPage = newPage;

        history.pushState(null, document.title, './?' + newPage)
        let state = Routs[newPage];
        if (!state) return;
        if (state instanceof Function)
            Routs[newPage] = state = state();
        if (lastState) {
            if (lastState.onDestroy) lastState.onDestroy();
            routerRoot.replaceChild(state, lastState);
            if (state.onRender) state.onRender();
        } else {
            routerRoot.appendChild(state);
            if (state.onRender) state.onRender();
        }
        lastState = state;
    })(location.search.length < 2 ? defaultPage : location.search.slice(1))

    routerRoot.getRoutesName = function () {
        return Object.keys(Routs);
    }

    return routerRoot;
}

/**
 * @param signal
 * @param element {HTMLElement|function}
 * */
function ShowIf(signal, element) {
    const emptyDiv = document.createElement('div');
    let showState = signal.get();
    let parent;
    signal.addListener(function (show) {
        if (showState !== show) {
            showState = show;
            if (show) {
                if (element instanceof Function)
                    element = element();
                parent.replaceChild(element, emptyDiv);
                if (element.onRender)
                    element.onRender();
            } else {
                if (element.onDestroy)
                    element.onDestroy();
                parent.replaceChild(emptyDiv, element);
            }
        }
    });
    return function (parentElement) {
        parent = parentElement;
        if (showState) {
            if (element instanceof Function)
                element = element();
            if (element.onRender)
                element.onRender();
        }
        return showState ? element : emptyDiv;
    }
}

module.exports = {
    Signal,
    ShowIf,
    QueryRouter,
    /**
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLDivElement
     * */
    div(classN, ...options) {
        const element = document.createElement('div');
        if (classN) element.className = classN;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLElement
     * */
    nav(classN, ...options) {
        const element = document.createElement('nav');
        if (classN) element.className = classN;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLUListElement
     * */
    ul(classN, ...options) {
        const element = document.createElement('ul');
        if (classN) element.className = classN;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLLIElement
     * */
    li(classN, ...options) {
        const element = document.createElement('li');
        if (classN) element.className = classN;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param [classN] {string} Class Name
     * @param placeholder {string}
     * @param [id] {string}
     * @param [options] Option for element
     * @return HTMLInputElement
     * */
    input(classN, placeholder, id, ...options) {
        const element = document.createElement('input');
        if (classN) element.className = classN;
        if (placeholder) element.placeholder = placeholder;
        if (id) element.id = id;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param [classN] {string} Class Name
     * @param text {string}
     * @param forId {string}
     * @param [options] Option for element
     * @return HTMLLabelElement
     * */
    label(classN, text, forId, ...options) {
        const element = document.createElement('label');
        if (classN) element.className = classN;
        if (text) element.textContent = text;
        if (forId) element.htmlFor = forId;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param [classN] {string} Class Name
     * @param [text] {string|Signal}
     * @param [onClick] {function|null}
     * @param [options] Option for element
     * @return HTMLButtonElement
     * */
    button(classN, text, onClick, ...options) {
        const element = document.createElement('button');
        if (classN) element.className = classN;
        if (text) element.textContent = (text instanceof Signal) ? text.__setElement(element) : text;
        if (onClick) element.onclick = onClick;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLTableElement
     * */
    table(classN, ...options) {
        const element = document.createElement('table');
        if (classN) element.className = classN;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLTableSectionElement
     * */
    thead(classN, ...options) {
        const element = document.createElement('thead');
        if (classN) element.className = classN;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLTableSectionElement
     * */
    tbody(classN, ...options) {
        const element = document.createElement('tbody');
        if (classN) element.className = classN;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param url {string} Url
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLIFrameElement
     * */
    iframe(url, classN, ...options) {
        const element = document.createElement('iframe');
        if (classN) element.className = classN;
        if (url) element.src = url;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param text {string|Signal}
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLParagraphElement
     * */
    p(text, classN, ...options) {
        const element = document.createElement('p');
        if (classN) element.className = classN;
        if (text) element.textContent = (text instanceof Signal) ? text.__setElement(element) : text;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param text {string|Signal}
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLSpanElement
     * */
    span(text, classN, ...options) {
        const element = document.createElement('span');
        if (classN) element.className = classN;
        if (text) element.textContent = (text instanceof Signal) ? text.__setElement(element) : text;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param text {string|Signal}
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLHeadingElement
     * */
    h1(text, classN, ...options) {
        const element = document.createElement('h1');
        if (classN) element.className = classN;
        if (text) element.textContent = (text instanceof Signal) ? text.__setElement(element) : text;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param [href] {string}
     * @param [text] {string|Signal}
     * @param [classN] {string} Class Name
     * @param [onClick] {function|null}
     * @param [options] Option for element
     * @return HTMLAnchorElement
     * */
    a(href, text, classN, onClick, ...options) {
        const element = document.createElement('a');
        if (classN) element.className = classN;
        if (href) element.href = href;
        if (text) element.textContent = (text instanceof Signal) ? text.__setElement(element) : text;
        if (onClick) element.onclick = onClick;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param text {string}
     * @return Text
     * */
    text(text) {
        return document.createTextNode(text);
    },

    /**
     * @param url {string}
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLImageElement
     * */
    img(url, classN, ...options) {
        const element = document.createElement('img');
        if (classN) element.className = classN;
        if (url) element.src = url;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param url {string}
     * @param [classN] {string} Class Name
     * @param [options] Option for element
     * @return HTMLElement
     * */
    svg(url, classN, ...options) {
        // const element = new DOMParser().parseFromString(fetchSync(url).body, 'image/svg+xml').documentElement;
        const element = document.createElement('svg');
        if (classN) element.classList.add(classN)
        if (options.length) addOption(element, options);
        return element;
    }
};