'use strict';

/*ExcludeStart*/
const {div, button, table, Signal, text, span, ShowIf} = require('../domHelper');
/*ExcludeEnd*/

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
const nightTimeStart = 10;

function CourseInfoWindow(showCourseInfoWindow) {
    const body = div();
    const courseInfoWindow = div('courseInfoWindow',
        button('closeButton', 'x', () => {
            showCourseInfoWindow.set(false);
        }),
        body
    );

    courseInfoWindow.clear = function () {
        body.innerHTML = '';
    }

    courseInfoWindow.add = function (element) {
        body.appendChild(element);
    }

    return courseInfoWindow;
}

module.exports = function (loginState) {
    console.log('Course schedule Init');
    // static element
    let styles = async_require('./courseSchedule.css');
    const scheduleStudentInfo = new Signal();
    const showCourseInfoWindow = new Signal(false);
    const courseInfoWindow = CourseInfoWindow(showCourseInfoWindow);
    const scheduleTable = new ScheduleTable(scheduleStudentInfo, showCourseInfoWindow, courseInfoWindow);

    onLoginState(loginState.state);

    async function onRender() {
        console.log('Course schedule Render');
        // close navLinks when using mobile devices
        navLinksClass.remove('open');
        loginState.addListener(onLoginState);
        (styles = await styles).add();
    }

    function onDestroy() {
        console.log('Course schedule Destroy');
        styles.remove();
        loginState.removeListener(onLoginState);
    }

    function onLoginState(state) {
        if (state)
            fetchApi('/courseSchedule').then(scheduleTable.init);
        else
            scheduleTable.clear();
    }

    return div('courseSchedule',
        {onRender, onDestroy},
        div('courseScheduleInfo',
            span(scheduleStudentInfo)
        ),
        scheduleTable.table(),
        ShowIf(showCourseInfoWindow, courseInfoWindow),
    );
};

function ScheduleTable(scheduleStudentInfo, showCourseInfoWindow, courseInfoWindow) {
    const courseInfo = {};
    const scheduleTable = table('courseScheduleTable', {'cellPadding': 0});
    let thead = scheduleTable.createTHead(), tbody = scheduleTable.createTBody();

    function cellClick() {
        const info = courseInfo[this.serialID];
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
            for (let time of info.t) {
                time = time.split(',');
                courseInfoWindow.add(
                    button(null, time[0] + ' ' + time[1] + ' ' + time[4],
                        () => {
                            fetchApi('/extract?l=' + time[2] + ',' + time[3]).then(i => {
                                if (i.data && i.data.status)
                                    window.open(i.data.url, '_blank');
                            });
                        }
                    )
                );
            }
            courseInfoWindow.add(text(JSON.stringify(info, null, 4)));
            showCourseInfoWindow.set(true);
        }
    }

    this.init = function ({id, credits, schedule, err}) {
        if (err) return;
        thead.innerHTML = '';
        tbody.innerHTML = '';
        scheduleStudentInfo.set(id + ' ' + 'credits: ' + credits);


        let nightTime = false;
        let holiday = false;
        for (const i of schedule) for (const j of i.info) {
            // parse time
            const time = j.time = j.time.split(',');
            time[0] = parseInt(time[0]);
            if (time.length > 1) {
                time[1] = time[1] === 'N' ? -1 : parseInt(time[1], 16);
                if (time[1] > 4) time[1]++;
                else if (time[1] === -1) time[1] = 5;
            }
            if (time.length > 2) {
                time[2] = time[2] === 'N' ? -1 : parseInt(time[2], 16);
                if (time[2] > 4) time[2]++;
                else if (time[1] === -1) time[2] = 5;
            }
            if (time.length > 0 && time[0] > 4) holiday = true;
            if (time.length > 1 && time[1] > nightTimeStart || time.length > 2 && time[2] > nightTimeStart) nightTime = true;
        }
        const tableWidth = holiday ? 7 : 5;
        const tableHeight = nightTime ? 17 : 11;

        const days = new Array(tableWidth);
        const daysUndecided = [];
        for (let i = 0; i < 7; i++) days[i] = new Array(tableHeight);
        for (const i of schedule)
            for (const j of i.info) {
                // Time undecided
                if (j.time[0] === -1) {
                    daysUndecided.push([i, j]);
                    continue;
                }

                // Add course to timetable
                const k = days[j.time[0]][j.time[1]];
                if (k instanceof Array)
                    k.push(j);
                else
                    days[j.time[0]][j.time[1]] = [i, j];
            }

        // Table header
        const headRow = thead.insertRow();
        headRow.className = 'noSelect';
        for (let i = 0; i < tableWidth + 1; i++) {
            const cell = headRow.insertCell();
            cell.textContent = weekTable[i];
            if (i === 0) cell.colSpan = 2;
        }
        headRow.appendChild(div('background'));

        // Table time cell
        const rows = new Array(tableHeight);
        const rowSize = new Int32Array(tableHeight);
        for (let i = 0; i < tableHeight; i++) {
            rows[i] = tbody.insertRow();
            const index = rows[i].insertCell();
            index.textContent = timeTable[i][0];
            index.className = 'noSelect';
            const time = rows[i].insertCell();
            if (timeTable[i][1]) time.textContent = timeTable[i][1];
            time.className = 'noSelect';
        }

        // Add course cell
        for (let i = 0; i < tableWidth; i++) {
            for (let j = 0; j < tableHeight; j++) {
                const course = days[i][j];
                if (!course) continue;
                // Fill space
                if (i - rowSize[j] > 0)
                    rows[j].insertCell().colSpan = i - rowSize[j];

                // Build cell
                const info = course[0];
                const cell = rows[j].insertCell();
                cell.className = 'activateCell';
                cell.serialID = info.deptID + '-' + info.sn;
                cell.onclick = cellClick;
                courseInfo[cell.serialID] = null;

                const rooms = [];
                for (let k = 1; k < course.length; k++)
                    rooms.push(span(course[k].room));

                cell.appendChild(
                    div(null,
                        span(info.name),
                        rooms,
                    )
                );


                // Add space
                if (course[1].time.length === 3) {
                    const length = course[1].time[2] - course[1].time[1] + 1;
                    cell.rowSpan = length;
                    rowSize[j] = i + 1;
                    for (let k = 1; k < length; k++) {
                        // fill space
                        if (i - rowSize[j + k] > 0)
                            rows[j + k].insertCell().colSpan = i - rowSize[j + k];
                        rowSize[j + k] = i + 1;
                    }
                } else if (course[1].time.length === 2)
                    rowSize[j] = i + 1;
            }
        }

        for (let i = 0; i < tableHeight; i++) {
            rows[i].appendChild(div('splitLine'));
        }

        // Add day undecided
        for (let i = 0; i < daysUndecided.length; i++) {
            const row = tbody.insertRow();
            // Time info
            if (i === 0) {
                const empty = div();
                empty.style.display = 'none';
                row.appendChild(empty);
                const cell = row.insertCell();
                cell.colSpan = 2;
                cell.className = 'noSelect';
                cell.textContent = 'Undecided'
            }

            // Build cell
            const course = daysUndecided[i];
            const info = course[0];
            const cell = row.insertCell();
            cell.className = 'activateCell';
            cell.colSpan = tableWidth;
            cell.serialID = info.deptID + '-' + info.sn;
            cell.onclick = cellClick;
            courseInfo[cell.serialID] = null;

            const rooms = [];
            for (let k = 1; k < course.length; k++)
                rooms.push(span(course[k].room));

            cell.appendChild(
                div(null,
                    span(info.name),
                    rooms,
                )
            );


            row.appendChild(div('splitLine'));
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
    };

    this.clear = function(){
        thead.innerHTML = '';
        tbody.innerHTML = '';
        scheduleStudentInfo.set();
    };

    this.table = function () {return scheduleTable;};
}