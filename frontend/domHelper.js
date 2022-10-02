/**
 * @param classN {string|null} Class Name
 * @param options Option for element
 * @return HTMLElement
 * */
function div(classN, ...options) {
    const element = document.createElement('div');
    if (classN) element.className = classN;
    if (options.length) addOption(element, options);
    return element;
}

/**
 * @param classN {string|null} Class Name
 * @param options Option for element
 * @return HTMLElement
 * */
function nav(classN, ...options) {
    const element = document.createElement('nav');
    if (classN) element.className = classN;
    if (options.length) addOption(element, options);
    return element;
}

/**
 * @param classN {string|null} Class Name
 * @param placeholder {string}
 * @param options Option for element
 * @return HTMLInputElement
 * */
function input(classN, placeholder, ...options) {
    const element = document.createElement('input');
    if (classN) element.className = classN;
    if (placeholder) element.placeholder = placeholder;
    if (options.length) addOption(element, options);
    return element;
}

/**
 * @param classN {string|null} Class Name
 * @param text {string}
 * @param onClick {function|null}
 * @param options Option for element
 * @return HTMLButtonElement
 * */
function button(classN, text, onClick, ...options) {
    const element = document.createElement('button');
    if (classN) element.className = classN;
    if (text) element.textContent = text;
    if (onClick) element.addEventListener('click', onClick);
    if (options.length) addOption(element, options);
    return element;
}

/**
 * @param classN {string|null} Class Name
 * @param options Option for element
 * @return HTMLTableElement
 * */
function table(classN, ...options) {
    const element = document.createElement('table');
    if (classN) element.className = classN;
    if (options.length) addOption(element, options);
    return element;
}

/**
 * @param text {string}
 * @param classN {string|null} Class Name
 * @param options Option for element
 * @return HTMLElement
 * */
function p(text, classN, ...options) {
    const element = document.createElement('p');
    if (classN) element.className = classN;
    if (text) element.textContent = text;
    if (options.length) addOption(element, options);
    return element;
}

/**
 * @param text {string}
 * @param classN {string|null} Class Name
 * @param options Option for element
 * @return HTMLElement
 * */
function h1(text, classN, ...options) {
    const element = document.createElement('h1');
    if (classN) element.className = classN;
    if (text) element.textContent = text;
    if (options.length) addOption(element, options);
    return element;
}

/**
 * @param url {string}
 * @param classN {string|null} Class Name
 * @param options Option for element
 * @return HTMLElement
 * */
function img(url, classN, ...options) {
    const element = document.createElement('img');
    if (classN) element.className = classN;
    if (url) element.src = url;
    if (options.length) addOption(element, options);
    return element;
}

/**
 * @param url {string}
 * @param classN {string|null} Class Name
 * @param options Option for element
 * @return HTMLElement
 * */
function svg(url, classN, ...options) {
    const parser = new DOMParser();
    const element = parser.parseFromString(getText(url), 'image/svg+xml').documentElement;
    if (classN) element.classList.add(classN)
    if (options.length) addOption(element, options);
    return element;
}

function addOption(element, options) {
    for (let i = 0; i < options.length; i++)
        if (options[i] instanceof Element)
            element.appendChild(options[i]);
        else
            element[options[i][0]] = options[i][1];
}