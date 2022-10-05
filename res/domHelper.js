'use strict';

function addOption(element, options) {
    for (let i = 0; i < options.length; i++)
        if (options[i] instanceof Element)
            element.appendChild(options[i]);
        else if (options[i] instanceof Function) {
            const child = options[i](element);
            if (child) element.appendChild(child);
        } else
            element[options[i][0]] = options[i][1];
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
    const isBool = typeof init === 'boolean';
    thisData = init;

    this.__setElement = function (element) {
        thisElement = element;
        return isBool ? state[thisData ? 1 : 0] : thisData;
    }

    this.__addListener = function (listener) {
        if (!thisListener) thisListener = [listener];
        else thisListener.push(listener);
    }

    this.set = function (data) {
        thisData = data;
        if (thisElement)
            thisElement.textContent = isBool ? state[thisData ? 1 : 0] : thisData;
        if (thisListener)
            for (const listener of thisListener)
                listener(thisData);
    }

    this.get = function () {
        return thisData;
    }
}

/**
 * @param signal
 * @param element {HTMLElement}
 * */
function ShowIf(signal, element) {
    let showState = signal.get();
    let parent;
    signal.__addListener(function (show) {
        if (showState !== show) {
            showState = show;
            if (show) {
                if (element.onRender)
                    element.onRender();
                parent.appendChild(element);
            }
            else parent.removeChild(element);
        }
    });
    return function (parentElement) {
        parent = parentElement;
        return showState ? element : null;
    }
}

module.exports = {
    Signal,
    ShowIf,
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
     * @param placeholder {string}
     * @param [options] Option for element
     * @return HTMLInputElement
     * */
    input(classN, placeholder, ...options) {
        const element = document.createElement('input');
        if (classN) element.className = classN;
        if (placeholder) element.placeholder = placeholder;
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
        if (onClick) element.addEventListener('click', onClick);
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
        const element = new DOMParser().parseFromString(fetchSync(url).body, 'image/svg+xml').documentElement;
        if (classN) element.classList.add(classN)
        if (options.length) addOption(element, options);
        return element;
    }
};