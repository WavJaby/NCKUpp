'use strict';
// noinspection JSUnusedLocalSymbols
const {
    debug: doomDebug,
    Signal,
    ShowIf,
    HashRouter,
    div,
    nav,
    ul,
    li,
    input,
    checkbox,
    button,
    table,
    thead,
    tbody,
    colgroup,
    col,
    tr,
    th,
    td,
    iframe,
    p,
    span,
    h1,
    a,
    br,
    text,
    img,
    svg,
    label,
    TextState,
    State,
    ClassList,
    any,
    footer,
    linkStylesheet
} = require('./res/domHelper');
// noinspection JSUnusedLocalSymbols
const SelectMenu = require('./res/selectMenu');

const apiEndPoint = location.hostname === 'localhost'
    ? 'https://localhost/api'
    : 'https://api.simon.chummydns.com/api';
const mobileWidth = 700;
window.messageAlert = MessageAlert();
/**
 * @typedef {Object} ApiResponse
 * @property {string} success
 * @property {string[]} err
 * @property {string[]} warn
 * @property {string} msg
 * @property {any} data
 */
/**
 * @typedef {Object} CustomApiFetch
 * @property {int} [timeout] Fetch timeout time millisecond
 *
 * @typedef {RequestInit & CustomApiFetch} ApiFetch
 */
/**
 * @param {string} endpoint
 * @param {ApiFetch} [option]
 * @return Promise<ApiResponse>
 */
window.fetchApi = function (endpoint, option) {
    if (option) option.credentials = 'include';
    else option = {credentials: 'include'};
    let abortTimeout;
    if (window.AbortController && option.timeout) {
        const controller = new AbortController();
        option.signal = controller.signal;
        abortTimeout = setTimeout(() => controller.abort(), option.timeout);
    }
    return fetch(apiEndPoint + endpoint, option)
        .then(i => i.json())
        // Handle self defined error
        .then(i => {
            if (abortTimeout)
                clearTimeout(abortTimeout);
            if (!i.success)
                messageAlert.addError(
                    'Api response error',
                    i.err.join('\n'), 1000);
            return i;
        })
        .catch(e => {
            // Timeout error
            if (e.name === 'AbortError') {
                messageAlert.addError(
                    'Request timeout',
                    'Try again later', 3000);
            }
            // Other error
            else {
                messageAlert.addError(
                    'Api error',
                    e instanceof Error ? e.stack || e || 'Unknown error!' : e, 1000);
            }
        });
};

window.loadingElement = svg('<circle class="path" cx="25" cy="25" r="20" fill="none" stroke-width="5" stroke-linecap="square"/>', '0 0 50 50', 'loaderCircle');
window.navMenu = new ClassList('links');
window.pageLoading = new Signal(false);
/**
 * @typedef {Object} LoginData
 * @property {boolean} login
 * @property {string} name
 * @property {string} studentID
 * @property {int} year
 * @property {int} semester
 */
// Main function
(function main() {
    // debug
    let debugWindow = null;
    if (location.hostname === 'localhost') {
        console.log('Debug enabled');
        doomDebug();

        const memoryUpdate = new Signal(window.performance.memory);
        setInterval(() => memoryUpdate.set(window.performance.memory), 1000);
        debugWindow = span(
            TextState(memoryUpdate, state => (state.usedJSHeapSize / 1000 / 1000).toFixed(2) + 'MB'),
            null,
            {style: 'position: absolute; top: 0; z-index: 100; background: black; font-size: 10px; opacity: 0.5;'})
    }

    // main code
    const userLoginData = new Signal();
    const showLoginWindow = new Signal(false);
    const hashRouter = HashRouter('search',
        {
            search: () => require('./res/pages/courseSearch')(userLoginData),
            schedule: () => require('./res/pages/courseSchedule')(userLoginData),
            grades: () => require('./res/pages/stuIdSysGrades')(userLoginData),
        },
        footer(
            div('borderLine'),
            span('Author: WavJaby', 'author'),
            a(null, 'https://github.com/WavJaby/NCKUpp', 'openRepo noSelect', null,
                {target: '_blank'},
                img('./res/assets/github_icon.svg', 'githubIcon noDrag'),
                span('GitRepo'),
            ),
        )
    );
    const navPageButtonName = {
        // search: 'Search',
        // schedule: 'Schedule',
        // grades: 'Grades',
        search: '課程查詢',
        schedule: '課表',
        grades: '成績查詢',
    };

    // check login
    fetchApi('/login').then(onLoginStateChange);

    const root = div('root',
        // Pages
        hashRouter,
        // Navbar
        nav('navbar noSelect',
            NavSelectList('loginBtn',
                [
                    span(TextState(userLoginData, res => res ? res.studentID : 'Login')),
                ],
                [
                    ['Profile', null],
                    ['Logout', () => fetchApi('/logout').then(onLoginStateChange)],
                ],
                false,
                () => {
                    // Is login
                    if (userLoginData.state)
                        return true;
                    // Not login, open login window
                    showLoginWindow.set(!showLoginWindow.state);
                    return false;
                }
            ),
            ul('hamburgerMenu', img('./res/assets/burger_menu_icon.svg', 'noDrag', {onclick: () => window.navMenu.toggle('open')})),
            ul('homePage', li(null, text('NCKU++'))),
            ul(window.navMenu,
                hashRouter.getRoutesName().map(i =>
                    li(null, text(navPageButtonName[i]), {
                        onclick: () => hashRouter.openPage(i)
                    })
                ),
                // NavSelectList('arrow', text('List'), [
                //     ['0w0', null],
                //     ['awa', null],
                // ]),
            )
        ),
        ShowIf(showLoginWindow, LoginWindow(onLoginStateChange)),
        ShowIf(window.pageLoading, div('loading', window.loadingElement.cloneNode(true))),
        messageAlert,
        debugWindow,
    );
    window.onload = () => {
        document.body.append(root)
        hashRouter.init();
    };

    // functions
    function onLoginStateChange(response) {
        // Request timeout or cancel
        if (!response) {
            userLoginData.set(null);
            return;
        }
        /**@type LoginData*/
        const loginData = response.data;
        if (loginData && loginData.login) {
            userLoginData.set(loginData);
            showLoginWindow.set(false);
        } else {
            if (response.msg)
                messageAlert.addError('Login error', response.msg, 5000);
            userLoginData.set(null);
        }
    }
})();

/**
 * @param {string} classname
 * @param {Text|Element|Element[]} title
 * @param {[string, function()][]} items
 * @param {boolean} [enableHover]
 * @param {function(): boolean} [onOpen]
 * @return {HTMLUListElement}
 */
function NavSelectList(classname, title, items, enableHover, onOpen) {
    const itemsElement = ul(null);
    const list = ul(
        classname ? 'list ' + classname : 'list',
        enableHover ? {onmouseleave: closeSelectList, onmouseenter: openSelectList} : null,
        // Element
        li(null, title, {onclick: toggleSelectList}),
        itemsElement,
    );

    for (const item of items) {
        const itemOnclick = item[1];
        itemsElement.appendChild(
            li(null, text(item[0]), {
                onclick: itemOnclick ? () => {
                    itemOnclick();
                    closeSelectList();
                } : closeSelectList
            })
        );
    }

    function openSelectList() {
        if (window.innerWidth > mobileWidth)
            list.style.height = (list.firstElementChild.offsetHeight + list.lastElementChild.offsetHeight) + 'px';
    }

    function closeSelectList() {
        list.style.height = null;
    }

    function toggleSelectList() {
        if (onOpen && !onOpen() && !list.style.height) return;

        if (list.style.height)
            list.style.height = null;
        else
            list.style.height = (list.firstElementChild.offsetHeight + list.lastElementChild.offsetHeight) + 'px';
    }

    return list;
}

function MessageAlert() {
    const messageBoxRoot = div('messageBox');
    let updateTop = null;
    let height = 0;

    messageBoxRoot.addInfo = function (title, description, removeTimeout) {
        createMessageBox('info', title, description, removeTimeout);
    }

    messageBoxRoot.addError = function (title, description, removeTimeout) {
        createMessageBox('error', title, description, removeTimeout);
    }

    messageBoxRoot.addSuccess = function (title, description, removeTimeout) {
        createMessageBox('success', title, description, removeTimeout);
    }

    function onclick() {
        removeMessageBox(this.parentElement);
    }

    function createMessageBox(classname, title, description, removeTimeout) {
        const messageBox = div(classname,
            span(title, 'title'),
            span(description, 'description'),
            div('closeButton', {onclick})
        );
        messageBoxRoot.appendChild(messageBox);
        // height += messageBox.offsetHeight + 10;
        if (removeTimeout !== undefined)
            setTimeout(() => removeMessageBox(messageBox), removeTimeout);
        if (!updateTop)
            updateTop = setTimeout(updateMessageBoxTop, 0);
    }

    function removeMessageBox(messageBox) {
        messageBox.style.opacity = '0';
        setTimeout(() => {
            messageBoxRoot.removeChild(messageBox);
            updateMessageBoxTop();
        }, 200);
    }

    function updateMessageBoxTop() {
        height = 0;
        for (let node of messageBoxRoot.childNodes) {
            height -= node.offsetHeight + 10;
            node.style.bottom = height + 'px';
        }
        updateTop = null;
    }

    return messageBoxRoot;
}

function LoginWindow(onLoginStateChange) {
    const username = input('loginField', 'Account', null, {onkeyup, type: 'text'});
    const password = input('loginField', 'Password', null, {onkeyup, type: 'password'});
    let loading = false;

    function onkeyup(e) {
        if (e.key === 'Enter') login();
    }

    function login() {
        if (!loading) {
            loading = true;
            window.pageLoading.set(true);
            const usr = username.value.endsWith('@ncku.edu.tw') ? username : username.value + '@ncku.edu.tw';
            fetchApi('/login', {
                method: 'POST',
                body: `username=${encodeURIComponent(usr)}&password=${encodeURIComponent(password.value)}`,
                timeout: 10000,
            }).then(i => {
                loading = false;
                window.pageLoading.set(false);
                onLoginStateChange(i);
            });
        }
    }

    // element
    return div('loginWindow', {onRender: () => username.focus()},
        username,
        password,
        button('loginField', 'Login', login, {type: 'submit'}),
    );
}