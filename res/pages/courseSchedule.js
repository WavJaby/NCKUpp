'use strict';

import {a, button, checkboxWithName, div, h1, h2, mountableStylesheet, p, span, table} from '../lib/domHelper_v003.min.js';
import {checkLocalStorage, courseDataTimeToString, fetchApi, parseRawCourseData, timeParse} from '../lib/lib.js';
import PopupWindow from '../popupWindow.js';

// static
const weekTable = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
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

/**
 * @param {QueryRouter} router
 * @param {Signal} loginState
 * @return {HTMLDivElement}
 */
export default function (router, loginState) {
	console.log('Course schedule Init');
	// static element
	const styles = mountableStylesheet('./res/pages/courseSchedule.css');
	let /**@type{PageStorage}*/pageStorage;
	const showClassroomCheckbox = checkboxWithName(null, '顯示教室', false);
	const showPreScheduleCheckbox = checkboxWithName(null, '顯示預排', false);
	const scheduleTableInfo = span(null, 'scheduleTableInfo');
	const windowRoot = div();
	const scheduleTable = new ScheduleTable(windowRoot);
	const downloadScheduleButton = button('downloadScheduleBtn', '下載課表');
	const downloadScheduleButtonHide = a(null, null, null, {style: 'display:none'});
	let scheduleLoading = false, scheduleLoadingQueue = false;

	showClassroomCheckbox.input.onchange = () => scheduleTable.showRoomName(showClassroomCheckbox.input.checked);
	showPreScheduleCheckbox.input.onchange = () => scheduleTable.setTableShow(showPreScheduleCheckbox.input.checked);
	scheduleTable.showRoomName(showClassroomCheckbox.input.checked);
	scheduleTable.setTableShow(showPreScheduleCheckbox.input.checked);

	downloadScheduleButton.onclick = function () {
		let table;
		// Get display table
		for (const tableChild of scheduleTable.table.children) {
			if (!tableChild.style.display) {
				table = tableChild;
				break;
			}
		}

		elementToImage(table, 'courseSchedule capture',
			['static.css', 'courseSchedule.css']).then(imageData => {
			downloadScheduleButtonHide.href = imageData;

			const clickEvent = new MouseEvent('click', {
				view: window,
				bubbles: true,
				cancelable: false
			});
			downloadScheduleButtonHide.dispatchEvent(clickEvent);
		});
	}

	function onRender() {
		console.log('Course schedule Render');
		styles.mount();
		pageStorage = router.getPageStorage(this);

		if (!checkLocalStorage()) {
			window.messageAlert.addError('請允許內部儲存', '開啟內部儲存來使用更多功能');
		} else {
			loadLastScheduleData();
		}
	}

	function onPageOpen() {
		console.log('Course schedule Open');
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
			// Loading, add queue
			if (scheduleLoading) {
				scheduleLoadingQueue = true;
				return;
			}
			// Update data
			scheduleLoading = true;
			scheduleLoadingQueue = false;
			if (!scheduleTable.dataReady())
				downloadScheduleButton.classList.remove('show');
			scheduleTable.clearScheduleData();
			fetchApi('/courseSchedule', 'Get schedule').then(response => {
				if (!response.success || !response.data) {
					scheduleLoading = false;
					return;
				}
				// Parse normal schedule
				const scheduleData = response.data;
				pageStorage['savedCurrentSchedule'] = scheduleData;
				scheduleTable.setScheduleData(scheduleData);
				updateScheduleInfo(scheduleData);
				checkScheduleDataAndRender(false);
			});
			fetchApi('/courseSchedule?pre=true', 'Get pre schedule').then(response => {
				if (!response.success || !response.data) {
					scheduleLoading = false;
					return;
				}
				// Parse pre schedule
				const preScheduleData = response.data;
				pageStorage['savedCurrentPreSchedule'] = preScheduleData;
				scheduleTable.setPreScheduleData(preScheduleData);
				checkScheduleDataAndRender(false);
			});
		} else {
			if (state) {
				if (scheduleTable.dataReady())
					window.messageAlert.addInfo('登入後即可更新課表資料', '', 3000);
				else
					window.askForLoginAlert();
			}
		}
	}

	function updateScheduleInfo(scheduleData) {
		const semesterInfo = scheduleData.semesterInfo;
		const studentInfo = scheduleData.studentInfo;
		downloadScheduleButtonHide.download = semesterInfo.year + '學年_第' + semesterInfo.sem + '學期_課表';
		scheduleTableInfo.textContent = studentInfo.id + ' 學分: ' + scheduleData.credits;
	}

	function checkScheduleDataAndRender(firstRender) {
		if (!scheduleTable.dataReady())
			return;

		scheduleTable.renderTable();
		downloadScheduleButton.classList.add('show');
		pageStorage.storageSave();

		// Try load course detail from page storage
		const courseDetailRaw = pageStorage['savedCurrentCourseDetailRaw'];
		if (firstRender && courseDetailRaw) {
			scheduleTable.setCourseDetailData(parseCourseDetail(courseDetailRaw));
			scheduleLoadingDone();
		} else {
			// Fetch course data
			fetchApi('/search?serial=' + scheduleTable.getSearchQuery(), 'Get course info').then(response => {
				// Update course data
				if (response.success && response.data) {
					pageStorage['savedCurrentCourseDetailRaw'] = response.data;
					pageStorage.storageSave();
					scheduleTable.setCourseDetailData(parseCourseDetail(response.data));
				}
				scheduleLoadingDone();
			});
		}
	}

	function scheduleLoadingDone() {
		// Reset loading
		scheduleLoading = false;
		// Fetch again if queue
		if (scheduleLoadingQueue) {
			scheduleLoadingQueue = false;
			onLoginState(loginState.state);
		}
	}

	function parseCourseDetail(courseDataRaw) {
		const courseDetail = {};
		for (const course of courseDataRaw)
			courseDetail[course.sn] = parseRawCourseData(course, null);
		return courseDetail;
	}

	function loadLastScheduleData() {
		const scheduleData = pageStorage['savedCurrentSchedule'];
		const preScheduleData = pageStorage['savedCurrentPreSchedule'];
		if (scheduleData && preScheduleData) {
			scheduleLoading = true;
			scheduleLoadingQueue = false;
			scheduleTable.setScheduleData(scheduleData);
			updateScheduleInfo(scheduleData);
			scheduleTable.setPreScheduleData(preScheduleData);
			checkScheduleDataAndRender(true);
		}
	}

	return div('courseSchedule',
		{onRender, onPageOpen, onPageClose},
		h1('學期課表', 'title'),
		div('scheduleTab'),
		div('options',
			showClassroomCheckbox,
			showPreScheduleCheckbox,
		),
		scheduleTableInfo,
		scheduleTable.table,
		downloadScheduleButton,
		windowRoot,
	);
};

/**
 * @typedef {[ScheduleCourse, ...ScheduleCourseTimeInfo]} CourseWithTime
 */

/**
 * @typedef ScheduleCourseTimeInfo
 * @property {string} type
 * @property {string} roomID
 * @property {string} room
 * @property {string} time
 * @property {int[]} [parsedTime]
 */

/**
 * @typedef ScheduleCourse
 * @property {string} deptID
 * @property {string} sn
 * @property {string} name
 * @property {boolean} required
 * @property {float} credits
 * @property {string} [delete] Delete action key
 * @property {boolean} [pre] Is pre-schedule
 */

/**
 * @typedef ScheduleData
 * @property {string} studentId
 * @property {int} year
 * @property {int} semester
 * @property {float} credits
 * @property {ScheduleCourse[]} schedule
 */

function ScheduleTable(windowRoot) {
	const courseInfoWindow = new PopupWindow({root: windowRoot});
	const scheduleTable = table('courseScheduleTable', {'cellPadding': 0});
	const preScheduleTable = table('courseScheduleTable pre', {'cellPadding': 0});
	preScheduleTable.style.display = 'none';
	this.table = div('tableContainer', scheduleTable, preScheduleTable);
	initTable(scheduleTable);
	initTable(preScheduleTable);

	let /**@type ?ScheduleData*/ scheduleData = null;
	let /**@type ?ScheduleData*/ preScheduleData = null;

	let /**@type{Object.<string, CourseData>}*/courseDetail = {};
	let tableWidth, tableHeight;
	let courseSameCell = {};
	let preCourseRemoveKey = {};

	this.setScheduleData = function (data) {
		scheduleData = data;
		// Get table size
		[tableWidth, tableHeight] = timeTxt2IntAndGetTableSize(scheduleData);
	};

	this.setPreScheduleData = function (data) {
		preScheduleData = data;

		for (const course of preScheduleData.schedule)
			if (course.delete)
				preCourseRemoveKey[course.deptID + '-' + course.sn] = course.delete;
	};

	this.dataReady = function () {
		return scheduleData != null && preScheduleData != null;
	};

	this.renderTable = function () {
		initScheduleTable();
		initPreScheduleTable();
	};

	this.getSearchQuery = function () {
		// get course info
		const courseDept = {};
		for (const serialID in courseDetail) {
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
		return encodeURIComponent(courseFetchArr.join('&'));
	};

	this.setCourseDetailData = function (courseDetailData) {
		courseDetail = courseDetailData;
	};

	this.clear = function () {
		dataReset();
		initTable(scheduleTable);
		initTable(preScheduleTable);
	};

	this.clearScheduleData = function () {
		scheduleData = null;
		preScheduleData = null;
	}

	function dataReset() {
		scheduleData = null;
		preScheduleData = null;
		courseSameCell = {};
		courseDetail = {};
		preCourseRemoveKey = {};
	}

	function setAndRenderPreScheduleData(response) {
		preScheduleData = response.data;
		initPreScheduleTable();
	}

	function cellClick() {
		const data = courseDetail[this.serialID];
		if (!data)
			return;

		const locationButtons = [];
		if (data.time)
			for (let time of data.time) {
				let timeStr = courseDataTimeToString(time);
				locationButtons.push(button(null, timeStr + ' ' + time.classroomName, openCourseLocation, {locationQuery: time.deptID + ',' + time.classroomID}));
			}
		courseInfoWindow.setWindowContent(div('courseInfo',
			preCourseRemoveKey[this.serialID] && button('delete', '刪除', removePreCourse, {serialID: this.serialID}),
			h2(data.serialNumber + ' ' + data.courseName),
			data.instructors.map(i => span(i + ' ')),
			p(data.courseNote),
			button(null, 'moodle', openCourseMoodle, {moodleQuery: data.moodle}),
			locationButtons,
			!data.preferenceEnter ? null :
				button(null, '加入志願', sendCosData, {courseData: data, key: data.preferenceEnter}),
			!data.addCourse ? null :
				button(null, '單科加選', sendCosData, {courseData: data, key: data.addCourse}),
		));
		courseInfoWindow.windowOpen();
	}

	function openCourseLocation() {
		fetchApi('/extract?location=' + this.locationQuery).then(i => {
			if (i.data && i.success)
				window.open(i.data.url, '_blank');
		});
	}

	function openCourseMoodle() {
		fetchApi('/extract?moodle=' + this.moodleQuery).then(i => {
			if (i.data && i.success)
				window.open(i.data.url, '_blank');
		});
	}

	/**@this{{courseData: CourseData, key: string}}*/
	function sendCosData() {
		const title = this.courseData.courseName + ' 加入';
		fetchApi(`/courseFuncBtn?cosdata=${encodeURIComponent(this.key)}`, 'Send course data').then(i => {
			if (i.success)
				window.messageAlert.addSuccess(title + '成功', i.msg, 5000);
			else {
				const d = div();
				d.innerHTML = i.msg;
				window.messageAlert.addErrorElement(title + '失敗', d, 20000);
			}
		});
	}

	function removePreCourse() {
		const key = preCourseRemoveKey[this.serialID];
		const detail = courseDetail[this.serialID];

		const suffix = '[' + detail.serialNumber + '] ' + detail.courseName;
		const deleteConform = confirm('是否要刪除 ' + suffix);
		if (!deleteConform)
			return;

		const title = '預排刪除 ' + suffix;
		fetchApi('/courseSchedule?pre=true', 'Delete pre schedule',
			{method: 'post', body: 'action=delete&info=' + key}
		).then(response => {
			if (response.success) {
				window.messageAlert.addSuccess(title, response.msg, 5000);
				fetchApi('/courseSchedule?pre=true', 'Get pre schedule').then(setAndRenderPreScheduleData);
				courseInfoWindow.windowClose();
			} else
				window.messageAlert.addError(title, response.msg, 5000);
		});
	}

	function initScheduleTable() {
		// Parse data
		const {dayTable, dayUndecided} = parseData2DayTable(tableWidth, tableHeight, scheduleData);
		// console.log(dayTable);

		// Create table
		initTable(scheduleTable);
		const rows = createScheduleTable(scheduleTable, dayUndecided.length > 0, tableWidth, tableHeight);

		// Add course cell
		const rowSize = new Int32Array(tableHeight);
		for (let i = 0; i < tableWidth; i++) {
			for (let j = 0; j < tableHeight; j++) {
				const dayCourse = dayTable[i][j];
				if (!dayCourse) continue;
				const course = dayCourse[0];
				// Fill time section
				if (i - rowSize[j] > 0)
					rows[j].insertCell().colSpan = i - rowSize[j];

				// Build cell
				const cell = rows[j].insertCell();
				createCourseCell(cell, course, false).classList.add('fullHeight');

				// Add space
				if (course[1].parsedTime.length === 3) {
					const length = course[1].parsedTime[2] - course[1].parsedTime[1] + 1;
					cell.rowSpan = length * 2 - 1;
					rowSize[j] = i + 1;
					for (let k = 1; k < length; k++) {
						// fill space
						if (i - rowSize[j + k] > 0)
							rows[j + k].insertCell().colSpan = i - rowSize[j + k];
						rowSize[j + k] = i + 1;
					}
				} else if (course[1].parsedTime.length === 2)
					rowSize[j] = i + 1;
			}
		}

		createCourseUndecided(dayUndecided, scheduleTable, false);
	}

	function initPreScheduleTable() {
		// Get table size
		let [preTableWidth, preTableHeight] = timeTxt2IntAndGetTableSize(preScheduleData);
		preTableWidth = Math.max(tableWidth, preTableWidth);
		preTableHeight = Math.max(tableHeight, preTableHeight);
		// Parse data
		const {dayTable, dayUndecided} = parseData2DayTable(preTableWidth, preTableHeight, scheduleData);
		const {
			dayTable: dayTablePre,
			dayUndecided: dayUndecidedPre
		} = parseData2DayTable(preTableWidth, preTableHeight, preScheduleData);

		// Bind table
		for (const courseData of dayUndecidedPre) {
			courseData[0].pre = true;
			dayUndecided.push(courseData);
		}
		for (let i = 0; i < preTableWidth; i++) {
			for (let j = 0; j < preTableHeight; j++) {
				const courseTimeDataPre = dayTablePre[i][j];
				if (!courseTimeDataPre)
					continue;
				let courseTimeData = dayTable[i][j];
				if (!courseTimeData) courseTimeData = dayTable[i][j] = [];
				for (const courseData of courseTimeDataPre) {
					courseData[0].pre = true;
					courseTimeData.push(courseData);
				}
			}
		}

		// Extract course data
		for (let i = 0; i < preTableWidth; i++) {
			for (let j = 0; j < preTableHeight; j++) {
				const courseTimeData = dayTable[i][j];
				if (!courseTimeData)
					continue;
				// Course in section
				for (const courseData of courseTimeData) {
					const courseTime = courseData[1].parsedTime;
					if (j !== courseTime[1])
						continue;
					const length = courseTime[2] - courseTime[1] + 1;
					// Fill time
					for (let k = 1; k < length; k++) {
						let courseTimeDataNext = dayTable[i][j + k];
						if (!courseTimeDataNext) courseTimeDataNext = dayTable[i][j + k] = [];
						courseTimeDataNext.push(courseData);
					}
				}
			}
		}


		// Create table
		initTable(preScheduleTable);
		const rows = createScheduleTable(preScheduleTable, dayUndecided.length > 0, preTableWidth, preTableHeight);
		/**@type{HTMLTableCellElement[][]}*/
		const cellTable = new Array(preTableWidth);
		// Create table body
		for (let i = 0; i < preTableWidth; i++) {
			cellTable[i] = new Array(preTableHeight);
			for (let j = 0; j < preTableHeight; j++) {
				// Create empty cell
				cellTable[i][j] = rows[j].insertCell();
			}
		}

		// Put course
		for (let dayOfWeek = 0; dayOfWeek < preTableWidth; dayOfWeek++) {
			for (let sectionTime = 0; sectionTime < preTableHeight; sectionTime++) {
				const sectionCourseListCol = dayTable[dayOfWeek];
				const sectionCourseList = sectionCourseListCol[sectionTime];
				if (!sectionCourseList)
					continue;

				for (let sectionCourse of sectionCourseList) {
					const courseTime = sectionCourse[1].parsedTime;
					if (sectionTime !== courseTime[1])
						continue;
					const cellTableCol = cellTable[courseTime[0]];
					const parentCell = cellTableCol[courseTime[1]];
					const timeLength = courseTime[2] - courseTime[1] + 1;
					createCourseCell(parentCell, sectionCourse, true);
					for (let i = 1; i < timeLength; i++) {
						const sectionCourseListNext = sectionCourseListCol[sectionTime + i];
						const parentCellNext = cellTableCol[courseTime[1] + i];
						if (sectionCourseList.length === 1 &&
							(!sectionCourseListNext || sectionCourseListNext.length === 1 && sectionCourseListNext[0] === sectionCourse)) {
							parentCell.rowSpan += 2;
							parentCellNext.parentElement.removeChild(parentCellNext);
						} else
							createCourseCell(parentCellNext, sectionCourse, true);
					}
				}
			}
		}

		// Full height when
		for (let i = 0; i < preTableWidth; i++) {
			for (let j = 0; j < preTableHeight; j++)
				if (cellTable[i][j].childElementCount === 1)
					cellTable[i][j].firstElementChild.classList.add('fullHeight');
		}

		createCourseUndecided(dayUndecidedPre, preScheduleTable, true);
	}

	function courseCellHoverStart() {
		const /**@type{HTMLDivElement[]}*/ cells = courseSameCell[this.serialID];
		if (!cells)
			return;
		for (let cell of cells) {
			cell.classList.add('hover');
		}
	}

	function courseCellHoverEnd() {
		const /**@type{HTMLDivElement[]}*/ cells = courseSameCell[this.serialID];
		if (!cells)
			return;
		for (let cell of cells) {
			cell.classList.remove('hover');
		}
	}

	/**
	 * @param {HTMLTableElement} table
	 */
	function initTable(table) {
		if (table.tHead)
			while (table.tHead.firstChild) table.tHead.removeChild(table.tHead.firstChild);
		else table.createTHead();
		if (table.tBody)
			while (table.tBody.firstChild) table.tBody.removeChild(table.tBody.firstChild);
		else table.tBody = table.createTBody();
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
			const parsedTime = timeParse(j.time);

			// Find table width
			if (parsedTime[0] > 4)
				holiday = true;
			if (parsedTime.length > 1 && parsedTime[1] > nightTimeStart || parsedTime.length > 2 && parsedTime[2] > nightTimeStart)
				nightTime = true;

			// Save parsed time
			j.parsedTime = parsedTime;
		}
		return [holiday ? 7 : 5, nightTime ? 17 : 11]
	}

	/**
	 * @param {int} tableWidth
	 * @param {int} tableHeight
	 * @param {ScheduleData} scheduleData
	 * @return {{dayTable: [CourseWithTime][][], dayUndecided: CourseWithTime[]}}
	 */
	function parseData2DayTable(tableWidth, tableHeight, scheduleData) {
		// Parse schedule
		const dayTable = new Array(tableWidth);
		const dayUndecided = [];
		for (let i = 0; i < tableWidth; i++)
			dayTable[i] = new Array(tableHeight);
		for (const eachCourse of scheduleData.schedule) {
			const serialID = eachCourse.deptID + '-' + eachCourse.sn;
			if (courseDetail[serialID] === undefined)
				courseDetail[serialID] = null;
			for (const timeLocInfo of eachCourse.info) {
				// Time undecided
				if (timeLocInfo.parsedTime[0] === -1) {
					dayUndecided.push([eachCourse, timeLocInfo]);
					continue;
				}

				// Add course to timetable
				let section = dayTable[timeLocInfo.parsedTime[0]][timeLocInfo.parsedTime[1]];
				if (!section)
					section = dayTable[timeLocInfo.parsedTime[0]][timeLocInfo.parsedTime[1]] = {};

				let sectionCourse = section[eachCourse.deptID + '-' + eachCourse.sn];
				if (!sectionCourse)
					sectionCourse = section[eachCourse.deptID + '-' + eachCourse.sn] = [eachCourse, timeLocInfo];
				else
					sectionCourse.push(timeLocInfo);
			}
		}
		for (let i = 0; i < tableWidth; i++)
			for (let j = 0; j < tableHeight; j++)
				if (dayTable[i][j])
					dayTable[i][j] = Object.values(dayTable[i][j]);

		return {dayTable: dayTable, dayUndecided: dayUndecided};
	}

	/**
	 * @param {HTMLTableElement} table
	 * @param {boolean} haveDayUndecided
	 * @param {int} tableWidth
	 * @param {int} tableHeight
	 * @return {HTMLTableRowElement[]}
	 */
	function createScheduleTable(table, haveDayUndecided, tableWidth, tableHeight) {
		const headRow = table.tHead.insertRow();
		headRow.className = 'noSelect';
		for (let i = 0; i < tableWidth + 1; i++) {
			const cell = headRow.insertCell();
			cell.textContent = weekTable[i];
			if (i === 0) cell.colSpan = 3;
		}

		// Table time cell
		const rows = new Array(tableHeight);
		for (let i = 0; i < tableHeight; i++) {
			rows[i] = table.tBody.insertRow();
			rows[i].className = 'timeRow';
			const index = rows[i].insertCell();
			index.className = 'timeCode noSelect';
			index.appendChild(span(timeTable[i][0]));

			const time = rows[i].insertCell();
			time.className = 'time noSelect';
			if (timeTable[i][1])
				time.appendChild(span(timeTable[i][1]));
			// time.appendChild(div('border'));

			// Empty row for split line
			rows[i].insertCell();

			if (i + 1 < tableHeight || haveDayUndecided) {
				const splitLine = table.tBody.insertRow();
				splitLine.className = 'splitLine';
				splitLine.insertCell().colSpan = 2;
				splitLine.insertCell().colSpan = 1 + tableWidth;
			}
		}
		return rows;
	}

	/**
	 * @param {HTMLTableCellElement} parentCell
	 * @param {CourseWithTime} courseWithTime
	 * @param {boolean} showSerialId
	 * @return {HTMLDivElement}
	 */
	function createCourseCell(parentCell, courseWithTime, showSerialId) {
		parentCell.className = 'activate';
		const courseInfo = courseWithTime[0];
		const serialID = courseInfo.deptID + '-' + courseInfo.sn;

		const courseCell = div(courseInfo.pre ? 'pre' : 'sure', {serialID: serialID, onclick: cellClick},
			showSerialId ? span(courseInfo.deptID + '-' + courseInfo.sn + ' ') : null,
			span(courseInfo.name),
		);
		if (showSerialId) {
			(courseSameCell[serialID] || (courseSameCell[serialID] = [])).push(courseCell);
			courseCell.onmouseenter = courseCellHoverStart;
			courseCell.onmouseleave = courseCellHoverEnd;
		}

		// Add room
		for (let k = 1; k < courseWithTime.length; k++) {
			if (!courseWithTime[k].room) continue;
			courseCell.appendChild(span(courseWithTime[k].room, 'room'));
		}
		parentCell.appendChild(courseCell);
		return courseCell;
	}

	function createCourseUndecided(dayUndecided, table, pre) {
		// Add day undecided
		if (dayUndecided.length > 0) {
			const row = table.tBody.insertRow();
			row.className = 'timeRow undecided';
			const timeCode = row.insertCell();
			timeCode.className = 'timeCode noSelect';
			// timeCode.appendChild(span('#'));
			const time = row.insertCell();
			time.colSpan = 2;
			time.className = 'time noSelect';
			// cell.textContent = 'Undecided';
			time.textContent = '未定';
			const daysUndecidedCell = row.insertCell();
			daysUndecidedCell.colSpan = tableWidth;
			daysUndecidedCell.className = 'activate';
			for (let i = 0; i < dayUndecided.length; i++) {
				// Build cell
				const course = dayUndecided[i];
				createCourseCell(daysUndecidedCell, course, pre);
			}
		}
	}

	this.showRoomName = function (show) {
		if (show) {
			scheduleTable.tBody.classList.add('showRoom');
			preScheduleTable.tBody.classList.add('showRoom');
		} else {
			scheduleTable.tBody.classList.remove('showRoom');
			preScheduleTable.tBody.classList.remove('showRoom');
		}
	}

	this.setTableShow = function (showPre) {
		if (showPre) {
			scheduleTable.style.display = 'none';
			preScheduleTable.style.display = '';
		} else {
			scheduleTable.style.display = '';
			preScheduleTable.style.display = 'none';
		}
	}
}

/**
 * @param {HTMLElement} element
 * @param {string} rootClassName
 * @param loadedStyleSheet
 */
async function elementToImage(element, rootClassName, loadedStyleSheet) {
	const elementWidth = element.offsetWidth || 100;
	const elementHeight = element.offsetHeight || 100;

	const bound = element.getBoundingClientRect && element.getBoundingClientRect();
	const width = bound && bound.width ? Math.ceil(bound.width) : elementWidth + 1;
	const height = bound && bound.height ? Math.ceil(bound.height) : elementHeight + 1;

	// Read style
	let cssStyles = '';
	for (let i = 0; i < document.styleSheets.length; i++) {
		const styleSheet = document.styleSheets[i];
		let find = false;
		for (const i of loadedStyleSheet) {
			if (styleSheet.href.endsWith(i)) {
				find = true;
				break;
			}
		}
		if (!find)
			continue;

		let styles = styleSheet.cssRules || styleSheet.rules;
		for (const item in styles) {
			if (styles[item].cssText)
				cssStyles += styles[item].cssText;
		}
	}

	// Read font
	const embedFont = await loadAndPackFont('https://fonts.googleapis.com/css2?family=JetBrains+Mono');

	// Create svg
	const svgBody = '<svg xmlns="http://www.w3.org/2000/svg" width="' + width + '" height="' + height + '">' +
		'<defs><style>' + embedFont + cssStyles + 'body{background:none;z-index:-1;margin: 0;}</style></defs>' +
		'<foreignObject width="100%" height="100%">' +
		'<body xmlns="http://www.w3.org/1999/xhtml"><div style="width:100%;height:100%" class="' + rootClassName + '">' +
		element.outerHTML +
		'</div></body>' +
		'</foreignObject></svg>';

	const canvas = document.createElement('canvas');
	const ctx = canvas.getContext('2d');
	canvas.width = width;
	canvas.height = height;

	// Make svg to data url
	const svgBlob = new Blob([svgBody], {type: 'image/svg+xml;charset=utf-8'});
	const reader = new FileReader();
	reader.readAsDataURL(svgBlob);
	await new Promise(resolve => reader.onloadend = resolve);
	const svgObjectUrl = reader.result.toString();

	// To image
	const tempImg = new Image();
	const imageLoad = new Promise(resolve => tempImg.onload = resolve);
	tempImg.src = svgObjectUrl;
	await imageLoad;
	ctx.drawImage(tempImg, 0, 0);

	return canvas.toDataURL('image/png');

	async function loadAndPackFont(url) {
		const URL_REGEX = /src: ?url\(['"]?([^'"]+?)['"]?\)/g;
		const font = await fetch(url).then(i => i.text());
		let fontFile, lastIndex = 0;
		const tasks = [];
		const fontEmbedTextBuilder = [];
		while ((fontFile = URL_REGEX.exec(font))) {
			fontEmbedTextBuilder.push(font.substring(lastIndex, fontFile.index + 9));
			const url = fontFile[1];
			const reader = new FileReader();
			const index = fontEmbedTextBuilder.length;
			tasks.push(new Promise(resolve => reader.onloadend = () => resolve([index, reader.result])));
			fetch(url).then(i => i.blob()).then(i => reader.readAsDataURL(i));
			fontEmbedTextBuilder.push(null);
			lastIndex = fontFile.index + url.length + 9;
		}
		// Resolve tasks
		for (const task of tasks) {
			const [index, result] = await task;
			fontEmbedTextBuilder[index] = '"' + result + '"';
		}
		fontEmbedTextBuilder.push(font.substring(lastIndex, font.length));
		return fontEmbedTextBuilder.join('');
	}
}