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
    debug: doomDebug, footer
} = require('./res/domHelper');
const loadingElement = div('loaderCircle',
    svg('<circle class="path" cx="50" cy="50" r="20" fill="none" stroke-width="5" stroke-linecap="square"/>', '25 25 50 50', 'circular')
);
const navLinksClass = new ClassList('links');
const apiEndPoint = location.hostname === 'localhost'
    ? 'http://localhost:8080/api'
    : 'https://api.simon.chummydns.com/api';
const mobile = window.innerWidth <= 700;
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
    const userData = new Signal();
    const showLoginWindow = new Signal(false);

    const queryRouter =
        QueryRouter('schedule',
            {
                search: () => require('./res/pages/courseSearch')(),
                schedule: () => require('./res/pages/courseSchedule')(userData),
            },
            Footer());
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
            ul('loginBtn list',
                li('btn',
                    img('./res/assets/github_icon.svg'),
                    div(null, span(TextState(userData, (userData) => userData ? userData.studentID : 'Login'))),
                    {
                        onclick: function () {
                            // if login state
                            if (userData.state) listToggle(this.parentElement);
                            // toggle login window
                            else showLoginWindow.set(!showLoginWindow.state);
                        },
                    },
                ),
                ul(null,
                    li(null, text('Profile'), {
                        onclick: function () {
                            listClose(this.parentElement.parentElement);
                        }
                    }),
                    li(null, text('Logout'), {
                        onclick: function () {
                            fetchApi('/logout').then(onLoginStateChange);
                            listClose(this.parentElement.parentElement);
                        }
                    }),
                ),
            ),
            ul('hamburgerMenu', img('./res/assets/burger_menu_icon.svg', 'noDrag', {onclick: () => navLinksClass.toggle('open')})),
            ul(navLinksClass,
                li('list', {onmouseleave: listLeave, onmouseover: listHover},
                    span('List', null, {
                        onclick: function () {listToggle(this.parentElement);}
                    }),
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
    window.onload = () => document.body.append(root);

    // functions
    function onLoginStateChange(response) {
        if (response.err === 'loginErr')
            alert(response.msg);
        if (response.login) {
            userData.set(response);
            showLoginWindow.set(false);
        } else
            userData.set(null);
    }

    function listHover() {
        if (!mobile)
            this.style.height = (
                this.firstElementChild.offsetHeight +
                this.lastElementChild.offsetHeight
            ) + 'px';
    }

    function listLeave() {
        this.style.height = null;
    }

    function listClose(list) {
        list.style.height = null;
    }

    function listToggle(list) {
        if (list.style.height)
            list.style.height = null;
        else
            list.style.height = (list.firstElementChild.offsetHeight + list.lastElementChild.offsetHeight) + 'px';
    }

    function LoginWindow(onLoginStateChange) {
        const username = input('loginField', 'Account', null, {onkeyup, type: 'text'});
        const password = input('loginField', 'Password', null, {onkeyup, type: 'password'});

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
            button('loginField', 'Login', login, {type: 'submit'}),
        );
    }

    function Footer() {
        return footer(

        );
    }
})();