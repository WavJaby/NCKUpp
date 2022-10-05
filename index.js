'use strict';
const {ShowIf, div, button, span, table, Signal, text, input, nav} = require('./res/domHelper');
const apiEndPoint = location.host === 'localhost'
    ? 'http://localhost/api'
    : 'https://api.simon.chummydns.com/api';

(async function main() {
    // 登入
    let login = false;
    const loginState = new Signal(false, ['登入', '登出']);
    const showLoginWindow = new Signal(false);
    // check login
    fetchApi('/login').then(onLoginStateChange);

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

    document.body.appendChild(div('root',
        // 下拉選單
        nav('navbar',
            div(null,
                ShowIf(showLoginWindow,
                    LoginWindow(onLoginStateChange)
                )
            ),
            button('loginBtn', loginState, () => {
                if (login) fetchApi('/logout').then(onLoginStateChange);
                else showLoginWindow.set(!showLoginWindow.get());
            }),
        ),
        // 課表
        div(null,
            ShowIf(loginState, CourseScheduleTable(CourseInfoWindow()))
        )
    ));

    function CourseInfoWindow() {
        const body = div();
        const courseInfoWindow = div('courseInfoWindow',
            button('closeButton', 'x', () => {
                if (document.body.contains(courseInfoWindow))
                    document.body.removeChild(courseInfoWindow);
            }),
            body
        );

        courseInfoWindow.show = function () {
            document.body.appendChild(courseInfoWindow);
        }

        courseInfoWindow.clear = function () {
            body.innerHTML = '';
        }

        courseInfoWindow.add = function (element) {
            body.appendChild(element);
        }

        return courseInfoWindow;
    }

    function CourseScheduleTable(courseInfoWindow) {
        // static
        const weekTable = ['#', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
        const timeTable = [
            ['0', '07:10\n08:00'],
            ['1', '08:10\n09:00'],
            ['2', '09:10\n10:00'],
            ['3', '10:10\n11:00'],
            ['4', '11:10\n12:00'],
            ['N', '12:10\n13:00'],
            ['5', '13:10\n14:00'],
            ['6', '14:10\n15:00'],
            ['7', '15:10\n16:00'],
            ['8', '16:10\n17:00'],
            ['9', '17:10\n18:00'],
            ['A', '18:10\n19:00'],
            ['B', '19:10\n20:00'],
            ['C', '20:10\n21:00'],
            ['D', '21:10\n22:00'],
            ['E'],
            ['F'],
        ];
        const nightTimeStart = 11;

        // static element
        const scheduleTable = table('courseScheduleTable');
        const scheduleStudentInfo = new Signal();
        // data
        const courseInfo = {};
        let thead, tbody;

        function onRender() {
            if (thead) thead.remove();
            if (tbody) tbody.remove();
            fetchApi('/courseSchedule').then(buildScheduleTable);
        }

        function cellClick(e) {
            const info = courseInfo[e.target.serialID];
            if (info) {
                courseInfoWindow.clear();
                courseInfoWindow.add(
                    button(null, 'moodle',
                        () => {
                            fetchApi('/extract?m=' + info.m).then(i => {
                                if (i.data && i.data.status)
                                    window.open(i.data.url, '_blank');
                            });
                        }
                    )
                );
                for (const locationKey of info.l) {
                    courseInfoWindow.add(
                        button(null, 'location',
                            () => {
                                fetchApi('/extract?l=' + locationKey).then(i => {
                                    if (i.data && i.data.status)
                                        window.open(i.data.url, '_blank');
                                });
                            }
                        )
                    );
                }
                courseInfoWindow.add(text(JSON.stringify(info, null, 4)));
                courseInfoWindow.show();
            }
        }

        function buildScheduleTable({id, name, credits, schedule, err}) {
            if (err) return;
            scheduleStudentInfo.set(id + ' ' + name + ' ' + 'credits: ' + credits);

            thead = scheduleTable.createTHead();
            tbody = scheduleTable.createTBody();
            const headRow = thead.insertRow();

            let nightTime = false;
            let holiday = false;
            for (let i = nightTimeStart; i < 16; i++)
                for (let j = 0; j < 7; j++)
                    if (schedule[j][i].length > 0)
                        nightTime = true;

            for (let i = 0; i < 16; i++)
                for (let j = 5; j < 7; j++)
                    if (schedule[j][i].length > 0)
                        holiday = true;
            // header
            for (let i = 0; i < (holiday ? 7 : 5) + 1; i++)
                headRow.insertCell().textContent = weekTable[i];

            // body
            for (let i = 0; i < 17; i++) {
                const row = tbody.insertRow();
                if (i < nightTimeStart || nightTime && i < 16) {
                    const time = row.insertCell();
                    time.textContent = timeTable[i][1];
                    for (let j = 0; j < (holiday ? 7 : 5); j++) {
                        const cell = row.insertCell();
                        if (schedule[j][i].length > 0) {
                            const serialID = schedule[j][i][0];
                            courseInfo[serialID] = null;
                            cell.classList.add('activateCell');
                            cell.textContent = serialID + schedule[j][i][1] + '\n' + schedule[j][i][2];
                            cell.serialID = serialID;
                            cell.onclick = cellClick;
                        }
                    }
                }
            }

            // get course info
            const courseDept = {};
            for (const serialID in courseInfo) {
                const dept = serialID.split('-');
                let deptData = courseDept[dept[0]];
                if (deptData)
                    deptData.push(dept[1]);
                else
                    courseDept[dept[0]] = [dept[1]];
            }
            const courseFetchArr = [];
            for (const serialID in courseDept)
                courseFetchArr.push(serialID + '=' + courseDept[serialID].join(','));
            const spl = encodeURIComponent('&');
            const courseFetchData = encodeURIComponent(courseFetchArr.join(spl));

            // fetch data
            fetchApi('/search?serial=' + courseFetchData).then(i => {
                for (const entry of Object.entries(i))
                    for (const course of entry[1])
                        courseInfo[course.sn] = course;
            });
        }

        return div(null,
            ['onRender', onRender],
            div('courseScheduleInfo',
                span(scheduleStudentInfo)
            ),
            scheduleTable
        );
    }

    function LoginWindow(onLoginStateChange) {
        const username = input('loginField', '帳號', ['autocomplete', 'on']);
        const password = input('loginField', '密碼', ['type', 'password']);

        let loading = false;
        // element
        return div('loginWindow',
            username,
            password,
            button('loginField', '登入', () => {
                const usr = username.value.endsWith('@ncku.edu.tw') ? username : username.value + '@ncku.edu.tw';
                if (!loading)
                    fetchApi('/login', {
                        method: 'POST',
                        body: `username=${encodeURIComponent(usr)}&password=${encodeURIComponent(password.value)}`
                    }).then(i => {
                        onLoginStateChange(i);
                        loading = false;
                    });
                loading = true;
            }),
        );
    }
})();

/**
 * @typedef {{err:string, msg:string, warn:string, login:boolean, data:{}}} APIResponse
 */

/**
 * @param endpoint {string}
 * @param [option] {RequestInit}
 * @return Promise<APIResponse>
 * */
function fetchApi(endpoint, option) {
    if (option) option.credentials = 'include';
    else option = {credentials: 'include'};
    return fetch(apiEndPoint + endpoint, option).then(i => i.json());
}