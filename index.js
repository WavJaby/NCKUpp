'use strict';
const {
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
    debug: doomDebug, footer
} = require('./res/domHelper');
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
 * @param endpoint {string}
 * @param [option] {RequestInit}
 * @return Promise<ApiResponse>
 */
window.fetchApi = function (endpoint, option) {
    if (option) option.credentials = 'include';
    else option = {credentials: 'include'};
    return fetch(apiEndPoint + endpoint, option)
        .catch(i => messageAlert.addError(
            'Api error',
            i instanceof Error ? i.stack || i || 'Unknown error!' : i, 10000)
        )
        .then(i => i.json())
        .then(i => {
                if (!i.success)
                    messageAlert.addError(
                        'Api request error',
                        i.err.join('\n'), 10000);
                return i;
            }
        );
};

window.hashData = {
    hashMap: window.location.hash.length !== 0 ? JSON.parse(decodeURIComponent(atob(window.location.hash.slice(1)))) : {},
    get(key) {
        return this.hashMap[key];
    },
    set(key, value) {
        this.hashMap[key] = value;
        window.location.hash = btoa(encodeURIComponent(JSON.stringify(this.hashMap)));
    },
    contains(key) {
        return this.hashMap[key] !== undefined;
    }
};
window.loadingElement = svg('<circle class="path" cx="25" cy="25" r="20" fill="none" stroke-width="5" stroke-linecap="square"/>', '0 0 50 50', 'loaderCircle');
window.navMenu = new ClassList('links');

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

        console.log(window.hashData.hashMap);
        const memoryUpdate = new Signal(window.performance.memory);
        setInterval(() => memoryUpdate.set(window.performance.memory), 1000);
        debugWindow = span(
            TextState(memoryUpdate, state => (state.usedJSHeapSize / 1000 / 1000).toFixed(2) + 'MB'),
            null,
            {style: 'position: absolute; top: 0; z-index: 100; background: black; font-size: 10px; opacity: 0.5;'})
    }

    // main code
    const userLoginData = new Signal();
    const pageLoading = new Signal(false);
    const showLoginWindow = new Signal(false);
    const hashRouter = HashRouter('schedule',
        {
            search: () => require('./res/pages/courseSearch')(userLoginData),
            schedule: () => require('./res/pages/courseSchedule')(userLoginData),
            grades: () => require('./res/pages/stuIdSysGrades')(userLoginData),
        },
        Footer()
    );
    const navPageButtonName = {
        search: 'Search',
        schedule: 'Schedule',
        grades: 'Grades',
    };

    // check login
    fetchApi('/login').then(onLoginStateChange);

    const root = div('root',
        // Pages
        hashRouter,
        // Navbar
        nav('navbar noSelect',
            NavSelectList('loginBtn', [
                img('./res/assets/github_icon.svg'),
                span(TextState(userLoginData, response => response ? response.studentID : 'Login')),
            ], [
                li(null, text('Profile')),
                li(null, text('Logout'), {onclick: () => fetchApi('/logout').then(onLoginStateChange)})
            ], false, () => !userLoginData.state && (showLoginWindow.set(!showLoginWindow.state) || true)),
            ul('hamburgerMenu', img('./res/assets/burger_menu_icon.svg', 'noDrag', {onclick: () => window.navMenu.toggle('open')})),
            ul(window.navMenu,
                NavSelectList('arrow', text('List'), [
                    li(null, text('0w0')),
                    li(null, text('awa'))
                ]),
                hashRouter.getRoutesName().map(i =>
                    li(null, text(navPageButtonName[i]), {
                        onclick: () => hashRouter.openPage(i)
                    })
                ),
            )
        ),
        ShowIf(showLoginWindow, LoginWindow(onLoginStateChange, pageLoading)),
        ShowIf(pageLoading, div('loading', window.loadingElement.cloneNode(true))),
        messageAlert,
        debugWindow,
    );
    window.onload = () => document.body.append(root);

    // functions
    function onLoginStateChange(response) {
        /**@type LoginData*/
        const loginData = response.data;
        if (loginData && loginData.login) {
            userLoginData.set(loginData);
            showLoginWindow.set(false);
        } else {
            if (response.msg)
                messageAlert.addError('Login error', response.msg, 10000);
            userLoginData.set(null);
        }
    }
})();

function NavSelectList(classname, title, items, enableHover, onOpen) {
    const list = ul(classname ? 'list ' + classname : 'list', enableHover ? {onmouseleave, onmouseenter} : null,
        li(null, title, {onclick}),
        ul(null, items)
    );

    for (const item of items) {
        const itemOnclick = item.onclick;
        if (itemOnclick)
            item.onclick = () => {
                itemOnclick();
                onmouseleave();
            };
        else
            item.onclick = onmouseleave;
    }

    function onmouseenter() {
        if (window.innerWidth > mobileWidth)
            list.style.height = (list.firstElementChild.offsetHeight + list.lastElementChild.offsetHeight) + 'px';
    }

    function onmouseleave() {
        list.style.height = null;
    }

    function onclick() {
        if (onOpen && onOpen() && !list.style.height) return;

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
        height += messageBox.offsetHeight + 10;
        if (removeTimeout !== undefined)
            setTimeout(() => removeMessageBox(messageBox), removeTimeout);
        if (!updateTop)
            updateTop = setTimeout(updateMessageBoxTop, 50);
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
            node.style.top = height + 'px';
            height += node.offsetHeight + 10;
        }
        updateTop = null;
    }

    return messageBoxRoot;
}

function LoginWindow(onLoginStateChange, pageLoading) {
    const username = input('loginField', 'Account', null, {onkeyup, type: 'text'});
    const password = input('loginField', 'Password', null, {onkeyup, type: 'password'});
    let loading = false;

    function onkeyup(e) {
        if (e.key === 'Enter') login();
    }

    function login() {
        if (!loading) {
            loading = true;
            pageLoading.set(true);
            const usr = username.value.endsWith('@ncku.edu.tw') ? username : username.value + '@ncku.edu.tw';
            fetchApi('/login', {
                method: 'POST',
                body: `username=${encodeURIComponent(usr)}&password=${encodeURIComponent(password.value)}`
            }).then(i => {
                loading = false;
                pageLoading.set(false);
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

function Footer() {
    return footer(

    );
}