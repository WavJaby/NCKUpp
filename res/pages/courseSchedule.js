'use strict';

import {
	a,
	button,
	checkbox,
	div,
	h1,
	h2,
	mountableStylesheet,
	p,
	span,
	table,
	tbody,
	td,
	text,
	th,
	thead,
	tr
} from '../minjs_v000/domHelper.min.js';
import {addPreSchedule, checkLocalStorage, courseDataTimeToString, fetchApi, parseRawCourseData, removePreSchedule} from '../lib/lib.js';
import PopupWindow from '../popupWindow.js';
import {createSelectAvailableStr, createSyllabusUrl, NckuHubDetailWindow, nckuHubScoreToSpan} from './courseSearch.js';

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
	const showClassroomCheckbox = checkbox(null, false, null, span('顯示教室'));
	const showPreScheduleCheckbox = checkbox(null, false,null, span('顯示預排'));
	const scheduleTableInfo = span(null, 'scheduleTableInfo');
	const windowRoot = div();
	const scheduleTable = new ScheduleTable(windowRoot, updatePreScheduleData);
	const courseTable = new CourseTable(windowRoot, updatePreScheduleData);
	const downloadScheduleButton = button('downloadScheduleBtn', '下載課表');
	const downloadScheduleButtonHide = a(null, null, null, {style: 'display:none'});
	let scheduleLoading = false, scheduleLoadingQueue = false, scheduleLoadingTaskCount = 0;

	showClassroomCheckbox.input.onchange = () => scheduleTable.showRoomName(showClassroomCheckbox.input.checked);
	showPreScheduleCheckbox.input.onchange = () => {
		scheduleTable.setTableShow(showPreScheduleCheckbox.input.checked);
		courseTable.setTableShow(showPreScheduleCheckbox.input.checked);
	};
	scheduleTable.showRoomName(showClassroomCheckbox.input.checked);
	scheduleTable.setTableShow(showPreScheduleCheckbox.input.checked);
	courseTable.setTableShow(showPreScheduleCheckbox.input.checked);

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

		if (!checkLocalStorage()) {
			window.messageAlert.addError('請允許內部儲存', '開啟內部儲存來使用更多功能');
			return;
		}

		pageStorage = router.getPageStorage(this, 0);
		loadLastScheduleData();
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
			scheduleLoadingTaskCount = 2;
			if (!scheduleTable.dataReady())
				downloadScheduleButton.classList.remove('show');
			scheduleTable.clearScheduleData();
			courseTable.clearCourseData();
			fetchApi('/courseSchedule', 'Get schedule').then(response => {
				scheduleLoadingTaskCount--;
				if (!response.success || !response.data) {
					scheduleLoading = false;
					return;
				}
				// Parse normal schedule
				const scheduleData = response.data;
				pageStorage.data['savedCurrentSchedule'] = scheduleData;
				scheduleTable.setScheduleData(scheduleData);
				courseTable.setScheduleData(scheduleData);
				updateScheduleInfo(scheduleData);
				checkScheduleDataAndRender(false);
			});
			fetchApi('/courseSchedule?pre=true', 'Get pre-schedule').then(response => {
				scheduleLoadingTaskCount--;
				if (!response.success || !response.data) {
					scheduleLoading = false;
					return;
				}
				// Parse pre schedule
				const preScheduleData = response.data;
				pageStorage.data['savedCurrentPreSchedule'] = preScheduleData;
				scheduleTable.setPreScheduleData(preScheduleData);
				courseTable.setPreScheduleData(preScheduleData);
				checkScheduleDataAndRender(false);
			});
			fetchApi('/courseRegister?mode=course', 'Get course register table').then(response => {
				if (!response.success || !response.data) {
					scheduleLoading = false;
					return;
				}
				const genCourseData = response.data;
				pageStorage.data['savedCurrentGenCourseData'] = genCourseData;
				courseTable.setGenCourseData(genCourseData);
				checkCourseDataAndRender();
			});
			fetchApi('/courseRegister?mode=genEdu', 'Get gen-edu register table').then(response => {
				if (!response.success || !response.data) {
					scheduleLoading = false;
					return;
				}
				const genEduCourseData = response.data;
				pageStorage.data['savedCurrentGenEduCourseData'] = genEduCourseData;
				courseTable.setGenEduCourseData(genEduCourseData);
				checkCourseDataAndRender();
			});
			fetchApi('/A9Registered', 'Get A9 register count').then(response => {
				if (!response.success || !response.data) {
					scheduleLoading = false;
					return;
				}
				courseTable.setA9Registered(response.data);
				checkCourseDataAndRender();
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
		courseTable.renderTable();
		pageStorage.save();
		downloadScheduleButton.classList.add('show');

		// Try load course detail from page storage
		const courseDetailRaw = pageStorage.data['savedCurrentCourseDetailRaw'];
		if (firstRender && courseDetailRaw) {
			const courseData = parseCourseDetail(courseDetailRaw);
			scheduleTable.setCourseDetailData(courseData);
			courseTable.setCourseDetailData(courseData);
			checkScheduleLoadingDone();
			updateNckuHubData();
		} else {
			scheduleLoadingTaskCount++;
			// Fetch course data
			fetchApi('/search?serial=' + scheduleTable.getSearchQuery(), 'Get course info').then(response => {
				// Update course data
				if (response.success && response.data) {
					pageStorage.data['savedCurrentCourseDetailRaw'] = response.data;
					pageStorage.save();
					const courseData = parseCourseDetail(response.data);
					scheduleTable.setCourseDetailData(courseData);
					courseTable.setCourseDetailData(courseData);
				}
				scheduleLoadingTaskCount--;
				checkScheduleLoadingDone();
				updateNckuHubData();
			});
		}
	}

	function checkCourseDataAndRender() {
		if (!courseTable.dataReady())
			return;

		courseTable.renderTable();
	}

	function checkScheduleLoadingDone() {
		if (scheduleLoadingTaskCount !== 0)
			return;
		// Reset loading
		scheduleLoading = false;
		// Fetch again if queue
		if (scheduleLoadingQueue) {
			scheduleLoadingQueue = false;
			onLoginState(loginState.state);
		}
	}

	function updateNckuHubData() {
		const courseList = scheduleTable.getScheduleCourse().join(',');
		fetchApi('/nckuhub?id=' + courseList)
			.then(response => response.success && courseTable.setNckuHubData(response.data));
	}

	function parseCourseDetail(courseDataRaw) {
		const courseDetail = {};
		for (const course of courseDataRaw)
			courseDetail[course.sn] = parseRawCourseData(course, null);
		return courseDetail;
	}

	function loadLastScheduleData() {
		const scheduleData = pageStorage.data['savedCurrentSchedule'];
		const preScheduleData = pageStorage.data['savedCurrentPreSchedule'];
		if (scheduleData && preScheduleData) {
			scheduleLoading = true;
			scheduleLoadingQueue = false;
			scheduleLoadingTaskCount = 0;
			updateScheduleInfo(scheduleData);
			scheduleTable.setScheduleData(scheduleData);
			scheduleTable.setPreScheduleData(preScheduleData);
			courseTable.setScheduleData(scheduleData);
			courseTable.setPreScheduleData(preScheduleData);
			checkScheduleDataAndRender(true);
		}

		const genCourseData = pageStorage.data['savedCurrentGenCourseData'];
		const genEduCourseData = pageStorage.data['savedCurrentGenEduCourseData'];
		if (genCourseData && genEduCourseData) {
			// 	courseTable.setGenCourseData(genCourseData);
			// 	courseTable.setGenEduCourseData(genEduCourseData);
			// 	courseTable.renderTable();
		}
	}

	function updatePreScheduleData() {
		fetchApi('/courseSchedule?pre=true', 'Get pre-schedule').then(response => {
			if (!response.success || !response.data)
				return;
			scheduleTable.setAndRenderPreScheduleData(response.data);
			courseTable.setPreScheduleData(response.data);
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
		courseTable.element,
		windowRoot,
	);
};

/**
 * @param {HTMLElement} windowRoot
 * @param updatePreScheduleData
 * @constructor
 */
function CourseTable(windowRoot, updatePreScheduleData) {
	const nckuHubDetailWindow = new NckuHubDetailWindow(windowRoot);
	const courseListBody = tbody();
	this.element = table('courseTable',
		thead(null, tr(null,
			// th('系所', 'departmentName'),
			th('系-序號', 'serialNumber'),
			// th('類別', 'category'),
			// th('年級', 'grade'),
			// th('班別', 'classInfo'),
			// th('組別', 'classGroup'),
			th('時間', 'courseTime'),
			th('課程名稱', 'courseName'),
			th('選必修', 'required'),
			th('學分', 'credits'),
			th('抽籤人數', 'registerCount'),
			th('已選', 'selected'),
			th('餘額', 'available'),
			// NCKU HUB
			th('收穫', 'nckuHub'),
			th('甜度', 'nckuHub'),
			th('涼度', 'nckuHub'),
			// Function buttons
			th('功能', 'options'),
		)),
		courseListBody
	);
	let genCourseData = null;
	let genEduCourseData = null;
	let /**@type ?ScheduleData*/ scheduleData = null;
	let /**@type ?ScheduleData*/ preScheduleData = null;
	let a9Registered = null;
	let /**@type{Object.<string, CourseData>}*/ courseDetail = {};
	let nckuHubData = {}, nckuHubElements = {};
	let preRegAction = null, regAction = null, preRegPreSkip = null;

	this.setGenCourseData = function (data) {
		regAction = data.action;
		genCourseData = {};
		for (let i of data.courseList) {
			genCourseData[i.serialNumber] = i;
		}
	};

	this.setGenEduCourseData = function (data) {
		preRegAction = data.action;
		preRegPreSkip = data.preSkip;
		genEduCourseData = {};
		for (let i of data.courseList) {
			genEduCourseData[i.serialNumber] = i;
		}
	};

	this.setPreScheduleData = function (data) {
		preScheduleData = data;
	};

	this.setScheduleData = function (data) {
		scheduleData = data;
	};

	this.setNckuHubData = function (data) {
		nckuHubData = data;

		for (const i in nckuHubElements) {
			const nckuHubEle = nckuHubElements[i];
			const nckuHub = nckuHubData[i];
			if (!nckuHub || nckuHub.rate_count === 0)
				continue;

			if (nckuHubEle[0].firstChild)
				nckuHubEle[0].firstChild.parentElement.removeChild(nckuHubEle[0].firstChild);
			nckuHubEle[0].appendChild(nckuHubScoreToSpan(nckuHub.got));
			if (nckuHubEle[1].firstChild)
				nckuHubEle[1].firstChild.parentElement.removeChild(nckuHubEle[1].firstChild);
			nckuHubEle[1].appendChild(nckuHubScoreToSpan(nckuHub.sweet));
			if (nckuHubEle[2].firstChild)
				nckuHubEle[2].firstChild.parentElement.removeChild(nckuHubEle[2].firstChild);
			nckuHubEle[2].appendChild(nckuHubScoreToSpan(nckuHub.cold));
		}
	};

	this.setA9Registered = function (data) {
		a9Registered = data.list;
	};

	this.setCourseDetailData = function (courseDetailData) {
		courseDetail = courseDetailData;
		updateTable();
	};

	this.renderTable = updateTable;

	this.clearCourseData = function () {
		genCourseData = null;
		genEduCourseData = null;
		scheduleData = null;
		preScheduleData = null;
		a9Registered = null;
		nckuHubElements = {};
		// courseDetail = {};
	};

	this.dataReady = function () {
		return scheduleData != null && preScheduleData != null &&
			genCourseData != null && genEduCourseData != null && a9Registered != null;
	};

	this.setTableShow = function (state) {
		if (state)
			courseListBody.classList.add('showPre');
		else
			courseListBody.classList.remove('showPre');
	};

	function updateTable() {
		while (courseListBody.firstChild)
			courseListBody.firstChild.parentElement.removeChild(courseListBody.firstChild);
		// console.log(genCourseData, genEduCourseData);
		console.log('Course table update');

		if (preScheduleData) for (const preSchedule of preScheduleData.schedule) {
			courseListBody.appendChild(tr('pre margin'));
			courseListBody.appendChild(
				renderCourseBlock(preSchedule,
					genCourseData && genCourseData[preSchedule.serialNumber] || genEduCourseData && genEduCourseData[preSchedule.serialNumber] || {},
					courseDetail[preSchedule.serialNumber] || {},
					a9Registered && a9Registered[preSchedule.serialNumber],
					preSchedule.delete,
					true)
			);
		}

		if (scheduleData) for (const preSchedule of scheduleData.schedule) {
			courseListBody.appendChild(tr('margin'));
			courseListBody.appendChild(
				renderCourseBlock(preSchedule,
					genCourseData && genCourseData[preSchedule.serialNumber] || genEduCourseData && genEduCourseData[preSchedule.serialNumber] || {},
					courseDetail[preSchedule.serialNumber] || {},
					a9Registered && a9Registered[preSchedule.serialNumber],
					preSchedule.delete,
					false)
			);
		}
	}

	function renderCourseBlock(preSchedule, regCourseData, detail, a9Registered, removeKey, pre) {
		const courseName = detail.courseName || preSchedule.courseName;
		const serialNumber = detail.serialNumber || preSchedule.serialNumber;
		const credits = detail.credits || preSchedule.credits;
		const nckuHub = nckuHubData[serialNumber] || {};
		const nckuHubEle = [
			div(null, nckuHub.rate_count > 0 && nckuHub.got && nckuHubScoreToSpan(nckuHub.got)),
			div(null, nckuHub.rate_count > 0 && nckuHub.sweet && nckuHubScoreToSpan(nckuHub.sweet)),
			div(null, nckuHub.rate_count > 0 && nckuHub.cold && nckuHubScoreToSpan(nckuHub.cold)),
		];
		nckuHubElements[serialNumber] = nckuHubEle;
		return tr(pre ? 'pre courseBlock' : 'courseBlock',
			// td(detail.departmentName, 'departmentName'),
			td(serialNumber, 'serialNumber'),
			// td(null, 'category', detail.category && text(detail.category)),
			// td(null, 'grade', detail.courseGrade && text(detail.courseGrade.toString())),
			// td(null, 'classInfo', detail.classInfo && text(detail.classInfo)),
			// td(null, 'classGroup', detail.classGroup && text(detail.classGroup)),
			td(null, 'courseTime',
				detail.time && detail.time.map(i =>
					i.flexTimeDataKey
						? button(null, '詳細時間', null, {key: i.flexTimeDataKey, courseData: detail})
						: button(null, courseDataTimeToString(i))
				) || detail.timeString && text(detail.timeString)
			),
			td(null, 'courseName',
				a(null, createSyllabusUrl(detail.semester, detail.systemNumber), null, null, {target: '_blank'}, span(courseName))
			),
			td(null, 'required', detail.required == null ? null : text(detail.required ? '必修' : '選修')),
			td(null, 'credits', credits == null ? null : text(credits.toString())),
			td(null, 'registerCount', a9Registered == null ? null : text(a9Registered.count.toString())),
			td(null, 'selected', detail.selected == null ? null : span(detail.selected)),
			td(null, 'available', detail.available == null ? null : createSelectAvailableStr(detail)),
			td(null, 'nckuHub', nckuHubEle, {colSpan: 3, onclick: openNckuHubWindow, serialNumber, courseData: preSchedule}),
			td(null, 'functionBtn', regCourseData.cosdata && (regCourseData.prechk == null
				? button('functionBtn', '單科加選', addCourse, {courseData: preSchedule, key: regCourseData.cosdata, preKey: detail.preRegister})
				: button('functionBtn', '加入志願', addPreferenceEnter, {
					courseData: preSchedule,
					prechk: regCourseData.prechk,
					key: regCourseData.cosdata,
					preKey: detail.preRegister
				})),
				removeKey && button('removeBtn', '刪除', removePreScheduleButtonClick, {courseData: preSchedule, key: removeKey})
			)
		);
	}

	function openNckuHubWindow() {
		const nckuHub = nckuHubData[this.serialNumber];
		if (!nckuHub)
			return;
		this.courseData.nckuHub = nckuHub;
		nckuHubDetailWindow.set(this.courseData);
	}

	function addPreferenceEnter() {
		const button = this;
		const courseData = this.courseData;
		const preScheduleKey = this.preKey;
		const title = courseData.courseName + ' 加入志願';
		fetchApi(`/courseRegister?mode=genEdu`, 'Add preference', {
			method: 'POST',
			body: `prechk=${this.prechk}&cosdata=${this.key}&action=${preRegAction}&preSkip=${preRegPreSkip}`
		}).then(response => {
			preRegAction = response.data.action;
			if (!response.success) {
				window.messageAlert.addError(title + '失敗', response.msg, 10000);
				return;
			}
			window.messageAlert.addSuccess(title + '成功', response.msg, 5000);
			button.style.display = 'none';
			// Add course back after preference enter
			addPreSchedule(courseData, preScheduleKey);
		});
	}

	function addCourse() {
		const button = this;
		const courseData = this.courseData;
		const preScheduleKey = this.preKey;
		const title = courseData.courseName + ' 單科加選';
		fetchApi(`/courseRegister?mode=course`, 'Add course', {
			method: 'POST',
			body: `action=${regAction}&cosdata=${this.key}`
		}).then(response => {
			regAction = response.data.action;
			if (!response.success) {
				window.messageAlert.addError(title + '失敗', response.msg, 10000);
				return;
			}
			window.messageAlert.addSuccess(title + '成功', response.msg, 5000);
			button.style.display = 'none';
			// Add course back after preference enter
			addPreSchedule(courseData, preScheduleKey);
		});
	}

	function removePreScheduleButtonClick() {
		const removeKey = this.key;
		const courseData = this.courseData;

		const deleteConform = confirm('是否要刪除 ' + courseData.courseName);
		if (!deleteConform)
			return;

		const prefix = '預排刪除 ';
		removePreSchedule(courseData, removeKey,
			function (msg, courseName) {
				updatePreScheduleData();
				window.messageAlert.addSuccess(prefix + courseName, msg, 5000);
			},
			function (msg, courseName) {
				window.messageAlert.addError(prefix + courseName, msg, 5000);
			}
		);
	}
}

/**
 * @typedef {[ScheduleCourse, CourseDataTime]} CourseWithTime
 */

/**
 * @typedef ScheduleData
 * @property {string} studentId
 * @property {int} year
 * @property {int} semester
 * @property {float} credits
 * @property {ScheduleCourse[]} schedule
 */

/**
 * @param {HTMLElement} windowRoot
 * @param {function()} updatePreScheduleData
 * @constructor
 */
function ScheduleTable(windowRoot, updatePreScheduleData) {
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
				preCourseRemoveKey[course.serialNumber] = course.delete;
	};

	this.setAndRenderPreScheduleData = function (data) {
		preScheduleData = data;
		initPreScheduleTable();
	}

	this.setCourseDetailData = function (courseDetailData) {
		courseDetail = courseDetailData;
	};

	this.dataReady = function () {
		return scheduleData != null && preScheduleData != null;
	};

	this.renderTable = function () {
		initScheduleTable();
		initPreScheduleTable();
	};

	this.getSearchQuery = function () {
		// // get course info
		// const courseDept = {};
		// for (const serialNumber in courseDetail) {
		// 	const dept = serialNumber.split('-');
		// 	let deptData = courseDept[dept[0]];
		// 	if (deptData)
		// 		deptData.push(dept[1]);
		// 	else
		// 		courseDept[dept[0]] = [dept[1]];
		// }
		// const courseFetchArr = [];
		// for (const serialNumber in courseDept)
		// 	courseFetchArr.push(serialNumber + '=' + courseDept[serialNumber].join(','));
		// return encodeURIComponent(courseFetchArr.join('&'));
		return Object.keys(courseDetail).join(',');
	};

	this.getScheduleCourse = function () {
		return Object.keys(courseDetail);
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

	function cellClick() {
		const data = courseDetail[this.serialNumber];
		if (!data)
			return;

		const locationButtons = [];
		if (data.time)
			for (let time of data.time) {
				let timeStr = courseDataTimeToString(time);
				locationButtons.push(button(null, timeStr + ' ' + time.classroomName, openCourseLocation, {locationQuery: time.deptID + ',' + time.classroomID}));
			}
		courseInfoWindow.windowSet(div('courseInfo',
			preCourseRemoveKey[this.serialNumber] && button('delete', '刪除', removePreScheduleButtonClick, {serialNumber: this.serialNumber}),
			h2(data.serialNumber + ' ' + data.courseName),
			data.instructors.map(i => span(i + ' ')),
			p(data.courseNote),
			button(null, 'moodle', openCourseMoodle, {moodleQuery: data.moodle}),
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

	/**@this{{serialNumber: string}}*/
	function removePreScheduleButtonClick() {
		const removeKey = preCourseRemoveKey[this.serialNumber];
		const courseData = courseDetail[this.serialNumber];

		const deleteConform = confirm('是否要刪除 ' + courseData.courseName);
		if (!deleteConform)
			return;

		const prefix = '預排刪除 ';
		removePreSchedule(courseData, removeKey,
			function (msg, courseName) {
				updatePreScheduleData();
				courseInfoWindow.windowClose();
				window.messageAlert.addSuccess(prefix + courseName, msg, 5000);
			},
			function (msg, courseName) {
				window.messageAlert.addError(prefix + courseName, msg, 5000);
			}
		);
	}

	function initScheduleTable() {
		// Parse data
		const {dayTable, dayUndecided} = parseData2DayTable(tableWidth, tableHeight, scheduleData);
		// console.log(dayTable);

		// Create table
		initTable(scheduleTable);
		createScheduleTable(scheduleTable, dayTable, dayUndecided.length > 0, tableWidth, tableHeight);

		// // Add course cell
		// const rowSize = new Int32Array(tableHeight);
		// for (let i = 0; i < tableWidth; i++) {
		// 	for (let j = 0; j < tableHeight; j++) {
		// 		const dayCourse = dayTable[i][j];
		// 		if (!dayCourse) continue;
		// 		const course = dayCourse[0];
		// 		// Fill time section
		// 		if (i - rowSize[j] > 0)
		// 			rows[j].insertCell().colSpan = i - rowSize[j];
		//
		// 		// Build cell
		// 		const cell = rows[j].insertCell();
		// 		createCourseCell(cell, course, false).classList.add('fullHeight');
		//
		// 		// Add space
		// 		if (course[1].parsedTime.length === 3) {
		// 			const length = course[1].parsedTime[2] - course[1].parsedTime[1] + 1;
		// 			cell.rowSpan = length * 2 - 1;
		// 			rowSize[j] = i + 1;
		// 			for (let k = 1; k < length; k++) {
		// 				// fill space
		// 				if (i - rowSize[j + k] > 0)
		// 					rows[j + k].insertCell().colSpan = i - rowSize[j + k];
		// 				rowSize[j + k] = i + 1;
		// 			}
		// 		} else if (course[1].parsedTime.length === 2)
		// 			rowSize[j] = i + 1;
		// 	}
		// }

		createCourseUndecided(dayUndecided, scheduleTable);
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
		// console.log(dayUndecided)
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

		// Create table
		initTable(preScheduleTable);
		createScheduleTable(preScheduleTable, dayTable, dayUndecided.length > 0, preTableWidth, preTableHeight);

		createCourseUndecided(dayUndecided, preScheduleTable);
	}

	function courseCellHoverStart() {
		const /**@type{HTMLDivElement[]}*/ cells = courseSameCell[this.serialNumber];
		if (!cells)
			return;
		for (let cell of cells) {
			cell.classList.add('hover');
		}
	}

	function courseCellHoverEnd() {
		const /**@type{HTMLDivElement[]}*/ cells = courseSameCell[this.serialNumber];
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
		for (const i of scheduleData.schedule) for (const time of i.time) {
			// Find table width
			if (time.dayOfWeek > 4)
				holiday = true;
			if (time.sectionStart !== null && time.sectionStart > nightTimeStart ||
				time.sectionEnd !== null && time.sectionEnd > nightTimeStart)
				nightTime = true;
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
			const serialNumber = eachCourse.serialNumber;
			if (courseDetail[serialNumber] === undefined)
				courseDetail[serialNumber] = null;
			for (const timeLocInfo of eachCourse.time) {
				// Time undecided
				if (timeLocInfo.dayOfWeek === null || timeLocInfo.sectionStart === null) {
					dayUndecided.push([eachCourse, timeLocInfo]);
					continue;
				}

				// Add course to timetable
				let section = dayTable[timeLocInfo.dayOfWeek][timeLocInfo.sectionStart];
				if (!section)
					section = dayTable[timeLocInfo.dayOfWeek][timeLocInfo.sectionStart] = {};

				let sectionCourse = section[eachCourse.serialNumber];
				if (!sectionCourse)
					sectionCourse = section[eachCourse.serialNumber] = [eachCourse, timeLocInfo];
				else
					sectionCourse.push(timeLocInfo);
			}
		}
		for (let i = 0; i < tableWidth; i++)
			for (let j = 0; j < tableHeight; j++)
				if (dayTable[i][j]) {
					const /**@type{CourseWithTime[]}*/ courseTimeData = dayTable[i][j] = Object.values(dayTable[i][j]);
					// Course in section
					for (const courseData of courseTimeData) {
						const courseTime = courseData[1];
						if (j !== courseTime.sectionStart)
							continue;
						const length = courseTime.sectionEnd - courseTime.sectionStart + 1;
						// Fill time, push room info
						for (let k = 1; k < length; k++) {
							let courseTimeDataNext = dayTable[i][j + k];
							if (!courseTimeDataNext) courseTimeDataNext = dayTable[i][j + k] = [];
							courseTimeDataNext.push(courseData);
						}
					}
				}

		return {dayTable: dayTable, dayUndecided: dayUndecided};
	}

	/**
	 * @param {HTMLTableElement} table
	 * @param {[CourseWithTime][][]} dayTable
	 * @param {boolean} haveDayUndecided
	 * @param {int} tableWidth
	 * @param {int} tableHeight
	 */
	function createScheduleTable(table, dayTable, haveDayUndecided, tableWidth, tableHeight) {
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

		// Create course cell
		/**@type{HTMLTableCellElement[][]}*/
		const cellTable = new Array(tableWidth);
		// Create table body
		for (let i = 0; i < tableWidth; i++) {
			cellTable[i] = new Array(tableHeight);
			for (let j = 0; j < tableHeight; j++) {
				// Create empty cell
				cellTable[i][j] = rows[j].insertCell();
			}
		}

		// Put course
		for (let dayOfWeek = 0; dayOfWeek < tableWidth; dayOfWeek++) {
			for (let sectionTime = 0; sectionTime < tableHeight; sectionTime++) {
				const sectionCourseListCol = dayTable[dayOfWeek];
				const sectionCourseList = sectionCourseListCol[sectionTime];
				if (!sectionCourseList)
					continue;
				// Section course list
				for (let sectionCourse of sectionCourseList) {
					const courseTime = sectionCourse[1];
					if (sectionTime !== courseTime.sectionStart)
						continue;
					const cellTableCol = cellTable[courseTime.dayOfWeek];
					const parentCell = cellTableCol[courseTime.sectionStart];
					const timeLength = courseTime.sectionEnd - courseTime.sectionStart + 1;
					createCourseCell(parentCell, sectionCourse, true);
					for (let i = 1; i < timeLength; i++) {
						const sectionCourseListNext = sectionCourseListCol[sectionTime + i];
						const parentCellNext = cellTableCol[courseTime.sectionStart + i];
						// If current and next section only have one course, expand row
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
		for (let i = 0; i < tableWidth; i++) {
			for (let j = 0; j < tableHeight; j++)
				if (cellTable[i][j].childElementCount === 1)
					cellTable[i][j].firstElementChild.classList.add('fullHeight');
		}
	}

	/**
	 * @param {HTMLTableCellElement} parentCell
	 * @param {CourseWithTime} courseWithTime
	 * @param {boolean} showSerialNumber
	 * @return {HTMLDivElement}
	 */
	function createCourseCell(parentCell, courseWithTime, showSerialNumber) {
		parentCell.className = 'activate';
		const courseInfo = courseWithTime[0];
		const serialNumber = courseInfo.serialNumber;

		const courseCell = div(courseInfo.pre ? 'pre' : 'sure', {serialNumber: serialNumber, onclick: cellClick},
			showSerialNumber ? span(courseInfo.serialNumber + ' ') : null,
			span(courseInfo.courseName),
		);
		if (showSerialNumber) {
			(courseSameCell[serialNumber] || (courseSameCell[serialNumber] = [])).push(courseCell);
			courseCell.onmouseenter = courseCellHoverStart;
			courseCell.onmouseleave = courseCellHoverEnd;
		}

		// Add room
		for (let k = 1; k < courseWithTime.length; k++) {
			if (!courseWithTime[k].classroomName) continue;
			courseCell.appendChild(span(courseWithTime[k].classroomName, 'room'));
		}
		parentCell.appendChild(courseCell);
		return courseCell;
	}

	function createCourseUndecided(dayUndecided, table) {
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
				createCourseCell(daysUndecidedCell, course, course.pre);
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