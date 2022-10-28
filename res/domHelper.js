'use strict';
let debug = null;

function addOption(element, options) {
    for (let i = 0; i < options.length; i++) {
        const option = options[i];
        if (option instanceof Array) {
            addOption(element, option);
            continue;
        }
        if (option instanceof Element || option instanceof Text)
            element.appendChild(option);
        else if (option instanceof StateChanger)
            option.init(element);
        else if (option instanceof Signal)
            new StateChanger(option).init(element);
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
 * @param {string | boolean | number} [initState] Init data
 * */
function Signal(initState) {
    const thisListener = [];
    this.state = initState !== undefined ? initState : null;

    this.addListener = function (listener) {
        thisListener.push(listener);
    };

    this.removeListener = function (listener) {
        const index = thisListener.indexOf(listener);
        if (index !== -1) thisListener.splice(index, 1);
    };

    this.set = function (newState) {
        if (this.state === newState) return;
        this.state = newState;
        for (let i = 0; i < thisListener.length; i++)
            thisListener[i](newState);
    };

    this.update = function () {
        for (let i = 0; i < thisListener.length; i++)
            thisListener[i](this.state);
    }
}

/**
 * @param {Signal} signal
 * @param {function(state)} renderState
 * */
function State(signal, renderState) {
    if (signal === null || signal === undefined) return document.createElement('div');
    return new StateChanger(signal, renderState);
}

function StateChanger(signal, renderState) {
    let lastElement;
    let thisParent;
    this.init = function (parent) {
        thisParent = parent;
        parent.appendChild(lastElement = renderState ? renderState(signal.state) : signal.state ? signal.state : document.createElement('div'));
        signal.addListener(onStateChange);
    };

    function onStateChange(state) {
        // if (debug !== null) debug.trace('[Debug] State change', this);
        const newElement = renderState ? renderState(state) : state;
        thisParent.replaceChild(newElement, lastElement);
        lastElement = newElement;
    }
}

/**
 * @param {Signal} signal
 * @param {function(state)} [toString]
 * */
function TextState(signal, toString) {
    return new TextStateChanger(signal, toString);
}

function TextStateChanger(signal, toString) {
    let element = null;
    signal.addListener(updateText);

    this.init = function (newElement) {
        element = newElement;
        return toString ? toString(signal.state) : signal.state;
    };

    function updateText(state) {
        element.textContent = toString ? toString(state) : state;
    }
}

function parseTextInput(text, element) {
    if (text instanceof Signal)
        element.textContent = new TextStateChanger(text).init(element);
    else if (text instanceof TextStateChanger)
        element.textContent = text.init(element);
    else
        element.textContent = text;
}

/**
 * @param {string} className
 * */
function ClassList(...className) {
    const classList = className;

    this.init = function (element) {
        if (element.classList) {
            if (classList.length > 0)
                element.classList.add(...classList);
            this.add = names => element.classList.add(names);
            this.remove = names => element.classList.remove(names);
            this.toggle = name => element.classList.toggle(name);
            this.contains = name => element.classList.contains(name);
        } else {
            this.add = function (...className) {
                Array.prototype.push.apply(classList, className);
                element.className = classList.join(' ');
            };

            this.remove = function (...className) {
                for (let i = 0; i < className.length; i++) {
                    const index = classList.indexOf(className[i]);
                    if (index !== -1)
                        classList.splice(index, 1);
                }
                element.className = classList.join(' ');
            };

            this.toggle = function (className) {
                const index = classList.indexOf(className);
                let toggle;
                if (index !== -1) {
                    classList.splice(index, 1);
                    toggle = false;
                } else {
                    classList.push(className);
                    toggle = true;
                }
                element.className = classList.join(' ');
                return toggle;
            };

            this.contains = function (className) {
                return classList.indexOf(className) !== -1;
            };

            element.className = classList.join(' ');
        }
    };
}

function parseClassInput(className, element) {
    if (className instanceof ClassList)
        className.init(element);
    else
        element.className = className;
}

/**
 * @param {string} defaultPage
 * @param {Object<string, function()|HTMLElement>} Routs
 * */
function QueryRouter(defaultPage, Routs) {
    const routerRoot = document.createElement('div');
    routerRoot.className = 'router';
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
    })(location.search.length < 2 ? defaultPage : location.search.slice(1));

    routerRoot.getRoutesName = function () {
        return Object.keys(Routs);
    };

    return routerRoot;
}

/**
 * @param {Signal} signal
 * @param {HTMLElement | function} element
 * */
function ShowIf(signal, element) {
    const emptyDiv = document.createElement('div');
    let showState = signal.state;
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
    };
}

module.exports = {
    Signal,
    ShowIf,
    State,
    TextState,
    ClassList,
    QueryRouter,
    /**
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLDivElement}
     * */
    div(classN, ...options) {
        const element = document.createElement('div');
        if (classN) parseClassInput(classN, element);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLElement}
     * */
    nav(classN, ...options) {
        const element = document.createElement('nav');
        if (classN) parseClassInput(classN, element);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLUListElement}
     * */
    ul(classN, ...options) {
        const element = document.createElement('ul');
        if (classN) parseClassInput(classN, element);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLLIElement}
     * */
    li(classN, ...options) {
        const element = document.createElement('li');
        if (classN) parseClassInput(classN, element);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param {string} [placeholder]
     * @param {string} [id]
     * @param [options] Options for element
     * @return {HTMLInputElement}
     * */
    input(classN, placeholder, id, ...options) {
        const element = document.createElement('input');
        if (classN) parseClassInput(classN, element);
        if (placeholder) element.placeholder = placeholder;
        if (id) element.id = id;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param {string | Signal | TextState | TextStateChanger} text
     * @param {string} forId
     * @param [options] Options for element
     * @return {HTMLLabelElement}
     * */
    label(classN, text, forId, ...options) {
        const element = document.createElement('label');
        if (classN) parseClassInput(classN, element);
        if (text) parseTextInput(text, element);
        if (forId) element.htmlFor = forId;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param {string | Signal | TextState | TextStateChanger} [text]
     * @param {function(MouseEvent)} [onClick]
     * @param [options] Options for element
     * @return {HTMLButtonElement}
     * */
    button(classN, text, onClick, ...options) {
        const element = document.createElement('button');
        if (classN) parseClassInput(classN, element);
        if (text) parseTextInput(text, element);
        if (onClick) element.onclick = onClick;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLTableElement}
     * */
    table(classN, ...options) {
        const element = document.createElement('table');
        if (classN) parseClassInput(classN, element);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLTableColElement}
     * */
    colgroup(classN, ...options) {
        const element = document.createElement('colgroup');
        if (classN) parseClassInput(classN, element);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLTableColElement}
     * */
    col(classN, ...options) {
        const element = document.createElement('col');
        if (classN) parseClassInput(classN, element);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLTableSectionElement}
     * */
    thead(classN, ...options) {
        const element = document.createElement('thead');
        if (classN) parseClassInput(classN, element);
        if (options.length) addOption(element, options);
        return element;
    },

    tr(classN, ...options) {
        const element = document.createElement('tr');
        if (classN) parseClassInput(classN, element);
        if (options.length) addOption(element, options);
        return element;
    },

    th(text, classN, ...options) {
        const element = document.createElement('th');
        if (classN) parseClassInput(classN, element);
        if (text) element.textContent = text;
        if (options.length) addOption(element, options);
        return element;
    },

    td(text, classN, ...options) {
        const element = document.createElement('td');
        if (classN) parseClassInput(classN, element);
        if (text) element.textContent = text;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLTableSectionElement}
     * */
    tbody(classN, ...options) {
        const element = document.createElement('tbody');
        if (classN) parseClassInput(classN, element);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string} url Url
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLIFrameElement}
     * */
    iframe(url, classN, ...options) {
        const element = document.createElement('iframe');
        if (classN) parseClassInput(classN, element);
        if (url) element.src = url;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | Signal | TextState | TextStateChanger} text
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLParagraphElement}
     * */
    p(text, classN, ...options) {
        const element = document.createElement('p');
        if (classN) parseClassInput(classN, element);
        if (text) parseTextInput(text, element);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | Signal | TextState | TextStateChanger} text
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLSpanElement}
     * */
    span(text, classN, ...options) {
        const element = document.createElement('span');
        if (classN) parseClassInput(classN, element);
        if (text) parseTextInput(text, element);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | Signal | TextState | TextStateChanger} text
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLHeadingElement}
     * */
    h1(text, classN, ...options) {
        const element = document.createElement('h1');
        if (classN) parseClassInput(classN, element);
        if (text) parseTextInput(text, element);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | Signal | TextState | TextStateChanger} text
     * @param {string} [href]
     * @param {string | ClassList} [classN] Class Name
     * @param {function(MouseEvent)} [onClick]
     * @param [options] Options for element
     * @return {HTMLAnchorElement}
     * */
    a(text, href, classN, onClick, ...options) {
        const element = document.createElement('a');
        if (classN) parseClassInput(classN, element);
        if (href) element.href = href;
        if (text) parseTextInput(text, element);
        if (onClick) element.onclick = onClick;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {String} text
     * @return {Text}
     */
    text(text) {
        return document.createTextNode(text);
    },

    /**
     * @return {HTMLBRElement}
     */
    br() {
        return document.createElement('br')
    },

    /**
     * @param {string} url
     * @param {string | ClassList} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLImageElement}
     * */
    img(url, classN, ...options) {
        const element = document.createElement('img');
        if (classN) parseClassInput(classN, element);
        if (url) element.src = url;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string} svgText
     * @param {string} viewBox
     * @param {string} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLElement}
     * */
    svg(svgText, viewBox, classN, ...options) {
        const element = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        element.innerHTML = svgText;
        if (classN) element.setAttributeNS(null, 'class', classN);
        if (classN) element.setAttributeNS(null, 'viewBox', viewBox);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string} tagN
     * @param {string} [classN] Class Name
     * @param [options] Options for element
     * @return {HTMLElement}
     * */
    any(tagN, classN, ...options) {
        const element = document.createElement(tagN);
        if (classN) parseClassInput(classN, element);
        if (options.length) addOption(element, options);
        return element;
    },

    debug() {
        debug = console;
    }
};