const apiEndPoint = location.host === 'localhost' ? 'https://localhost/api' : 'https://api.simon.chummydns.com/api';

document.body.appendChild((function () {
    // 登入
    const loginBtn = button('loginBtn', '登入', onLoginButtonClick);
    const loginWindow = LoginWindow(onLoginStateChange);
    let login = false;

    // 下拉選單
    const navBar = nav('navbar',
        loginBtn,
    );

    // 課表
    const courseTable = CourseScheduleTable();

    // check login
    fetch(apiEndPoint + '/login', {credentials: "include"}).then(i => i.json()).then(
        /**@param{{login:boolean}}i*/i => {
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
        fetch(apiEndPoint + '/logout', {credentials: "include"}).then(
            /**@param{{login:boolean}}i*/i => {
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
        // for (let i = 0; i < 7; i++) {
        //     fetch(apiEndPoint + '/search?wk=' + (i + 1)).then(i => i.json()).then(
        //         /**@param{{login:boolean}}i*/i => {
        //             console.log(i)
        //         }).catch(j=>console.log(i, j));
        // }
    }

    function onLogout() {
        courseTable.clearCourseTable();
    }

    return div('root',
        navBar,
        courseTable,
    );
})());

function CourseScheduleTable() {
    const courseTable = table('courseScheduleTable');

    courseTable.clearCourseTable = function () {
        courseTable.deleteTHead();
        for (let tBody of courseTable.tBodies)
            courseTable.removeChild(tBody);
    }

    courseTable.fetchCourseTable = function () {
        const thead = courseTable.createTHead();
        const headRow = thead.insertRow();
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

        const tbody = courseTable.createTBody();

        fetch(apiEndPoint + '/courseSchedule', {credentials: "include"}).then(i => i.json()).then(
            ({id, name, credits, schedule, err}) => {
                if (err) return;

                console.log(schedule);
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
                        time.innerHTML = timeTable[i][1];
                        for (let j = 0; j < (holiday ? 7 : 5); j++) {
                            const cell = row.insertCell();
                            if (schedule[j][i].length > 0) {
                                cell.innerHTML = schedule[j][i][0] + schedule[j][i][1] + '<br>' + schedule[j][i][2];
                                fetch(apiEndPoint + '/search?serial=' + schedule[j][i][0], {credentials: "include"}).then(i => i.json())
                                    .then(i => console.log(i));
                            }
                        }
                    }
                }

            });
    }
    return courseTable;
}

function LoginWindow(onLogin) {
    const username = input('loginField', '帳號');
    const password = input('loginField', '密碼', ['type', 'password']);

    return div('loginWindow',
        username,
        password,
        button('loginField', '登入', () => {
            const usr = username.value.endsWith('@ncku.edu.tw') ? username : username.value + '@ncku.edu.tw';
            console.log(usr)
            fetch(apiEndPoint + '/login', {
                method: 'POST',
                credentials: 'include',
                body: `username=${encodeURIComponent(usr)}&password=${encodeURIComponent(password.value)}`
            }).then(i => i.json()).then(
                /**@param{{login:boolean, err:string, msg:string, warn:string}}i*/i => {
                    if (i.login)
                        onLogin(i.login);
                    if (i.err === 'loginErr')
                        alert(i.msg);
                });
        }),
    );
}