const apiEndPoint = location.host === 'localhost' ? 'https://localhost/api' : 'https://api.simon.chummydns.com/api';

// 課表
const courseInfoWindow = CourseInfoWindow();
const courseTable = CourseScheduleTable();

document.body.appendChild((function () {
    // 登入
    const loginBtn = button('loginBtn', '登入', onLoginButtonClick);
    const loginWindow = LoginWindow(onLoginStateChange);
    let login = false;

    // 下拉選單
    const navBar = nav('navbar',
        loginBtn,
    );

    // check login
    fetchApi('/login').then(/**@param{{login:boolean}}i*/i => {
        onLoginStateChange(i.login);
    });

    function onLoginButtonClick() {
        if (login)
            logOut();
        else {
            if (navBar.contains(loginWindow))
                navBar.removeChild(loginWindow);
            else
                navBar.appendChild(loginWindow);
        }
    }

    function logOut() {
        fetchApi('/logout').then(/**@param{{login:boolean}}i*/i => {
            if (login !== i.login)
                onLoginStateChange(i.login);
        });
    }


    function onLoginStateChange(isLogin) {
        login = isLogin;
        if (isLogin) {
            loginBtn.textContent = '登出';
            if (navBar.contains(loginWindow))
                navBar.removeChild(loginWindow);
            onLogin();
        } else {
            loginBtn.textContent = '登入';
            onLogout();
        }
    }

    function onLogin() {
        courseTable.fetchCourseTable();
    }

    function onLogout() {
        courseTable.clearCourseTable();
    }

    return div('root',
        navBar,
        courseTable,
    );
})());

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

function CourseScheduleTable() {
    // static
    const weekTable = ['#', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const timeTable = [
        ['0', '07:10<br/>08:00'],
        ['1', '08:10<br/>09:00'],
        ['2', '09:10<br/>10:00'],
        ['3', '10:10<br/>11:00'],
        ['4', '11:10<br/>12:00'],
        ['N', '12:10<br/>13:00'],
        ['5', '13:10<br/>14:00'],
        ['6', '14:10<br/>15:00'],
        ['7', '15:10<br/>16:00'],
        ['8', '16:10<br/>17:00'],
        ['9', '17:10<br/>18:00'],
        ['A', '18:10<br/>19:00'],
        ['B', '19:10<br/>20:00'],
        ['C', '20:10<br/>21:00'],
        ['D', '21:10<br/>22:00'],
        ['E'],
        ['F'],
    ];
    const nightTimeStart = 11;

    // static element
    const scheduleTable = table('courseScheduleTable');
    const scheduleInfo = div('courseScheduleInfo');
    const scheduleData = div();
    // data
    const courseInfo = {};

    // functions
    scheduleData.clearCourseTable = function () {
        scheduleData.removeChild(scheduleInfo);
        scheduleData.removeChild(scheduleTable);

        scheduleInfo.innerHTML = '';
        scheduleTable.innerHTML = '';
    }

    scheduleData.fetchCourseTable = function () {
        fetchApi('/courseSchedule').then(buildScheduleTable);
        scheduleData.appendChild(scheduleInfo);
        scheduleData.appendChild(scheduleTable);
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
            courseInfoWindow.add(text(JSON.stringify(info, null, 4)));
            courseInfoWindow.show();
        }
    }

    function buildScheduleTable({id, name, credits, schedule, err}) {
        if (err) return;

        const thead = scheduleTable.createTHead();
        const tbody = scheduleTable.createTBody();
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

        scheduleInfo.appendChild(text(id + ' ' + name + ' ' + 'credits: ' + credits));

        // body
        for (let i = 0; i < 17; i++) {
            const row = tbody.insertRow();
            if (i < nightTimeStart || nightTime && i < 16) {
                const time = row.insertCell();
                time.innerHTML = timeTable[i][1];
                for (let j = 0; j < (holiday ? 7 : 5); j++) {
                    const cell = row.insertCell();
                    if (schedule[j][i].length > 0) {
                        const serialID = schedule[j][i][0];
                        courseInfo[serialID] = null;
                        cell.classList.add('activateCell');
                        cell.innerHTML = serialID + schedule[j][i][1] + '<br>' + schedule[j][i][2];
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

    return scheduleData;
}

function LoginWindow(onLogin) {
    const username = input('loginField', '帳號', ['autocomplete', 'on']);
    const password = input('loginField', '密碼', ['type', 'password']);

    let loading = false;
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
                    if (i.login)
                        onLogin(i.login);
                    if (i.err === 'loginErr')
                        alert(i.msg);
                    loading = false;
                });
            loading = true;
        }),
    );
}

/**
 * @param endpoint {string}
 * @param [option] {{}}
 * @return Promise<{err:string, msg:string, warn:string}>
 * */
function fetchApi(endpoint, option) {
    if (option) option.credentials = 'include';
    return fetch(apiEndPoint + endpoint, option).then(i => i.json());
}