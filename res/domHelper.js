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
        else if (option instanceof StateChanger || option instanceof ShowIfStateChanger)
            option.init(element);
        else if (option instanceof Signal)
            new StateChanger(option).init(element);
        else
            Object.assign(element, option);
    }
}

/**
 * @param {string | boolean | number | object} [initState] Init state data
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

    /**
     * @param {any} newState
     * @param {boolean} [forceUpdate]
     */
    this.set = function (newState, forceUpdate) {
        if (this.state === newState && !forceUpdate) return;
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
 * @param {function(state: any): Element} [renderState]
 * */
function State(signal, renderState) {
    if (signal === null || signal === undefined) throw new TypeError('State signal not given');
    return new StateChanger(signal, renderState);
}

/**
 * @constructor
 * @param {Signal} signal
 * @param {function(state: any): Element} [renderState]
 * */
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
 * @param {function(state: any): string} [toString]
 * */
function TextState(signal, toString) {
    if (signal === null || signal === undefined) throw new TypeError('State signal not given');
    return new TextStateChanger(signal, toString);
}

/**
 * @constructor
 * @param {Signal} signal
 * @param {function(state: any): string} [toString]
 * */
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

/**
 * @param {string | Signal | TextStateChanger} text
 * @param element
 */
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
    this.add = this.remove = this.toggle = this.contains = function () {
    }

    this.init = function (element) {
        if (element.classList) {
            for (let i = 0; i < classList.length; i++) {
                element.classList.add(classList[i]);
            }
            this.add = function (names) {
                element.classList.add(names);
            };
            this.remove = function (names) {
                element.classList.remove(names);
            };
            this.toggle = function (name) {
                return element.classList.toggle(name);
            };
            this.contains = function (name) {
                element.classList.contains(name);
            };
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

window.urlHashData = {
    data: {},
    update: function () {
        return this.data = (
            window.location.hash.length !== 0 ? JSON.parse(decodeURIComponent(atob(window.location.hash.slice(1)))) : {}
        );
    },
    get: function (key) {
        return this.data[key];
    },
    set: function (key, value) {
        this.data[key] = value;

        const newUrl = new URL(window.location);
        newUrl.hash = btoa(encodeURIComponent(JSON.stringify(this.data)));
        history.pushState({}, document.title, newUrl);
    },
    contains: function (key) {
        return this.data[key] !== undefined;
    }
}
window.urlHashData.update();

/**
 * @param {string} defaultPage
 * @param {Object<string, function()|HTMLElement>} routs
 * @param {HTMLElement} [footer]
 * */
function HashRouter(defaultPage, routs, footer) {
    const routerRoot = document.createElement('div');
    routerRoot.className = 'router';
    const loadPageCache = {};
    let lastPage, lastPageId = null;

    window.addEventListener('popstate', function () {
        const state = window.urlHashData.update();
        window.urlHashData.data = state;
        routerRoot.openPage(state.page);
    });

    routerRoot.openPage = function (pageId) {
        // if same page
        if (lastPageId === pageId) {
            // Get page
            const page = getPage(pageId);
            if (page.onPageOpen) page.onPageOpen();
            return;
        }
        lastPageId = pageId;
        window.urlHashData.set('page', pageId);

        // Get page
        const page = getPage(pageId);

        // Switch page element
        if (lastPage) {
            if (lastPage.onPageClose) lastPage.onPageClose();
            routerRoot.replaceChild(page, lastPage);
        }
        // append page element on first open
        else {
            routerRoot.appendChild(page);
            if (footer)
                routerRoot.appendChild(footer);
        }
        // Render page if not render yet
        if (!page.render) {
            page.render = true;
            page.onRender();
        }
        if (page.onPageOpen) page.onPageOpen();
        lastPage = page;
    }

    function getPage(pageId) {
        const loadedPage = loadPageCache[pageId];
        if (loadedPage)
            return loadedPage;

        // Load page
        const page = routs[pageId];
        if (!page) return null;

        // lazy
        if (page instanceof Function)
            return loadPageCache[pageId] = page();
        return loadPageCache[pageId] = page;
    }

    routerRoot.getRoutesName = function () {
        return Object.keys(routs);
    };

    // open default page
    routerRoot.init = function () {
        routerRoot.openPage(window.urlHashData.get('page') || defaultPage);
    }

    // // append footer
    // if (footer) {
    //     routerRoot.addEventListener('scroll', function () {
    //         const position = (routerRoot.scrollTop - (footer.offsetTop - routerRoot.offsetHeight));
    //         console.log(position)
    //
    //         if (position > 0) {
    //             // footer.style.marginTop = (lastState.offsetHeight + footer.offsetHeight) + 'px';
    //             lastState.style.bottom = footer.offsetHeight + 'px';
    //             // lastState.style.position = 'absolute';
    //             // lastState.style.bottom = -position + 'px';
    //         }else {
    //             // footer.style.marginTop = null;
    //             lastState.style.bottom = null;
    //             // lastState.style.position = null;
    //             // lastState.style.bottom = null;
    //         }
    //     });
    //     routerRoot.appendChild(footer);
    // }
    return routerRoot;
}

/**
 * @param {Signal} signal
 * @param {HTMLElement} element
 * */
function ShowIf(signal, element) {
    if (signal === null || signal === undefined) throw new TypeError('State signal not given');
    return new ShowIfStateChanger(signal, element);
}

/**
 * @constructor
 * @param {Signal} signal
 * @param {HTMLElement} element
 * */
function ShowIfStateChanger(signal, element) {
    const emptyDiv = document.createElement('div');
    let showState = signal.state;
    let parent;
    signal.addListener(function (show) {
        if (showState !== show) {
            showState = show;
            if (show) {
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
    this.init = function (parentElement) {
        parent = parentElement;
        if (showState) {
            if (element.onRender)
                element.onRender();
        }
        parentElement.appendChild(showState ? element : emptyDiv);
    };
}

module.exports = {
    Signal,
    ShowIf,
    State,
    TextState,
    ClassList,
    HashRouter,
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
     * @param {function(this: GlobalEventHandlers, ev: Event)} [onchange]
     * @param {string} [id]
     * @param [options] Options for element
     * @return {HTMLInputElement}
     * */
    checkbox(classN, onchange, id, ...options) {
        const element = document.createElement('input');
        element.type = 'checkbox';
        if (classN) parseClassInput(classN, element);
        element.onchange = onchange;
        if (id) element.id = id;
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param {string | Signal | TextStateChanger} text
     * @param {HTMLInputElement} inputElement
     * @param [options] Options for element
     * @return {HTMLLabelElement}
     * */
    label(classN, text, inputElement, ...options) {
        const element = document.createElement('label');
        if (classN) parseClassInput(classN, element);
        if (text) parseTextInput(text, element);
        if (inputElement) {
            if (!inputElement.id)
                inputElement.id = Math.random().toString(16).substring(2);
            element.htmlFor = inputElement.id;
        }
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param {string | ClassList} [classN] Class Name
     * @param {string | Signal | TextStateChanger} [text]
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
     * @param {string | Signal | TextStateChanger} text
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
     * @param {string | Signal | TextStateChanger} [text]
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
     * @param {string | Signal | TextStateChanger} text
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
     * @param {string | Signal | TextStateChanger} text
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
     * @param {any} text
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
     * @param {string} [viewBox]
     * @param {string} [classN] Class Name
     * @param [options] Options for element
     * @return {SVGSVGElement}
     * */
    svg(svgText, viewBox, classN, ...options) {
        const element = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        element.innerHTML = svgText;
        if (classN) element.setAttributeNS(null, 'class', classN);
        if (viewBox) element.setAttributeNS(null, 'viewBox', viewBox);
        if (options.length) addOption(element, options);
        return element;
    },

    /**
     * @param [options] Options for element
     * @return {HTMLElement}
     * */
    footer(...options) {
        const element = document.createElement('footer');
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