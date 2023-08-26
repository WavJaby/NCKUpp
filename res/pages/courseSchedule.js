'use strict';

import {a, button, checkboxWithName, div, h1, h2, mountableStylesheet, p, span, table, td} from '../domHelper_v001.min.js';
import {courseDataTimeToString, fetchApi, parseRawCourseData, timeParse} from '../lib.js';
import PopupWindow from '../popupWindow.js';

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

/**
 * @param {QueryRouter} router
 * @param {Signal} loginState
 * @return {HTMLDivElement}
 */
export default function (router, loginState) {
	console.log('Course schedule Init');
	// static element
	const styles = mountableStylesheet('./res/pages/courseSchedule.css');
	const showClassroomCheckbox = checkboxWithName(null, '顯示教室', false);
	const showPreScheduleCheckbox = checkboxWithName(null, '顯示預排科目', false);
	const scheduleTableInfo = span(null, 'scheduleTableInfo');
	const windowRoot = div();
	const scheduleTable = new ScheduleTable(windowRoot);
	const downloadScheduleButton = a('下載課表', null, 'downloadScheduleBtn');
	let scheduleLoading = false, scheduleLoadingQueue = false;

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
			// Loading, add queue
			if (scheduleLoading) {
				scheduleLoadingQueue = true;
				return;
			}
			// Update data
			scheduleLoading = true;
			scheduleLoadingQueue = false;
			downloadScheduleButton.classList.remove('show');
			scheduleTable.dataReset();
			fetchApi('/courseSchedule', 'Get schedule').then(response => {
				// Parse normal schedule
				scheduleTable.setScheduleData(response);
				if (scheduleTable.dataReady()) {
					scheduleTable.renderTable();
					updateCourseInfo();
				}
				const scheduleData = response.data;
				downloadScheduleButton.download = scheduleData.year + '學年_第' + scheduleData.semester + '學期_課表';
				scheduleTableInfo.textContent = scheduleData.studentId + ' credits: ' + scheduleData.credits;
			});
			fetchApi('/courseSchedule?pre=true', 'Get pre schedule').then(response => {
				// Parse normal schedule
				scheduleTable.setPreScheduleData(response);
				if (scheduleTable.dataReady()) {
					scheduleTable.renderTable();
					updateCourseInfo();
				}
			});
		} else {
			if (state)
				window.askForLoginAlert();
			scheduleTable.clear();
		}
	}

	function updateCourseInfo() {
		elementToImage(scheduleTable.table.firstElementChild, 'courseSchedule capture',
			['static.css', 'courseSchedule.css']).then(imageData => {
			downloadScheduleButton.href = imageData;
			downloadScheduleButton.classList.add('show');
		});
		scheduleTable.fetchCourseInfo().then(() => {
			// Reset loading
			scheduleLoading = false;
			// Fetch again if queue
			if (scheduleLoadingQueue) {
				scheduleLoadingQueue = false;
				onLoginState(loginState.state);
			}
		});
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
 * @typedef ScheduleCourseTimeInfo
 * @property {string} type
 * @property {string} time
 * @property {string} roomID
 * @property {string} room
 * @property {int[]} time
 */

/**
 * @typedef ScheduleCourse
 * @property {string} deptID
 * @property {string} sn
 * @property {string} name
 * @property {boolean} required
 * @property {float} credits
 * @property {string} [delete] Delete action key
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

	let /**@type ?ScheduleData*/ scheduleData = null;
	let /**@type ?ScheduleData*/ preScheduleData = null;

	const roomNameElements = [];
	let /**@type{Object.<string, CourseData>}*/courseDetail = {};
	let tableWidth, tableHeight;
	let courseSameCell = {};
	let preCourseRemoveKey = {};
	let showClassRoom = false;

	this.setScheduleData = function (response) {
		scheduleData = response.data;
		// Get table size
		[tableWidth, tableHeight] = timeTxt2IntAndGetTableSize(scheduleData);
	};

	this.setPreScheduleData = function (response) {
		preScheduleData = response.data;
	};

	this.dataReady = function () {
		return scheduleData != null && preScheduleData != null;
	};

	this.dataReset = function () {
		scheduleData = null;
		preScheduleData = null;
		roomNameElements.length = 0;
		courseDetail = {};
		courseSameCell = {};
		preCourseRemoveKey = {};
	};

	this.renderTable = function () {
		initScheduleTable();
		initPreScheduleTable();

		for (const course of preScheduleData.schedule)
			if (course.delete)
				preCourseRemoveKey[course.deptID + '-' + course.sn] = course.delete;
	};

	function setAndRenderPreScheduleData(response) {
		preScheduleData = response.data;
		initPreScheduleTable();
	}

	function cellClick() {
		const detail = courseDetail[this.serialID];
		if (!detail)
			return;

		const locationButtons = [];
		if (detail.time)
			for (let time of detail.time) {
				let timeStr = courseDataTimeToString(time);
				locationButtons.push(button(null, timeStr + ' ' + time.classroomName, openCourseLocation, {locationQuery: time.deptID + ',' + time.classroomID}));
			}
		courseInfoWindow.setWindowContent(div('courseInfo',
			preCourseRemoveKey[this.serialID] && button('delete', '刪除', removePreCourse, {serialID: this.serialID}),
			h2(detail.serialNumber + ' ' + detail.courseName),
			detail.instructors.map(i => span(i + ' ')),
			p(detail.courseNote),
			button(null, 'moodle', openCourseMoodle, {moodleQuery: detail.m}),
			locationButtons,
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

		// Create table
		initTable(scheduleTable);
		const rows = createScheduleTable(scheduleTable, tableWidth, tableHeight);

		// Add course cell
		const rowSize = new Int32Array(tableHeight);
		for (let i = 0; i < tableWidth; i++) {
			for (let j = 0; j < tableHeight; j++) {
				const dayCourse = dayTable[i][j];
				if (!dayCourse) continue;
				const course = Object.values(dayCourse)[0];
				// Fill time section
				if (i - rowSize[j] > 0)
					rows[j].insertCell().colSpan = i - rowSize[j];

				// Build cell
				const cell = rows[j].insertCell();
				createCourseCell(cell, course);

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
			row.appendChild(td(null, 'splitLine'));
		}

		// Row background
		for (let i = 0; i < tableHeight; i++) {
			rows[i].appendChild(td(null, 'splitLine'));
		}
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

		// Create table
		initTable(preScheduleTable);
		const rows = createScheduleTable(preScheduleTable, preTableWidth, preTableHeight);
		const cellTable = new Array(preTableHeight);

		// Create table body
		for (let i = 0; i < preTableHeight; i++) {
			cellTable[i] = new Array(preTableWidth);
			for (let j = 0; j < preTableWidth; j++) {
				// Create empty cell
				cellTable[i][j] = rows[i].insertCell();
			}
		}

		// Put course
		for (let i = 0; i < preTableWidth; i++) {
			for (let j = 0; j < preTableHeight; j++) {
				const dayCourse = dayTable[i][j];
				if (dayCourse)
					createCourseCellSingleList(cellTable, Object.values(dayCourse), false);
				const dayPreCourse = dayTablePre[i][j];
				if (dayPreCourse)
					createCourseCellSingleList(cellTable, Object.values(dayPreCourse), true);
			}
		}

		// Row background
		for (let i = 0; i < preTableHeight; i++) {
			rows[i].appendChild(td(null, 'splitLine'));

			for (let j = 0; j < preTableWidth; j++)
				if (cellTable[i][j].childElementCount === 1)
					cellTable[i][j].firstElementChild.classList.add('fullHeight');
		}
	}

	this.fetchCourseInfo = function () {
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
		const courseFetchData = encodeURIComponent(courseFetchArr.join('&'));

		// fetch data
		return fetchApi('/search?serial=' + courseFetchData, 'Get course info').then(i => {
			for (const entry of Object.entries(i.data))
				for (const course of entry[1])
					courseDetail[course.sn] = parseRawCourseData(course, null);
		});
	};

	this.clear = function () {
		initTable(scheduleTable);
		initTable(preScheduleTable);
	};

	function courseCellHoverStart() {
		const /**@type{HTMLDivElement[]}*/ cells = courseSameCell[this.serialID];
		if (!cells)
			return;
		for (let cell of cells) {
			cell.parentElement.classList.add('hover');
			cell.classList.add('hover');
		}
	}

	function courseCellHoverEnd() {
		const /**@type{HTMLDivElement[]}*/ cells = courseSameCell[this.serialID];
		if (!cells)
			return;
		for (let cell of cells) {
			cell.parentElement.classList.remove('hover');
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
			j.time = parsedTime;
		}
		return [holiday ? 7 : 5, nightTime ? 17 : 11]
	}

	function parseData2DayTable(tableWidth, tableHeight, scheduleData) {
		// Parse schedule
		const daySectionTable = new Array(tableWidth);
		const dayUndecided = [];
		for (let i = 0; i < 7; i++)
			daySectionTable[i] = new Array(tableHeight);
		for (const eachCourse of scheduleData.schedule)
			for (const timeLocInfo of eachCourse.info) {
				// Time undecided
				if (timeLocInfo.time[0] === -1) {
					dayUndecided.push([eachCourse, timeLocInfo]);
					continue;
				}

				// Add course to timetable
				let section = daySectionTable[timeLocInfo.time[0]][timeLocInfo.time[1]];
				if (!section)
					section = daySectionTable[timeLocInfo.time[0]][timeLocInfo.time[1]] = {};

				let sectionCourse = section[eachCourse.deptID + '-' + eachCourse.sn];
				if (!sectionCourse)
					sectionCourse = section[eachCourse.deptID + '-' + eachCourse.sn] = [eachCourse, timeLocInfo];
				else
					sectionCourse.push(timeLocInfo);
			}

		return {dayTable: daySectionTable, dayUndecided};
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
		headRow.appendChild(td(null, 'background'));

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
	 * @param {HTMLTableCellElement} parentCell
	 * @param {[ScheduleCourse, ...time]} courseWithTime
	 * @return {HTMLTableCellElement}
	 */
	function createCourseCell(parentCell, courseWithTime) {
		parentCell.className = 'activate';
		const courseInfo = courseWithTime[0];
		const serialID = courseInfo.deptID + '-' + courseInfo.sn;
		courseDetail[serialID] = null;

		const courseCell = div('sure', {serialID: serialID, onclick: cellClick},
			// span('[' + courseInfo.deptID + ']' + courseInfo.sn + ' '),
			span(courseInfo.name),
		);

		// Add room
		for (let k = 1; k < courseWithTime.length; k++) {
			if (!courseWithTime[k].room) continue;
			const roomNameElement = span(courseWithTime[k].room, 'room');
			// Show roomName or not
			if (!showClassRoom)
				roomNameElement.style.display = 'none';
			courseCell.appendChild(roomNameElement);
			roomNameElements.push(roomNameElement);
		}
		parentCell.appendChild(courseCell);
		return courseCell;
	}

	/**
	 * @param {HTMLTableCellElement[][]} cellTable
	 * @param {[ScheduleCourse, ...time][]} courseWithTimeList
	 * @param {boolean} pre
	 */
	function createCourseCellSingleList(cellTable, courseWithTimeList, pre) {
		for (const courseWithTime of courseWithTimeList) {
			if (courseWithTime[1].time.length === 3) {
				const courseTime = courseWithTime[1].time;
				const length = courseTime[2] - courseTime[1] + 1;
				for (let k = 0; k < length; k++) {
					createCourseCellSingle(cellTable[courseTime[1] + k][courseTime[0]], courseWithTime, pre);
				}
			} else
				console.warn(courseWithTime);
		}
	}

	/**
	 * @param {HTMLTableCellElement} parentCell
	 * @param {[ScheduleCourse, ...time]} courseWithTime
	 * @param {boolean} pre
	 * @return {HTMLDivElement}
	 */
	function createCourseCellSingle(parentCell, courseWithTime, pre) {
		parentCell.className = 'activate';
		const courseInfo = courseWithTime[0];
		const serialID = courseInfo.deptID + '-' + courseInfo.sn;
		const courseCell = div(pre ? 'pre' : 'sure', {serialID: serialID, onclick: cellClick},
			span('[' + courseInfo.deptID + ']' + courseInfo.sn + ' '),
			span(courseInfo.name),
		);
		courseDetail[serialID] = null;
		(courseSameCell[serialID] || (courseSameCell[serialID] = [])).push(courseCell);
		courseCell.onmouseenter = courseCellHoverStart;
		courseCell.onmouseleave = courseCellHoverEnd;

		// Add room
		for (let k = 1; k < courseWithTime.length; k++) {
			if (!courseWithTime[k].room) continue;
			const roomNameElement = span(courseWithTime[k].room, 'room');
			// Show roomName or not
			if (!showClassRoom)
				roomNameElement.style.display = 'none';
			courseCell.appendChild(roomNameElement);
			roomNameElements.push(roomNameElement);
		}

		parentCell.appendChild(courseCell);
		return courseCell;
	}

	this.showRoomName = function (show) {
		const display = show ? null : 'none';
		for (const roomNameElement of roomNameElements)
			roomNameElement.style.display = display;
	}

	this.setTableShow = function (showPre) {
		if (showPre) {
			scheduleTable.style.display = 'none';
			preScheduleTable.style.display = null;
		} else {
			scheduleTable.style.display = null;
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

	const width = elementWidth;
	const height = elementHeight;

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
		'<defs><style>' + embedFont + cssStyles + 'body{background:none; z-index:-1;}</style></defs>' +
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
		let embedFont = '';
		let fontFile, lastIndex = 0;
		while ((fontFile = URL_REGEX.exec(font))) {
			embedFont += font.substring(lastIndex, fontFile.index + 9);
			const url = fontFile[1];
			const reader = new FileReader();
			reader.readAsDataURL(await fetch(url).then(i => i.blob()));
			await new Promise(resolve => reader.onloadend = resolve);
			embedFont += '"' + reader.result + '"';
			lastIndex = fontFile.index + url.length + 9;
		}
		embedFont += font.substring(lastIndex, font.length);
		return embedFont;
	}
}