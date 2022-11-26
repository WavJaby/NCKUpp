'use strict';

const {
    Signal,
    ShowIf,
    QueryRouter,
    div,
    nav,
    ul,
    li,
    input,
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
    debug: doomDebug
} = require('./res/domHelper');
const loadingElement = div('loaderCircle',
    svg('<circle class="path" cx="50" cy="50" r="20" fill="none" stroke-width="5" stroke-linecap="square"/>', '25 25 50 50', 'circular')
);
const navLinksClass = new ClassList('links');
const apiEndPoint = location.hostname === 'localhost'
    ? 'http://localhost:8080/api'
    : 'https://api.simon.chummydns.com/api';
/**
 * @typedef {{err:string, msg:string, warn:string, login:boolean, data:any}} APIResponse
 */
/**
 * @param endpoint {string}
 * @param [option] {RequestInit}
 * @return Promise<APIResponse>
 * */
window.fetchApi = function (endpoint, option) {
    if (option) option.credentials = 'include';
    else option = {credentials: 'include'};
    return fetch(apiEndPoint + endpoint, option).then(i => i.json());
};

// Main function
(function main() {
    // debug
    let memoryUpdate = null;
    if (location.hostname === 'localhost') {
        doomDebug();
        memoryUpdate = new Signal(window.performance.memory);
        setInterval(() => memoryUpdate.set(window.performance.memory), 1000);
    }

    // main code
    let login = false;
    const loginState = new Signal(false);
    const showLoginWindow = new Signal(false);

    const queryRouter =
        QueryRouter('schedule', {
            search: () => require('./res/pages/courseSearch')(),
            schedule: () => require('./res/pages/courseSchedule')(loginState),
        });
    const navPageButtonName = {
        search: 'Search',
        schedule: 'Schedule'
    };

    // check login
    fetchApi('/login').then(onLoginStateChange);

    const root = div('root',
        // Pages
        queryRouter,
        // 選單列
        nav('navbar noSelect',
            ul('loginBtn',
                button(null, TextState(loginState, (state) => state ? '登出' : '登入'), () => {
                    if (login) fetchApi('/logout').then(onLoginStateChange);
                    else showLoginWindow.set(!showLoginWindow.state);
                }),
            ),
            ul('hamburgerMenu', img('./res/assets/burger_menu_icon.svg', 'noDrag', {onclick: () => navLinksClass.toggle('open')})),
            ul(navLinksClass,
                li('list', span('List'),
                    ul(null,
                        li(null, text('0w0')),
                        li(null, text('awa')),
                    ),
                ),
                queryRouter.getRoutesName().map(i =>
                    li(null, text(navPageButtonName[i]), {
                        onclick: () => queryRouter.openPage(i)
                    })
                ),
            )
        ),
        ShowIf(showLoginWindow,
            LoginWindow(onLoginStateChange)
        ),
        memoryUpdate === null ? null :
            span(TextState(memoryUpdate, state => (state.usedJSHeapSize / 1000 / 1000).toFixed(2) + 'MB'), null, {style: 'position: absolute; top: 0; z-index: 100; background: black; font-size: 10px; opacity: 0.5;'}),
    );
    window.onload = () => document.body.appendChild(root);

    function onLoginStateChange(response) {
        if (response.err === 'loginErr')
            alert(response.msg);
        if (login === response.login) return;
        loginState.set(login = response.login);
        if (response.login) onLogin();
        else onLogout();
    }

    function onLogin() {
        showLoginWindow.set(false);
    }

    function onLogout() {
    }

    function LoginWindow(onLoginStateChange) {
        const username = input('loginField', '帳號', null, {onkeyup, type: 'text'});
        const password = input('loginField', '密碼', null, {onkeyup, type: 'password'});

        function onkeyup(e) {
            if (e.key === 'Enter') login();
        }

        let loading = false;

        function login() {
            const usr = username.value.endsWith('@ncku.edu.tw') ? username : username.value + '@ncku.edu.tw';
            if (!loading) {
                fetchApi('/login', {
                    method: 'POST',
                    body: `username=${encodeURIComponent(usr)}&password=${encodeURIComponent(password.value)}`
                }).then(i => {
                    onLoginStateChange(i);
                    loading = false;
                });
                loading = true;
            }
        }

        // element
        return div('loginWindow', {onRender: () => username.focus()},
            username,
            password,
            button('loginField', '登入', login, {type: 'submit'}),
        );
    }
})();