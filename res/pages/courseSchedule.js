'use strict';

/*ExcludeStart*/
const module = {};
const {checkboxWithName, div, button, table, Signal, text, span, ShowIf, linkStylesheet} = require('../domHelper');
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
    const styles = linkStylesheet('./res/pages/courseSchedule.css');
    const showCourseInfoWindow = new Signal(false);
    const courseInfoWindow = CourseInfoWindow(showCourseInfoWindow);
    const showClassroomCheckbox = checkboxWithName(null, '顯示教室', false);
    const showPreScheduleCheckbox = checkboxWithName(null, '顯示預排科目', false);
    const scheduleTable = new ScheduleTable(showCourseInfoWindow, courseInfoWindow);

    showClassroomCheckbox.input.onchange = () => scheduleTable.showRoomName(showClassroomCheckbox.input.checked);

    showPreScheduleCheckbox.input.onchange = () => scheduleTable.setTableShow(showPreScheduleCheckbox.input.checked);

    function onRender() {
        console.log('Course schedule Render');
        styles.mount();
    }

    function onPageOpen() {
        console.log('Course schedule Open');
        // close navLinks when using mobile devices
        window.navMenu.remove('open');
        styles.enable();
        loginState.addListener(onLoginState);
        onLoginState(loginState.state);
    }


    function onPageClose() {
        console.log('Course schedule Close');
        styles.disable();
        loginState.removeListener(onLoginState);
    }


    /**
     * @param {LoginData} state
     */
    function onLoginState(state) {
        if (state && state.login) {
            window.fetchApi('/courseSchedule', 'Get schedule').then(data => {
                // Parse normal schedule
                scheduleTable.init(data);
                // Parse pre schedule
                window.fetchApi('/preCourseSchedule', 'Get pre schedule').then(scheduleTable.initPreSchedule);
            });
        } else {
            if (state)
                window.askForLoginAlert();
            scheduleTable.clear();
        }
    }

    return div('courseSchedule',
        {onRender, onPageOpen, onPageClose},
        div('scheduleTab'),
        div('options',
            showClassroomCheckbox,
            showPreScheduleCheckbox,
        ),
        scheduleTable.table,
        ShowIf(showCourseInfoWindow, courseInfoWindow),
    );
};

/**
 * @typedef ScheduleData
 * @property {string} studentId
 * @property {int} year
 * @property {int} semester
 * @property {float} credits
 * @property {{
 *     deptID: string,
 *     sn: string,
 *     name: string,
 *     required: boolean,
 *     credits: float,
 *     info: {
 *         type: string,
 *         time: string,
 *         roomID: string,
 *         room: string,
 *         time: int[],
 *     }[],
 * }[]} schedule
 */
function ScheduleTable(showCourseInfoWindow, courseInfoWindow) {
    const courseInfo = {};
    const roomNameElements = [];
    const scheduleTable = table('courseScheduleTable', {'cellPadding': 0});
    const preScheduleTable = table('courseScheduleTable pre', {'cellPadding': 0});
    preScheduleTable.style.display = 'none';
    this.table = div(null, scheduleTable, preScheduleTable);

    let savedScheduleData = null;
    let savedScheduleTableWidth = 0;
    let savedScheduleTableHeight = 0;
    let showClassRoom = false;


    function cellClick() {
        const info = courseInfo[this.serialID];
        if (info) {
            courseInfoWindow.clear();
            courseInfoWindow.add(
                button(null, 'moodle',
                    () => {
                        window.fetchApi('/extract?m=' + info.m).then(i => {
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
                            window.fetchApi('/extract?l=' + time[2] + ',' + time[3]).then(i => {
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

    /**
     * @param {ApiResponse} response
     */
    this.init = function (response) {
        if (!response.success) return;
        /**@type ScheduleData*/
        const scheduleData = response.data;
        initTable(scheduleTable);
        scheduleTable.caption.textContent = scheduleData.studentId + ' credits: ' + scheduleData.credits;
        roomNameElements.length = 0;

        // Get table size
        const [tableWidth, tableHeight] = timeTxt2IntAndGetTableSize(scheduleData);
        savedScheduleData = scheduleData;
        savedScheduleTableWidth = tableWidth;
        savedScheduleTableHeight = tableHeight;
        // Parse data
        const {dayTable, dayUndecided} = parseData2DayTable(tableWidth, tableHeight, scheduleData);

        // Create table header
        const rows = createScheduleTable(scheduleTable, tableWidth, tableHeight);

        // Add course cell
        const rowSize = new Int32Array(tableHeight);
        for (let i = 0; i < tableWidth; i++) {
            for (let j = 0; j < tableHeight; j++) {
                const course = dayTable[i][j];
                if (!course) continue;
                // Fill time section
                if (i - rowSize[j] > 0)
                    rows[j].insertCell().colSpan = i - rowSize[j];

                // Build cell
                const cell = createCourseCell(rows[j], course);

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

        // Add day undecided
        if (dayUndecided.length > 0) {
            const row = scheduleTable.tbody.insertRow();
            row.className = 'undecided';
            // Time info
            const empty = div();
            empty.style.display = 'none';
            row.appendChild(empty);
            const cell = row.insertCell();
            cell.colSpan = 2;
            cell.className = 'noSelect';
            // cell.textContent = 'Undecided';
            cell.textContent = '時間未定';
            const daysUndecidedCell = row.insertCell();
            daysUndecidedCell.colSpan = tableWidth;
            for (let i = 0; i < dayUndecided.length; i++) {
                // Build cell
                const course = dayUndecided[i];
                const info = course[0];
                const cell = div(null,
                    span(info.name),
                );
                cell.serialID = info.deptID + '-' + info.sn;
                cell.onclick = cellClick;
                daysUndecidedCell.appendChild(cell);
            }
            // Background
            row.appendChild(div('splitLine'));
        }

        // Row background
        for (let i = 0; i < tableHeight; i++) {
            rows[i].appendChild(div('splitLine'));
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
        const courseFetchData = encodeURIComponent(courseFetchArr.join('&'));

        // fetch data
        window.fetchApi('/search?serial=' + courseFetchData, 'Get course info').then(i => {
            for (const entry of Object.entries(i.data))
                for (const course of entry[1])
                    courseInfo[course.sn] = course;
        });
    };

    this.initPreSchedule = function (response) {
        const scheduleData = savedScheduleData;
        /**@type ScheduleData*/
        const preScheduleData = response.data;
        initTable(preScheduleTable);
        preScheduleTable.caption.textContent = scheduleData.studentId + ' credits: ' + scheduleData.credits;

        // Get table size
        const [tableWidth0, tableHeight0] = timeTxt2IntAndGetTableSize(preScheduleData);
        const tableWidth = Math.max(savedScheduleTableWidth, tableWidth0);
        const tableHeight = Math.max(savedScheduleTableHeight, tableHeight0);
        // Parse data
        const {dayTable, dayUndecided} = parseData2DayTable(tableWidth, tableHeight, scheduleData);
        const {dayTable: dayTablePre, dayUndecided: dayUndecidedPre} = parseData2DayTable(tableWidth, tableHeight, preScheduleData);

        // Create table header
        const rows = createScheduleTable(preScheduleTable, tableWidth, tableHeight);
        const cellTable = new Array(tableHeight);

        // Create table body
        for (let i = 0; i < tableHeight; i++) {
            cellTable[i] = new Array(tableWidth);
            for (let j = 0; j < tableWidth; j++) {
                // Create empty cell
                cellTable[i][j] = rows[i].insertCell();
            }
        }

        // Put course
        for (let i = 0; i < tableWidth; i++) {
            for (let j = 0; j < tableHeight; j++) {
                const course = dayTable[i][j];
                if (course) {
                    // Add section
                    if (course[1].time.length === 3) {
                        const length = course[1].time[2] - course[1].time[1] + 1;
                        for (let k = 0; k < length; k++) {
                            createCourseCellSingle(cellTable[j + k][i], course, false);
                        }
                    }
                }
                const preCourse = dayTablePre[i][j];
                if (preCourse) {
                    // Add section
                    if (preCourse[1].time.length === 3) {
                        const length = preCourse[1].time[2] - preCourse[1].time[1] + 1;
                        for (let k = 0; k < length; k++) {
                            createCourseCellSingle(cellTable[j + k][i], preCourse, true);
                        }
                    }
                }
            }
        }


        // Row background
        for (let i = 0; i < tableHeight; i++) {
            rows[i].appendChild(div('splitLine'));
        }
    }

    this.clear = function () {
        initTable(scheduleTable);
        initTable(preScheduleTable);
    };

    /**
     * @param {HTMLTableElement} table
     */
    function initTable(table) {
        if (table.tHead) table.tHead.innerHTML = '';
        else table.createTHead();
        if (table.tBody) table.tBody.innerHTML = '';
        else table.tBody = table.createTBody();
        if (table.caption) table.caption.textContent = '';
        else table.createCaption();
    }

    /**
     * @param {ScheduleData} scheduleData
     * @return {(number)[]}
     */
    function timeTxt2IntAndGetTableSize(scheduleData) {
        let nightTime = false;
        let holiday = false;
        for (const i of scheduleData.schedule) for (const j of i.info) {
            // parse time
            const time = j.time.split(',');
            const parsedTime = new Int8Array(3);

            parsedTime[0] = parseInt(time[0]);
            if (time.length > 1) {
                parsedTime[1] = time[1] === 'N' ? -1 : parseInt(time[1], 16);
                if (time[1] > 4) parsedTime[1]++;
                else if (parsedTime[1] === -1) parsedTime[1] = 5;
            }
            if (time.length > 2) {
                parsedTime[2] = time[2] === 'N' ? -1 : parseInt(time[2], 16);
                if (time[2] > 4) parsedTime[2]++;
                else if (parsedTime[1] === -1) parsedTime[2] = 5;
            }
            // Make section end equals to section start
            else if (time.length > 1)
                parsedTime[2] = parsedTime[1];

            // Find table width
            if (parsedTime[0] > 4)
                holiday = true;
            if (parsedTime.length > 1 && parsedTime[1] > nightTimeStart || parsedTime.length > 2 && parsedTime[2] > nightTimeStart)
                nightTime = true;

            // Save parsed time
            j.time = parsedTime;
        }
        return [holiday ? 7 : 5, nightTime ? 17 : 11]
    }

    function parseData2DayTable(tableWidth, tableHeight, scheduleData) {
        // Parse schedule
        const dayTable = new Array(tableWidth);
        const dayUndecided = [];
        for (let i = 0; i < 7; i++)
            dayTable[i] = new Array(tableHeight);
        for (const eachCourse of scheduleData.schedule)
            for (const timeLocInfo of eachCourse.info) {
                // Time undecided
                if (timeLocInfo.time[0] === -1) {
                    dayUndecided.push([eachCourse, timeLocInfo]);
                    continue;
                }

                // Add course to timetable
                const k = dayTable[timeLocInfo.time[0]][timeLocInfo.time[1]];
                if (k instanceof Array)
                    k.push(timeLocInfo);
                else
                    dayTable[timeLocInfo.time[0]][timeLocInfo.time[1]] = [eachCourse, timeLocInfo];
            }

        return {dayTable, dayUndecided};
    }

    /**
     * @param {HTMLTableElement} table
     * @param {int} tableWidth
     * @param {int} tableHeight
     * @return {any[]}
     */
    function createScheduleTable(table, tableWidth, tableHeight) {
        const headRow = table.tHead.insertRow();
        headRow.className = 'noSelect';
        for (let i = 0; i < tableWidth + 1; i++) {
            const cell = headRow.insertCell();
            cell.textContent = weekTable[i];
            if (i === 0) cell.colSpan = 2;
        }
        headRow.appendChild(div('background'));

        // Table time cell
        const rows = new Array(tableHeight);
        for (let i = 0; i < tableHeight; i++) {
            rows[i] = table.tBody.insertRow();
            const index = rows[i].insertCell();
            index.textContent = timeTable[i][0];
            index.className = 'noSelect';
            const time = rows[i].insertCell();
            if (timeTable[i][1]) time.textContent = timeTable[i][1];
            time.className = 'noSelect';
        }
        return rows;
    }

    /**
     * @param row Table row
     * @param {[info, ...time]} course
     * @return {HTMLTableCellElement}
     */
    function createCourseCell(row, course) {
        const cell = row.insertCell();
        const info = course[0];
        cell.className = 'activateCell';
        cell.serialID = info.deptID + '-' + info.sn;
        cell.onclick = cellClick;
        courseInfo[cell.serialID] = null;

        cell.appendChild(span(info.name));
        for (let k = 1; k < course.length; k++) {
            const roomNameElement = span(course[k].room);
            // Show roomName or not
            if (!showClassRoom)
                roomNameElement.style.display = 'none';
            cell.appendChild(roomNameElement);
            roomNameElements.push(roomNameElement);
        }
        return cell;
    }

    function createCourseCellSingle(cell, course, pre) {
        cell.className = 'activate';
        const info = course[0];
        const subCell = div(pre ? 'pre' : 'sure', {serialID: info.deptID + '-' + info.sn, onclick: cellClick},
            span('[' + info.deptID + ']' + info.sn + ' '),
            span(info.name),
        );
        if (courseInfo[cell.serialID] === undefined)
            courseInfo[cell.serialID] = null;

        // Add room
        for (let k = 1; k < course.length; k++) {
            if (!course[k].room) continue;
            const roomNameElement = span(course[k].room, 'room');
            // Show roomName or not
            if (!showClassRoom)
                roomNameElement.style.display = 'none';
            subCell.appendChild(roomNameElement);
            roomNameElements.push(roomNameElement);
        }

        cell.appendChild(subCell);
    }

    this.showRoomName = function (show) {
        const display = show ? null : 'none';
        for (const roomNameElement of roomNameElements)
            roomNameElement.style.display = display;
    }

    this.setTableShow = function (showPre) {
        if (showPre) {
            scheduleTable.style.display = 'none';
            preScheduleTable.style.display = 'block';
        } else {
            scheduleTable.style.display = 'block';
            preScheduleTable.style.display = 'none';
        }
    }
}