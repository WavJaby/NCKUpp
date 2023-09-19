'use strict';

/**
 * @typedef {Object} CourseDataRaw
 * @property {string} y - semester
 * @property {string} dn - departmentName
 * @property {string} sn - serialNumber
 * @property {string} ca - attributeCode
 * @property {string} cs - systemNumber
 * @property {int} g - courseGrade 年級
 * @property {string} co - classInfo 班別
 * @property {string} cg - classGroup 組別
 * @property {string} ct - category
 * @property {string} cn - courseName
 * @property {string} ci - courseNote
 * @property {string} cl - courseLimit
 * @property {string[]} tg - tags
 * @property {float} c - credits
 * @property {boolean} r - required
 * @property {string[]} i - instructors
 * @property {int} s - selected
 * @property {int} a - available
 * @property {string[]} t - time
 * @property {string} m - moodle
 * @property {string} pe - preferenceEnter
 * @property {string} ac - addCourse
 * @property {string} pr - preRegister
 * @property {string} ar - addRequest
 */
/**
 * @typedef {Object} CourseData
 * @property {string} semester
 * @property {string} departmentName
 * @property {string} serialNumber
 * @property {string} attributeCode
 * @property {string} systemNumber
 * @property {int} courseGrade
 * @property {string} classInfo
 * @property {string} classGroup
 * @property {string} category
 * @property {string} courseName
 * @property {string|null} courseNote
 * @property {string|null} courseLimit
 * @property {CourseDataTag[]|null} tags
 * @property {float} credits
 * @property {boolean} required
 * @property {(UrSchoolInstructorSimple|string)[]|null} instructors - Only name or full data
 * @property {int|null|undefined} registerCount - Register count for course. undefined: dept have no register data
 * @property {int} selected
 * @property {int} available
 * @property {CourseDataTime[]|null} time
 * @property {string} timeString
 * @property {string} moodle
 * @property {string} preferenceEnter
 * @property {string} addCourse
 * @property {string} preRegister
 * @property {string} addRequest
 * @property {NckuHub|null} nckuHub
 */
/**
 * @typedef {Object} CourseDataTag
 * @property {string} name
 * @property {string} color
 * @property {string} [link]
 */
/**
 * @typedef {Object} CourseDataTime
 * @property {int} dayOfWeek
 * @property {int | null} sectionStart
 * @property {int | null} sectionEnd
 * @property {string | null} deptID
 * @property {string | null} classroomID
 * @property {string | null} classroomName
 * @property {string | null} flexTimeDataKey
 */
/**
 * @typedef {Object} UrSchoolInstructor
 * @property {string} id
 * @property {[path: string, name: string][]} tags
 * @property {int} reviewerCount
 * @property {int} takeCourseCount
 * @property {string[]} takeCourseUser
 * @property {UrSchoolInstructorComments[]} comments
 * @property {UrSchoolInstructorSimple} info,
 */
/**
 * @typedef {Object} UrSchoolInstructorComments
 * @property {string} updated_at
 * @property {int} user_id
 * @property {boolean} is_anonymous
 * @property {string} profile
 * @property {string} created_at
 * @property {int} id
 * @property {string} body
 * @property {int} status
 * @property {int} timestamp
 */
/**
 * @typedef {Object} UrSchoolInstructorSimple
 * @property {string} id
 * @property {string} mode
 * @property {string} name
 * @property {string} department
 * @property {string} jobTitle
 * @property {float} recommend
 * @property {float} reward
 * @property {float} articulate
 * @property {float} pressure
 * @property {float} sweet
 * @property {string} averageScore
 * @property {string} qualifications
 * @property {string} note
 * @property {string} nickname
 * @property {string} rollCallMethod
 */
/**
 * @typedef {Object} NckuHub
 * @property {boolean} noData
 * @property {int} rate_count
 * @property {string} got
 * @property {string} sweet
 * @property {string} cold
 * @property {NckuHubCommentObject[]} comments
 * @property {NckuHubRateObject[]} rates
 * @property {Object.<int, NckuHubRateObject>} parsedRates
 */
/**
 * @typedef {Object} NckuHubRateObject
 * @property {int} id
 * @property {int} user_id
 * @property {int} post_id
 * @property {float} got
 * @property {float} sweet
 * @property {float} cold
 * @property {int} like
 * @property {int} dislike
 * @property {int} hard
 * @property {int} recommend
 * @property {int} give
 * @property {string} course_name
 * @property {string} teacher
 */
/**
 * @typedef {Object} NckuHubCommentObject
 * @property {int} id
 * @property {string} comment
 * @property {string} semester
 */
/**
 * @typedef {Object} AllDeptData
 * @property {int} deptCount
 * @property {AllDeptGroup[]} deptGroup
 * @typedef {Object} AllDeptGroup
 * @property {string} name
 * @property {[string, string][]} dept
 */
/**
 * @typedef {Object} FlexTimeData
 * @property {string} date
 * @property {string} timeStart
 * @property {string} timeEnd
 */


import {
	a,
	button,
	checkboxWithName,
	ClassList,
	div,
	h1,
	img,
	input,
	label,
	mountableStylesheet,
	p,
	Signal,
	span,
	State,
	table,
	tbody,
	td,
	text,
	th,
	thead,
	tr
} from '../minjs_v000/domHelper.min.js';

import SelectMenu from '../selectMenu.js';
import {addPreSchedule, courseDataTimeToString, fetchApi, getPreSchedule, parseRawCourseData, removePreSchedule} from '../lib/lib.js';
import PopupWindow from '../popupWindow.js';

const textColor = {
	red: '#fa2b16',
	orange: '#ff8633',
	green: '#62cc38'
};
const totalColSpan = 18;

// Search from other page
let externalSearch = false;
let courseSearchFunc;

export function courseSearch(router, rawQuery, saveQuery) {
	externalSearch = true;
	router.openPage('CourseSearch', false, () => courseSearchFunc(rawQuery, saveQuery));
}

export function createSelectAvailableStr(courseData) {
	if (courseData.selected === null && courseData.available === null)
		return null;
	const available = courseData.available === null ? ''
		: courseData.available === -1 ? '不限'
			: courseData.available === -2 ? '洽系所'
				: courseData.available.toString();
	// Colored text
	if (courseData.available !== null) {
		if (courseData.available === 0)
			return span(available, null, {style: 'color:' + textColor.red});
		else if (courseData.available > 40)
			return span(null, null, span(available, null, {style: 'color:' + textColor.green}));
		else if (courseData.available > 0)
			return span(null, null, span(available, null, {style: 'color:' + textColor.orange}));
	}
	return span(available);
}

export function createSyllabusUrl(yearSem, sysNumClassCode) {
	if (yearSem == null || sysNumClassCode == null)
		return null;

	const year = yearSem.substring(0, yearSem.length - 1).padStart(4, '0');
	const sem = yearSem.charAt(yearSem.length - 1) === '0' ? '1' : '2';

	let systemNumber = sysNumClassCode, classCode = '';
	const index = sysNumClassCode.indexOf('-');
	if (index !== -1) {
		systemNumber = sysNumClassCode.substring(0, index);
		classCode = sysNumClassCode.substring(index + 1);
	}

	return 'https://class-qry.acad.ncku.edu.tw/syllabus/online_display.php?syear=' + year + '&sem=' + sem +
		'&co_no=' + systemNumber +
		'&class_code=' + classCode;
}

export function nckuHubScoreToSpan(score) {
	const color = score > 7 ? textColor.green : score > 3 ? textColor.orange : textColor.red;
	score = score.toFixed(1);
	if (score === '10.0')
		score = '10';
	return span(score, null, {style: 'color:' + color});
}

/**
 * @param {QueryRouter} router
 * @param {Signal} loginState
 * @param {UserGuideTool} userGuideTool
 * @return {HTMLDivElement}
 */
export default function (router, loginState, userGuideTool) {
	console.log('Course search Init');
	const styles = mountableStylesheet('./res/pages/courseSearch.css');
	const expandArrowImage = img('./res/assets/down_arrow_icon.svg', 'Expand button');
	expandArrowImage.className = 'noSelect noDrag';

	const searchResultSignal = new Signal({loading: false, courseResult: null, nckuHubResult: null});
	const instructorInfoBubble = InstructorInfoBubble();
	const userGuideTrigger = userGuideTool.pageTrigger.CourseSearch;
	courseSearchFunc = search;

	// Element
	const courseSearchResultInfo = span();
	// Static data
	let avalibleNckuHubCourseID = null;
	let urSchoolData = null;

	// query string
	let lastQueryString;
	let searching;

	function onRender() {
		console.log('Course search Render');
		styles.mount();

		fetchApi('/alldept').then(response => {
			if (response == null || !response.success || !response.data)
				return;
			deptNameSelectMenu.setItems(response.data.deptGroup.map(i => [i.name, i.dept]));
			if (!externalSearch)
				loadLastSearch(false);
		});
		// Search from other page
		if (!externalSearch)
			loadLastSearch(true);
	}

	function onPageOpen(isHistory) {
		console.log('Course search Open');
		styles.enable();
		if (isHistory)
			loadLastSearch(true);

		loginState.addListener(onLoginState);
	}

	function onPageClose() {
		console.log('Course search Close');
		styles.disable();
		loginState.removeListener(onLoginState);
	}

	function onLoginState(state) {
		if (state && state.login) {
			getWatchCourse();
			search();
		} else {
			if (state) {
				// window.messageAlert.addError('鄰近選課期，可能需要登入才能使用搜尋功能', '使用右上角按鈕進行登入', 10000);
				window.messageAlert.addInfo('登入啟用更多功能', '登入後即可將課程加入關注列表、加入預排、加入志願、單科加選等功能', 10000);
			}
			watchList = null;
		}
	}

	function loadLastSearch(performSearch) {
		const rawQuery = window.urlHashData['searchRawQuery'];

		for (const node of courseSearchForm.getElementsByTagName('input')) {
			let found = false;
			if (rawQuery)
				for (const rawQueryElement of rawQuery) {
					if (node.id === rawQueryElement[0]) {
						// From select menu
						if (node.selectMenu)
							node.selectMenu.selectItemByValue(rawQueryElement[1]);
						else
							node.value = rawQueryElement[1];
						found = true;
						break;
					}
				}
			if (!found)
				node.value = '';
		}

		if (performSearch && rawQuery && rawQuery.length > 0)
			search(rawQuery, false);
	}

	// Search form
	const deptNameSelectMenu = new SelectMenu('系所', 'dept', 'dept', null, {searchValue: true});
	const sectionSelectMenu = new SelectMenu('節次', 'section', 'section', [
		['0', '0'], ['1', '1'], ['2', '2'], ['3', '3'], ['4', '4'], ['5', 'N'], ['6', '5'], ['7', '6'], ['8', '7'], ['9', '8'], ['10', '9'],
		['11', 'A'], ['12', 'B'], ['13', 'C'], ['14', 'D'], ['15', 'E']
	], {multiple: true});
	const dayOfWeekSelectMenu = new SelectMenu('星期', 'dayOfWeek', 'dayOfWeek', [['0', '一'], ['1', '二'], ['2', '三'], ['3', '四'], ['4', '五'], ['5', '六'], ['6', '日']], {
		searchBar: false,
		multiple: true
	})
	const courseSearchForm = div('form',
		input(null, '課程名稱', 'courseName', {onkeyup: onSearchFormInput, type: 'search'}),
		deptNameSelectMenu.element,
		input(null, '教師姓名', 'instructor', {onkeyup: onSearchFormInput, type: 'search'}),
		new SelectMenu('年級', 'grade', 'grade', [['1', '1'], ['2', '2'], ['3', '3'], ['4', '4'], ['5', '5'], ['6', '6'], ['7', '7']], {searchBar: false}).element,
		dayOfWeekSelectMenu.element,
		sectionSelectMenu.element,
		input(null, '序號查詢 A0-000,B0-001', 'serial', {onkeyup: onSearchFormInput, type: 'search'}),
		button(null, '搜尋', search),
		button(null, '關注列表', getWatchCourse),
	);

	function onSearchFormInput(e) {
		if (e.key === 'Enter')
			search();
	}

	const searchTask = [];
	let nckuHubLoadingTaskCount = 0;

	/**
	 * @param {string[][]} [rawQuery] [key, value][]
	 * @param {boolean} [saveQuery] Will save query string if not provide or true
	 * @return {void}
	 */
	async function search(rawQuery, saveQuery) {
		if (searching) return;
		if (rawQuery instanceof Event)
			rawQuery = null;
		const searchTaskID = newSearchTask();
		nckuHubLoadingOverlayHide();

		// get all course ID
		const getAvailableNckuHubID = avalibleNckuHubCourseID === null ? fetchApi('/nckuhub', 'Get NCKU Hub id') : null;
		// get urSchool data
		const getUrSchoolData = urSchoolData === null ? fetchApi('/urschool', 'Get UrSchool data') : null;
		if (getAvailableNckuHubID)
			avalibleNckuHubCourseID = (await getAvailableNckuHubID).data;
		if (getUrSchoolData)
			urSchoolData = (await getUrSchoolData).data;

		// Get queryData
		let queryData = rawQuery;
		if (!queryData) {
			// Generate query from form
			queryData = [];
			for (const node of courseSearchForm.getElementsByTagName('input')) {
				if (node.id && node.id.length > 0 && node.value) {
					let value;
					// From select menu
					if (node.parentElement instanceof HTMLLabelElement &&
						node.parentElement.getSelectedValue)
						value = node.parentElement.getSelectedValue();
					else
						value = node.value.trim();

					// console.log(value);
					if (value.length > 0)
						queryData.push([node.id, value]);
				}
			}
		}
		// To query string
		const queryString = queryData.map(i => i[0] + '=' + encodeURIComponent(i[1])).join('&');
		const isSearchA9 = queryData.some(i => i[0] === 'dept' && i[1] === 'A9');

		if (queryData.length === 0) {
			window.messageAlert.addInfo('課程搜尋', '請輸入搜尋資料', 2000);
			lastQueryString = queryString;
			cancelSearchTask(searchTaskID, true);
			return;
		}

		// Save query string and create history
		if ((saveQuery === undefined || saveQuery === true) && lastQueryString !== queryString) {
			window.urlHashData['searchRawQuery'] = queryData;
			window.pushHistory();
		}

		// Update queryString
		lastQueryString = queryString;
		console.log('Search:', queryString);
		userGuideTrigger.searchStart();

		// Fetch data
		const searchA9Fetch = isSearchA9 ? fetchApi('/A9Registered', 'Get A9 register count', {timeout: 10000}) : null;
		const result = (await fetchApi('/search?' + queryString, 'Searching', {timeout: 10000}));
		let registerCountA9 = isSearchA9 ? await searchA9Fetch : null;
		registerCountA9 = registerCountA9 && registerCountA9.data && registerCountA9.data.list;

		// Search failed
		if (!result || !result.success || !result.data) {
			cancelSearchTask(searchTaskID, true);
			return;
		}
		// Search cancel
		if (searchTask.indexOf(searchTaskID) === -1) {
			return;
		}

		// Parse result
		nckuHubLoadingOverlayShow();
		const nckuHubResult = {};
		/**@type CourseData[]*/
		const courseResult = [];
		for (const rawCourseData of result.data) {
			const courseData = parseRawCourseData(rawCourseData, urSchoolData);

			// Add register count if available
			const registerCount = registerCountA9 ? registerCountA9[courseData.serialNumber] || null : undefined;
			courseData.registerCount = registerCount && registerCount.count;
			courseResult.push(courseData);

			// Prepare nckuHub if available
			if (courseData.serialNumber != null) {
				if (avalibleNckuHubCourseID.indexOf(courseData.serialNumber) !== -1)
					nckuHubResult[courseData.serialNumber] = {courseData, signal: new Signal()};
			}
		}

		// Get NCKU hub data
		const chunkSize = 20;
		const courseSerialNumbers = Object.keys(nckuHubResult);
		nckuHubLoadingTaskCount = Math.ceil(courseSerialNumbers.length / chunkSize);
		for (let i = 0; i < courseSerialNumbers.length; i += chunkSize) {
			const chunk = courseSerialNumbers.slice(i, i + chunkSize);
			fetchApi('/nckuhub?id=' + chunk.join(',')).then(response => {
				// If success
				if (response.success && response.data) for (let data of Object.entries(response.data)) {
					const {/**@type CourseData*/courseData, /**@type Signal*/signal} = nckuHubResult[data[0]];
					const /**@type{NckuHub}*/ nckuHub = data[1];
					courseData.nckuHub = /**@type NckuHub*/ {
						noData: nckuHub.rate_count === 0 && nckuHub.comments.length === 0,
						got: nckuHub.got,
						sweet: nckuHub.sweet,
						cold: nckuHub.cold,
						rate_count: nckuHub.rate_count,
						comments: nckuHub.comments,
						parsedRates: nckuHub.rates.reduce((a, v) => {
							a[v.post_id] = v;
							return a;
						}, {})
					};
					signal.update();
				}

				// Task done
				if (--nckuHubLoadingTaskCount === 0) {
					nckuHubLoadingOverlayHide();
				}
			});
		}

		console.log(courseResult);

		const i = searchTask.indexOf(searchTaskID);
		if (i !== -1)
			searchTask.splice(i, 1);
		searchResultSignal.set({loading: false, failed: false, courseResult, nckuHubResult: nckuHubResult});
		searching = false;
	}

	function nckuHubLoadingOverlayShow() {
		nckuHubLoadingOverlay[0].classList.add('show');
		nckuHubLoadingOverlay[1].classList.add('show');
		nckuHubLoadingOverlay[2].classList.add('show');
	}

	function nckuHubLoadingOverlayHide() {
		nckuHubLoadingOverlay[0].classList.remove('show');
		nckuHubLoadingOverlay[1].classList.remove('show');
		nckuHubLoadingOverlay[2].classList.remove('show');
	}

	function newSearchTask() {
		searching = true;
		const searchTaskID = Date.now();
		searchTask.push(searchTaskID);
		searchResultSignal.set({loading: true, failed: false, courseResult: null, nckuHubResult: null});
		return searchTaskID;
	}

	function cancelSearchTask(searchTaskID, failed) {
		const i = searchTask.indexOf(searchTaskID);
		if (i !== -1)
			searchTask.splice(i, 1);
		searchResultSignal.set({loading: false, failed: failed, courseResult: null, nckuHubResult: null});
		searching = false;
	}

	// Watched list
	let watchList = null;

	/**
	 * @this {HTMLButtonElement & {courseData: CourseData}}
	 */
	function watchedCourseAddRemove() {
		if (!loginState.state || !loginState.state.login || !watchList) return;

		const courseData = this.courseData;
		let serialIndex, result;
		if ((serialIndex = watchList.indexOf(courseData.serialNumber)) === -1) {
			console.log('add watch');
			result = fetchApi('/watchdog', 'add course to watch list', {
				method: 'POST',
				body: `studentID=${loginState.state.studentID}&courseSerial=${courseData.serialNumber}`
			});
			this.textContent = '移除關注';
			watchList.push(courseData.serialNumber);
		} else {
			console.log('remove watch');
			result = fetchApi('/watchdog', 'remove course from watch list', {
				method: 'POST',
				body: `studentID=${loginState.state.studentID}&removeCourseSerial=${courseData.serialNumber}`
			});
			this.textContent = '加入關注';
			watchList.splice(serialIndex, 1);
		}
	}

	function getWatchCourse() {
		if (!loginState.state || !loginState.state.login) {
			window.messageAlert.addInfo('需要登入來使用關注功能', '右上角登入按鈕登入', 3000)
			return;
		}
		fetchApi(`/watchdog?studentID=${loginState.state.studentID}`).then(i => {
			const eql = encodeURIComponent('&');
			watchList = [];
			Object.entries(i.data).forEach(i => i[1].forEach(j => watchList.push(i[0] + '-' + j)));
			const serialQuery = Object.entries(i.data).map(i => i[0] + '=' + i[1].join(',')).join(eql);
			search([['serial', serialQuery]], false);
		})
	}

	/**@this{{courseData: CourseData, key: string}}*/
	function addCourse() {
		const title = this.courseData.courseName + ' 加入';
		fetchApi('/courseFuncBtn?cosdata=' + encodeURIComponent(this.key), 'Add course').then(i => {
			if (!i.success) {
				window.messageAlert.addError(title + '失敗', i.msg, 20000);
				return;
			}

			window.messageAlert.addSuccess(title + '成功', i.msg, 5000);
		});
	}

	/**@this{{courseData: CourseData, key: string}}*/
	function addPreferenceEnter() {
		const courseData = this.courseData;
		const serialNumber = courseData.serialNumber;
		const preferenceEnterKey = this.key;

		const title = this.courseData.courseName + ' 加入志願';
		fetchApi('/courseFuncBtn?prekey=' + encodeURIComponent(preferenceEnterKey), 'Add pre-schedule').then(addPreResponse => {
			// Add pre-schedule success or already added
			const preScheduleNormal = addPreResponse.code === 1000 || addPreResponse.code === 4002;

			// Get pre-register list
			return fetchApi(`/courseRegister?mode=genEdu`, 'Get pre-register').then(({success, data}) => {
				if (!success) {
					window.messageAlert.addError(title + '失敗', '請再嘗試一次', 5000);
					return;
				}
				const course = data.courseList.find(i => i.serialNumber === serialNumber);
				// Course not in pre-register list
				if (!course) {
					window.messageAlert.addError(title + '失敗', preScheduleNormal ? '該課程已在志願登記中' : '請重新整理再嘗試一次', 5000);
					// Add pre-schedule because preference enter, remove it after failed
					if (addPreResponse.code === 1000) {
						getPreSchedule(function (data) {
							if (!data.schedule) return;
							const course = data.schedule.find(i => i.serialNumber === serialNumber);
							if (course && course.delete) removePreSchedule(courseData, course.delete);
						});
					}
					return;
				}
				// Add course to preference enter list
				fetchApi(`/courseRegister?mode=genEdu`, 'Add preference', {
					method: 'POST',
					body: `prechk=${course.prechk}&cosdata=${course.cosdata}&action=${data.action}&preSkip=${data.preSkip}`
				}).then(response => {
					if (!response.success) {
						window.messageAlert.addError(title + '失敗', response.msg, 10000);
						return;
					}

					window.messageAlert.addSuccess(title + '成功', response.msg, 5000);

					// Course already in pre-schedule, add it back after success
					if (addPreResponse.code === 4002)
						addPreSchedule(courseData, preferenceEnterKey);
				});
			});
		});
	}

	/**@this{{courseData: CourseData, key: string}}*/
	function addPreScheduleButtonClick() {
		const title = this.courseData.courseName + ' 加入預排';
		addPreSchedule(this.courseData, this.key,
			function (msg) {
				window.messageAlert.addSuccess(title + '成功', msg, 5000);
			},
			function (msg) {
				window.messageAlert.addError(title + '失敗', msg, 20000);
			}
		);
	}

	// Render result
	const courseRenderResult = [];
	let courseRenderResultFilter = [];
	const courseRenderResultDisplay = [];
	const expandButtons = [];
	const expandAllButton = th(null, 'expandDownArrow expand', expandArrowImage.cloneNode(), {onclick: expandAllToggle});
	let waitingResult = false;

	function expandAllToggle() {
		const expand = !expandAllButton.classList.toggle('expand');
		for (let expandButton of expandButtons)
			expandButton(expand);
	}

	// Sort
	const sortArrow = expandArrowImage.cloneNode();
	const sortArrowClass = new ClassList('sortArrow');
	sortArrowClass.init(sortArrow);
	let sortKey = null;
	let sortLastIndex = null;

	function resetSortArrow() {
		sortKey = null;
		if (sortArrow.parentElement)
			sortArrow.parentElement.removeChild(sortArrow);
		sortArrowClass.remove('reverse');
	}

	function sortResultItem(key, element, method) {
		/**@type{[CourseData, HTMLElement][]}*/
		const courseResult = courseRenderResultFilter;
		courseRenderResultDisplay.length = courseResult.length;
		let reverse = sortArrowClass.contains('reverse');
		if (sortKey !== key || reverse) {
			sortKey = key;
			courseResult.sort(method);
			sortLastIndex = courseResult.length;
			for (let i = courseResult.length - 1; i > -1; i--)
				if (!sortToEnd(courseResult[i][0][key])) {
					sortLastIndex = i + 1;
					break;
				}
			sortArrowClass.remove('reverse');
			reverse = false;
			element.appendChild(sortArrow);
		} else {
			reverse = true;
			sortArrowClass.add('reverse');
		}

		let i = 0;
		if (reverse)
			for (; i < sortLastIndex; i++)
				courseRenderResultDisplay[i] = courseResult[sortLastIndex - i - 1][1];

		for (; i < courseResult.length; i++)
			courseRenderResultDisplay[i] = courseResult[i][1];

		searchResultSignal.update();
	}

	function sortStringKey() {
		if (courseRenderResult.length > 0) {
			const key = this.key;
			sortResultItem(key, this, ([a], [b]) => sortToEnd(a[key]) ? 1 : sortToEnd(b[key]) ? -1 : a[key].localeCompare(b[key]));
		}
	}

	function sortIntKey() {
		if (courseRenderResult.length > 0) {
			const key = this.key;
			sortResultItem(key, this, ([a], [b]) => (sortToEnd(a[key]) ? 1 : sortToEnd(b[key]) ? -1 : b[key] - a[key]));
		}
	}

	function sortNckuHubKey() {
		if (nckuHubLoadingTaskCount !== 0)
			return;
		if (courseRenderResult.length > 0) {
			const key = this.key;
			const keys = ['sweet', 'cold', 'got'];
			keys.splice(keys.indexOf(key), 1);
			keys.unshift(key);

			sortResultItem(key, this, ([a], [b]) => {
				return sortNumberKeyOrder(a.nckuHub, b.nckuHub, keys);
			});

			userGuideTrigger.nckuHubSort();
		}
	}

	/**
	 * @param {Object.<string,number>} a
	 * @param {Object.<string,number>} b
	 * @param {string[]} keys
	 * @return {number}
	 */
	function sortNumberKeyOrder(a, b, keys) {
		for (let key of keys) {
			if (sortToEnd(a && a[key]))
				return 1;
			if (sortToEnd(b && b[key]))
				return -1;
			if (a[key].toFixed(1) !== b[key].toFixed(1))
				return b[key] - a[key];
		}
		return 0;
	}

	function sortToEnd(data) {
		return data === null || data === undefined || data.length === 0;
	}


	// Filter
	const hideEmptyColumnTool = hideEmptyColumn(courseRenderResult, () => searchTableHead);
	const classFilterOption = classFilter(updateFilter, courseRenderResult);
	const categoryFilterOption = categoryFilter(updateFilter, courseRenderResult);
	const tagFilterOption = tagFilter(updateFilter, courseRenderResult);
	const requireFilterOption = requireFilter(updateFilter);
	const filter = new Filter();
	/**@type {FilterOption[]}*/
	const filterOptions = [
		textSearchFilter(updateFilter),
		hideConflictCourseFilter(updateFilter, loginState),
		classFilterOption,
		categoryFilterOption,
		tagFilterOption,
		requireFilterOption,
		insureSectionRangeFilter(updateFilter, dayOfWeekSelectMenu, sectionSelectMenu),
		hidePracticeFilter(updateFilter),
		hideEmptyColumnTool,
	];
	filter.setOptions(filterOptions);

	/**
	 * @param {boolean} [firstRenderAfterSearch]
	 */
	function updateFilter(firstRenderAfterSearch) {
		if (courseRenderResult.length === 0)
			return;

		console.log('Update Filter');
		resetSortArrow();
		for (let i of filterOptions) {
			if (i.onFilterStart)
				i.onFilterStart(firstRenderAfterSearch);
		}
		courseRenderResultFilter = filter.updateFilter(courseRenderResult);
		courseRenderResultDisplay.length = 0;
		for (/**@type{[CourseData, HTMLElement]}*/const i of courseRenderResultFilter)
			courseRenderResultDisplay.push(i[1]);
		if (!firstRenderAfterSearch)
			searchResultSignal.update();
	}


	// Search result render
	let showResultLastIndex = 0;
	let showResultIndexStep = 30;

	function renderSearchResult(state) {
		if (state.loading) {
			waitingResult = true;
			courseRenderResult.length = 0;
			courseRenderResultDisplay.length = 0;
			expandButtons.length = 0;
			hideEmptyColumnTool.resetHeaderHide();
			classFilterOption.clear();
			requireFilterOption.reset();
			courseSearchResultInfo.textContent = '搜尋中...';
			return tbody('loading', td(null, null, {colSpan: totalColSpan},
				window.loadingElement.cloneNode(true),
				button('cancelBtn', '取消', () => {
					cancelSearchTask(searchTask[searchTask.length - 1], false);
					courseSearchResultInfo.textContent = '搜尋取消';
				})
			));
		}

		// No result
		if (!state.courseResult || state.courseResult.length === 0 || state.failed) {
			if (state.failed)
				courseSearchResultInfo.textContent = '搜尋失敗';
			else if (state.courseResult && state.courseResult.length === 0)
				courseSearchResultInfo.textContent = '沒有結果';

			return tbody();
		}

		if (waitingResult) {
			waitingResult = false;
			// Render result elements
			for (/**@type{CourseData}*/const data of state.courseResult) {
				const expandArrowStateClass = new ClassList('expandDownArrow', 'expand');
				const nckuHubResultData = state.nckuHubResult[data.serialNumber];
				const expandButton = expandArrowImage.cloneNode();
				expandButtons.push(toggleCourseInfo);

				// Check if registerCount
				let registerCount = null;
				if (data.registerCount !== undefined) {
					registerCount = td(null, 'registerCount', span('抽籤人數:', 'label'), data.registerCount === null ? null : text(data.registerCount.toString()));
				}

				// Course detail
				const courseDetailColSpan = registerCount ? totalColSpan - 1 : totalColSpan - 2;
				const expandableElement = div('expandable', div('info',
					div('splitLine'),

					// Course tags
					data.tags === null ? null : div('tags',
						data.tags.map(i => i.link
							? a(i.name, i.link, null, null, {
								style: 'background:' + i.color,
								target: '_blank'
							})
							: div(null, text(i.name), {style: 'background:' + i.color})
						)
					),

					// Note, limit
					data.courseNote === null ? null : span(data.courseNote, 'note'),
					data.courseLimit === null ? null : span(data.courseLimit, 'limit'),

					// Instructor
					div('instructor',
						span('教師姓名:', 'label'),
						data.instructors === null ? null : data.instructors.map(instructor =>
							!(instructor instanceof Object)
								? span(instructor, 'instructorBtnNoInfo')
								: button('instructorBtn',
									instructor.name,
									() => openInstructorDetailWindow(instructor),
									{
										onmouseenter: e => instructorInfoBubble.set({
											target: e.target,
											offsetY: router.element.scrollTop,
											data: instructor
										}),
										onmouseleave: instructorInfoBubble.hide
									}
								)
						)
					),

					!data.systemNumber ? null : div('systemNumber', span('課程碼', 'label'), span(data.systemNumber)),
					!data.attributeCode ? null : div('attributeCode', span('屬性碼', 'label'), span(data.attributeCode)),
				));
				const courseDetail = td(null, null, {colSpan: courseDetailColSpan, orgColSpan: courseDetailColSpan},
					expandableElement
				);

				function toggleCourseInfo(forceState) {
					if (typeof forceState === 'boolean' ? forceState : expandArrowStateClass.contains('expand')) {
						expandableElement.style.height = expandableElement.firstChild.offsetHeight + 'px';
						setTimeout(() => expandableElement.style.height = '0');
						expandArrowStateClass.remove('expand');
					} else {
						expandableElement.style.height = expandableElement.firstChild.offsetHeight + 'px';
						setTimeout(() => expandableElement.style.height = null, 200);
						expandArrowStateClass.add('expand');
					}
				}

				// NCKU Hub info
				const nckuHubInfo = nckuHubResultData && nckuHubResultData.signal
					? State(nckuHubResultData.signal, () => {
						if (data.nckuHub) {
							if (data.nckuHub.noData)
								return td('沒有資料', 'nckuHub', {colSpan: 3, onclick: userGuideTrigger.nckuHubCommentEmpty});
							const options = {colSpan: 3, onclick: openNckuHubDetailWindow};
							if (data.nckuHub.rate_count === 0)
								return td(null, 'nckuHub', options,
									div(null, span('收穫', 'label'), span('--', null, {style: 'color:' + textColor.orange})),
									div(null, span('甜度', 'label'), span('--', null, {style: 'color:' + textColor.orange})),
									div(null, span('涼度', 'label'), span('--', null, {style: 'color:' + textColor.orange})));
							return td(null, 'nckuHub', options,
								div(null, span('收穫', 'label'), nckuHubScoreToSpan(data.nckuHub.got)),
								div(null, span('甜度', 'label'), nckuHubScoreToSpan(data.nckuHub.sweet)),
								div(null, span('涼度', 'label'), nckuHubScoreToSpan(data.nckuHub.cold)),
							);
						}
						return td('載入中...', 'nckuHub', {colSpan: 3});
					})
					: td('沒有資料', 'nckuHub', {colSpan: 3, onclick: userGuideTrigger.nckuHubCommentEmpty})

				// Open NCKU Hub detail window
				function openNckuHubDetailWindow() {
					nckuHubDetailWindow.set(data);
					userGuideTrigger.nckuHubCommentOpen();
				}

				// render result item
				const courseResult = [
					tr('courseBlockSpacing', {style: 'display:none'}),
					// Info
					tr('courseInfoBlock', {style: 'display:none'},
						td(null, expandArrowStateClass, expandButton, {onclick: toggleCourseInfo}),
						td(null, 'detailedCourseName',
							a(null, createSyllabusUrl(data.semester, data.systemNumber), null, null, {target: '_blank'},
								span((data.serialNumber ? data.serialNumber + ' ' : '') + data.courseName))
						),
						td(data.departmentName, 'departmentName', {title: data.departmentName}),
						td(data.serialNumber, 'serialNumber'),
						td(null, 'category', span('類別:', 'label'), data.category && text(data.category)),
						td(null, 'grade', span('年級:', 'label'), data.courseGrade && text(data.courseGrade.toString())),
						td(null, 'classInfo', span('班別:', 'label'), data.classInfo && text(data.classInfo)),
						td(null, 'classGroup', span('組別:', 'label'), data.classGroup && text(data.classGroup)),
						td(null, 'courseTime', span('時間:', 'label'),
							data.time && data.time.map(i =>
								i.flexTimeDataKey
									? button(null, '詳細時間', openFlexTimeWindow, {key: i.flexTimeDataKey, courseData: data})
									: button(null, courseDataTimeToString(i))
							) || text(data.timeString)
						),
						td(null, 'courseName',
							a(null, createSyllabusUrl(data.semester, data.systemNumber), null, null, {target: '_blank'}, span(data.courseName))
						),
						td(null, 'required', span('選必修:', 'label'), data.required === null ? null : text(data.required ? '必修' : '選修')),
						td(null, 'credits', span('學分:', 'label'), data.credits === null ? null : text(data.credits.toString())),
						registerCount,
						td(null, 'selected', span('已選:', 'label'), data.selected === null ? null : span(data.selected)),
						td(null, 'available', span('餘額:', 'label'), createSelectAvailableStr(data)),
						nckuHubInfo,
						// Title sections
						td(null, 'options', {rowSpan: 2},
							!data.serialNumber || !loginState.state || !loginState.state.login ? null :
								button(null, watchList && watchList.indexOf(data.serialNumber) !== -1 ? '移除關注' : '加入關注', watchedCourseAddRemove, {courseData: data}),
							!data.preRegister ? null :
								button(null, '加入預排', addPreScheduleButtonClick, {courseData: data, key: data.preRegister}),
							!data.preferenceEnter ? null :
								button(null, '加入志願', addPreferenceEnter, {courseData: data, key: data.preRegister}),
							!data.addCourse ? null :
								button(null, '單科加選', addCourse, {courseData: data, key: data.addCourse}),
						),
					),
					// Details
					tr('courseDetailBlock', {style: 'display:none'}, courseDetail,)
				];
				courseRenderResult.push([data, courseResult]);
			}

			// Show register count column if available
			if (state.courseResult[0].registerCount !== undefined)
				registerCountLabel.classList.remove('hide');
			else
				registerCountLabel.classList.add('hide');

			// First filter update after result render
			updateFilter(true);

			setTimeout(userGuideTrigger.resultRender, 1000);
		}

		// Update display element
		courseSearchResultInfo.textContent = '搜尋到 ' + courseRenderResultDisplay.length + ' 個結果, 點擊欄位即可排序';
		showResultLastIndex = showResultIndexStep - 1;
		const len = Math.min(showResultLastIndex + 1, courseRenderResultDisplay.length);
		for (let i = 0; i < len; i++) {
			const item = courseRenderResultDisplay[i];
			item[0].style.display = item[1].style.display = item[2].style.display = '';
		}

		return tbody(null, courseRenderResultDisplay);
	}

	function openInstructorDetailWindow(info) {
		window.pageLoading.set(true);
		fetchApi(`/urschool?id=${info.id}&mode=${info.mode}`, 'Get UrSchool Data', {timeout: 10000}).then(response => {
			window.pageLoading.set(false);
			if (!response || !response.success || !response.data)
				return;

			/**@type UrSchoolInstructor*/
			const instructor = response.data;
			instructor.info = info;
			instructorDetailWindow.set(instructor);
			userGuideTrigger.urSchoolCommentOpen();
		});
	}

	/**@this{{key: string, courseData: CourseData}}*/
	function openFlexTimeWindow() {
		window.pageLoading.set(true);
		const courseData = this.courseData;
		fetchApi(`/courseFuncBtn?flexTimeKey=${encodeURIComponent(this.key)}`, 'Send key data', {timeout: 5000}).then(response => {
			window.pageLoading.set(false);
			if (!response || !response.success || !response.data)
				return;

			flexTimeWindow.set([courseData, response.data]);
		});
	}

	// Search page
	const nckuHubLoadingOverlay = [
		div('loadingOverlay', window.loadingElement.cloneNode(true)),
		div('loadingOverlay', window.loadingElement.cloneNode(true)),
		div('loadingOverlay', window.loadingElement.cloneNode(true)),
	];
	const registerCountLabel = th('抽籤人數', 'registerCount', {key: 'registerCount', onclick: sortIntKey, noHide: true});
	const searchTableHead = thead('noSelect',
		filter.createElement(),
		tr(null, th(null, 'resultCount', {colSpan: totalColSpan}, courseSearchResultInfo)),
		tr(null,
			expandAllButton,
			// th('Dept', 'departmentName', {key: 'departmentName', onclick: sortStringKey}),
			// th('Serial', 'serialNumber', {key: 'serialNumber', onclick: sortStringKey}),
			// th('Category', 'category', {key: 'category', onclick: sortStringKey}),
			// th('Grade', 'grade', {key: 'courseGrade', onclick: sortIntKey}),
			// th('Class', 'classInfo', {key: 'classInfo', onclick: sortStringKey}),
			// th('Time', 'classGroup', {key: 'classGroup', onclick: sortStringKey}),
			// th('Course name', 'courseTime', {key: 'timeString', onclick: sortStringKey}),
			// th('Required', 'courseName', {key: 'courseName', onclick: sortStringKey}),
			// th('Credits', 'required', {key: 'required', onclick: sortIntKey}),
			// th('Sel/Avail', 'credits', {key: 'credits', onclick: sortIntKey}),
			// // NckuHub
			// th('Reward', 'available', {key: 'available', onclick: sortIntKey}),
			// th('Sweet', 'nckuHub', {key: 'got', onclick: sortNckuHubKey}),
			// th('Cool', 'nckuHub', {key: 'sweet', onclick: sortNckuHubKey}),
			// // Function buttons
			// th('Options', 'options'),
			th('系所', 'departmentName', {key: 'departmentName', onclick: sortStringKey}),
			th('系-序號', 'serialNumber', {key: 'serialNumber', onclick: sortStringKey}),
			th('類別', 'category', {key: 'category', onclick: sortStringKey}),
			th('年級', 'grade', {key: 'courseGrade', onclick: sortIntKey}),
			th('班別', 'classInfo', {key: 'classInfo', onclick: sortStringKey}),
			th('組別', 'classGroup', {key: 'classGroup', onclick: sortStringKey}),
			th('時間', 'courseTime', {key: 'timeString', onclick: sortStringKey}),
			th('課程名稱', 'courseName', {key: 'courseName', onclick: sortStringKey}),
			th('選必修', 'required', {key: 'required', onclick: sortIntKey}),
			th('學分', 'credits', {key: 'credits', onclick: sortIntKey}),
			registerCountLabel,
			th('已選', 'selected', {key: 'selected', onclick: sortIntKey}),
			th('餘額', 'available', {key: 'available', onclick: sortIntKey}),
			// NCKU HUB
			th('收穫', 'nckuHub', {key: 'got', onclick: sortNckuHubKey, noHide: true}, nckuHubLoadingOverlay[0]),
			th('甜度', 'nckuHub', {key: 'sweet', onclick: sortNckuHubKey, noHide: true}, nckuHubLoadingOverlay[1]),
			th('涼度', 'nckuHub', {key: 'cold', onclick: sortNckuHubKey, noHide: true}, nckuHubLoadingOverlay[2]),
			// Function buttons
			th('功能', 'options'),
		),
	);

	const courseSearchPageElement = div('courseSearch',
		{onRender, onPageClose, onPageOpen},
		h1('課程查詢', 'title'),
		courseSearchForm,
		table('result', {cellPadding: 0},
			State(searchResultSignal, renderSearchResult),
			searchTableHead,
		),
		instructorInfoBubble,
	);

	router.element.addEventListener('scroll', function () {
		if (ieVersion) {
			// console.log(router.element.scrollTop, router.element.firstElementChild.offsetHeight - router.element.offsetHeight);
		}
		if (courseRenderResultDisplay.length > showResultLastIndex) {
			const lastShowedElement = courseRenderResultDisplay[showResultLastIndex][2];
			const resultTableOffset = searchTableHead.parentElement.offsetTop;
			const lastShowedElementBottomOffsetTop = lastShowedElement.offsetTop + lastShowedElement.offsetHeight + resultTableOffset;
			const screenBottomOffsetTop = router.element.scrollTop + router.element.offsetHeight;

			if (screenBottomOffsetTop / lastShowedElementBottomOffsetTop > 0.9) {
				const pShowResultLastIndex = showResultLastIndex;
				showResultLastIndex += showResultIndexStep;
				for (let i = pShowResultLastIndex + 1; i < courseRenderResultDisplay.length; i++) {
					const item = courseRenderResultDisplay[i];
					const display = i > showResultLastIndex ? 'none' : '';
					item[0].style.display = item[1].style.display = item[2].style.display = display;
				}
			}
		}
	});

	const instructorDetailWindow = new InstructorDetailWindow(courseSearchPageElement, userGuideTrigger);
	const nckuHubDetailWindow = new NckuHubDetailWindow(courseSearchPageElement, userGuideTrigger);
	const flexTimeWindow = new FlexTimeWindow(courseSearchPageElement);

	return courseSearchPageElement;
};

/**
 * @param {UrSchoolInstructorSimple}instructor
 */
function instructorInfoElement(instructor) {
	return div('instructorInfo',
		div('rate',
			instructor.recommend !== -1 && instructor.reward !== -1 && instructor.articulate !== -1 && instructor.pressure !== -1 && instructor.sweet !== -1
				? table(null,
					// tr(null, th('Recommend'), th('Reward'), th('Articulate'), th('Pressure'), th('Sweet')),
					tr(null, th('推薦'), th('收穫'), th('口條'), th('壓力'), th('分數甜度')),
					tr(null,
						td(instructor.recommend, getColor(instructor.recommend)),
						td(instructor.reward, getColor(instructor.reward)),
						td(instructor.articulate, getColor(instructor.articulate)),
						th(instructor.pressure, getColor(6 - instructor.pressure)),
						td(instructor.sweet, getColor(instructor.sweet))
					),
				)
				: null,
		),
		div('info',
			table(null,
				// tr(null, th('Average score'), td(averageScore)),
				// tr(null, th('Note'), td(note)),
				// tr(null, th('Nickname'), td(nickname)),
				// tr(null, th('Department'), td(department)),
				// tr(null, th('Job title'), td(jobTitle)),
				// tr(null, th('Roll call method'), td(rollCallMethod)),
				// tr(null, th('Academic qualifications'), td(qualifications)),
				tr(null, th('平均成績'), td(instructor.averageScore)),
				tr(null, th('值得一提'), td(instructor.note)),
				tr(null, th('綽號'), td(instructor.nickname)),
				tr(null, th('系所'), td(instructor.department)),
				tr(null, th('職稱'), td(instructor.jobTitle)),
				tr(null, th('點名方式'), td(instructor.rollCallMethod)),
				tr(null, th('最高學歷'), td(instructor.qualifications)),
			),
		)
	);
}

function InstructorInfoBubble() {
	const signal = new Signal();
	const classList = new ClassList('instructorInfoOffset');
	const offsetElement = div(classList,
		State(signal, /**@param{target:any, data: UrSchoolInstructorSimple, offsetY: float}state*/state => {
			if (!state) return div();

			const bound = state.target.getBoundingClientRect();
			/**@type UrSchoolInstructorSimple*/
			const instructor = state.data;
			const element = instructorInfoElement(instructor);
			element.insertBefore(span(instructor.name), element.firstChild);

			offsetElement.style.left = bound.left + 'px';
			offsetElement.style.top = (bound.top + state.offsetY - 40) + 'px';
			classList.add('show');
			return element;
		})
	);
	offsetElement.set = signal.set;
	offsetElement.hide = () => classList.remove('show');
	return offsetElement;
}

function InstructorDetailWindow(courseSearch, userGuideTrigger) {
	const popupWindow = new PopupWindow({root: courseSearch, onclose: userGuideTrigger.urSchoolCommentClose});
	this.set = function (/**@param{UrSchoolInstructor}instructor*/instructor) {
		const instructorInfo = instructorInfoElement(instructor.info);
		popupWindow.windowSet(div('instructorDetailWindow',
			div('title',
				span(instructor.info.department),
				span(instructor.info.name),
				span(instructor.info.jobTitle),
			),
			div('tags',
				instructor.tags.map(i => {
					return span(i[1]);
				})
			),
			div('reviewerCount',
				span(instructor.reviewerCount.toString()),
				span('人共同評分'),
			),
			instructorInfo,
			div('comments',
				instructor.comments.map(i => {
					return div('item',
						img(`https://graph.facebook.com/v2.8/${i.profile}/picture?type=square`, '', 'profile'),
						div('body',
							span(i.created_at, 'createDate'),
							span(i.body, 'message'),
						),
					);
				})
			),
		));
		popupWindow.windowOpen();
	};
}

function getColor(number) {
	return number < 2 ? 'red' : number < 4 ? 'yellow' : 'blue';
}

export function NckuHubDetailWindow(courseSearch, userGuideTrigger) {
	const popupWindow = new PopupWindow({root: courseSearch, onclose: userGuideTrigger && userGuideTrigger.nckuHubCommentClose});

	this.set = function (/**@param{CourseData}courseData*/courseData) {
		const nckuHub = courseData.nckuHub;
		popupWindow.windowSet(div('nckuHubDetailWindow',
			div('courseInfoPanel',
				span(courseData.serialNumber),
				span(courseData.courseName),
				span(courseData.timeString),
			),
			div('nckuHubPanel',
				// rates
				span('課程評分 (' + nckuHub.rate_count + ')', 'title'),
				nckuHub.rate_count === 0 ? div('rates') : div('rates',
					div(null, div('rateBox',
						span('收穫'),
						nckuHubScoreToSpan(nckuHub.got),
					)),
					div(null, div('rateBox',
						span('甜度'),
						nckuHubScoreToSpan(nckuHub.sweet),
					)),
					div(null, div('rateBox',
						span('涼度'),
						nckuHubScoreToSpan(nckuHub.cold),
					)),
				),
				// comment
				span('課程心得 (' + nckuHub.comments.length + ')', 'title'),
				div('comments',
					nckuHub.comments.map(comment => div('commentBlock',
						span(comment.semester, 'semester'),
						p(comment.comment, 'comment'),
					)),
				),
			)
		));
		popupWindow.windowOpen();
	};
}

function FlexTimeWindow(courseSearch) {
	const popupWindow = new PopupWindow({root: courseSearch});

	/**
	 * @param {CourseData} courseData
	 * @param {FlexTimeData[]} timeData
	 */
	this.set = function ([courseData, timeData]) {
		popupWindow.windowSet(div('flexTimeWindow',
			div('courseInfoPanel',
				span(courseData.serialNumber),
				span(courseData.courseName),
				span(courseData.timeString),
			),
			table('timeTable',
				tr(null, th('日期'), th('開始時間'), th('結束時間')),
				timeData.map(i => tr(null,
					td(i.date),
					td(i.timeStart),
					td(i.timeEnd),
				))
			)
		));
		popupWindow.windowOpen();
	};
}

/**
 * @typedef FilterOption
 * @property {function(firstRenderAfterSearch: boolean): void} [onFilterStart] Call before filter start
 * @property {function(item: any): boolean} [condition] Check item to show
 * @property {HTMLElement|HTMLElement[]} element
 * @property {boolean} [fullLine]
 */
/**
 * Filter tool bar
 * @constructor
 */
function Filter() {
	let /**@type{FilterOption[]}*/options = null;

	this.setOptions = function (filterOptions) {
		options = filterOptions;
	};

	this.createElement = function () {
		const rows = [];
		let row = null;
		for (const option of options) {
			if (!row)
				row = tr(null, th(null, 'filterOptions', {colSpan: totalColSpan}));
			if (option.element instanceof Array)
				for (const element of option.element)
					row.firstElementChild.appendChild(element);
			else
				row.firstElementChild.appendChild(option.element);

			if (option.fullLine) {
				rows.push(row);
				row = null;
			}
		}
		if (row)
			rows.push(row);
		return rows;
	}

	/**
	 * Apply filter
	 * @param {any[]} items
	 * @return {any[]}
	 */
	this.updateFilter = function (items) {
		const courseRenderResultFilter = [];
		for (const i of items) {
			let pass = true;
			for (const j of options) {
				if (j.condition && !j.condition(i)) {
					pass = false;
					break;
				}
			}
			if (pass)
				courseRenderResultFilter.push(i);
		}
		return courseRenderResultFilter;
	}
}

/**
 * @param {function()} onFilterUpdate
 * @return {FilterOption}
 */
function textSearchFilter(onFilterUpdate) {
	const searchInput = input(null, '篩選課程', null, {
		type: 'search',
		oninput: textSearchFilterChange
	});
	let textSearchFilterKeys = [];
	let lastTextSearchFilterKey = null;

	function textSearchFilterChange() {
		const key = searchInput.value.trim();
		// if word not finish
		if (key.length > 0 && !key.match(/^[\u4E00-\u9FFF（）\w -]+$/g))
			return;

		// if same
		if (lastTextSearchFilterKey === key)
			return;
		lastTextSearchFilterKey = key;
		textSearchFilterKeys = key.length === 0 ? [] : key.split(' ');
		onFilterUpdate();
	}

	/**@param{CourseData}courseData*/
	function condition([courseData]) {
		if (textSearchFilterKeys.length === 0)
			return true;
		return findIfContains(courseData.courseName, textSearchFilterKeys) ||
			findIfContains(courseData.serialNumber, textSearchFilterKeys) ||
			findIfContains(courseData.classInfo, textSearchFilterKeys) ||
			courseData.instructors && courseData.instructors.find(i =>
				findIfContains(i instanceof Object ? i.name : i, textSearchFilterKeys))
	}

	function findIfContains(data, keys) {
		if (!data) return false;
		for (const key of keys)
			if (key.length === 0 || data.indexOf(key) !== -1) return true;
		return false;
	}

	return {
		condition: condition,
		element: label('searchBar', null,
			img('./res/assets/funnel_icon.svg', ''),
			searchInput,
		),
		fullLine: true,
	};
}

/**
 * @param {function()} onFilterUpdate
 * @param {Signal} loginState
 * @return {FilterOption}
 */
function hideConflictCourseFilter(onFilterUpdate, loginState) {
	const checkBoxOuter = checkboxWithName(null, '隱藏衝堂', false, hideConflictFilterChange);
	const checkBox = checkBoxOuter.input;
	let fetchingData = false;
	let /**@type{CourseDataTime[]}*/timeData = null;

	/**@param{CourseData}courseData*/
	function condition([courseData]) {
		if (!checkBox.checked || !courseData.time)
			return true;

		for (const cosTime of courseData.time) {
			if (!cosTime.sectionStart)
				continue;
			const sectionStart = cosTime.sectionStart;
			const sectionEnd = cosTime.sectionEnd ? cosTime.sectionEnd : sectionStart;

			for (const usedCosTime of timeData) {
				if (cosTime.dayOfWeek !== usedCosTime.dayOfWeek)
					continue;

				if (sectionStart >= usedCosTime.sectionStart && sectionStart <= usedCosTime.sectionEnd ||
					sectionEnd >= usedCosTime.sectionStart && sectionEnd <= usedCosTime.sectionEnd ||
					sectionStart <= usedCosTime.sectionStart && sectionEnd >= usedCosTime.sectionEnd)
					return false;
			}
		}

		return true;
	}

	function hideConflictFilterChange() {
		// If no data
		if (checkBox.checked) {
			// If not login
			if (!loginState.state || !loginState.state.login) {
				checkBox.checked = false;
				window.askForLoginAlert();
			} else if (!fetchingData) {
				fetchingData = true;
				fetchApi('/courseSchedule', 'Get schedule').then(response => {

					if (!response || !response.success || !response.data) {
						checkBox.checked = false;
						timeData = null;
						return;
					}

					// Parse time data
					const usedTime = [];
					for (const i of response.data.schedule) {
						for (const time of i.time) {
							if (time.sectionStart === null)
								continue;
							if (time.sectionEnd === null)
								time.sectionEnd = time.sectionStart;
							usedTime.push(time);
						}
					}
					timeData = usedTime;
					fetchingData = false;
					// console.log(usedTime);

					onFilterUpdate();
				});
			}
		}

		if (timeData)
			onFilterUpdate();
	}

	return {
		condition: condition,
		element: checkBoxOuter,
	};
}

/**
 * @param {function()} onFilterUpdate
 * @param {SelectMenu} dayOfWeekSelectMenu
 * @param {SelectMenu} sectionSelectMenu
 * @return {FilterOption}
 */
function insureSectionRangeFilter(onFilterUpdate, dayOfWeekSelectMenu, sectionSelectMenu) {
	const checkBoxOuter = checkboxWithName(null, '確保節次範圍', true, onchange);
	const checkBox = checkBoxOuter.input;

	let searchDayOfWeek = -1;
	let searchSectionStart = -1, searchSectionEnd = -1;

	function onFilterStart(firstRenderAfterSearch) {
		if (firstRenderAfterSearch)
			updateSearchSection();
	}

	/**@param{CourseData}courseData*/
	function condition([courseData]) {
		if (!checkBox.checked || (searchSectionStart === -1 && searchDayOfWeek === -1))
			return true;

		if (!courseData.time)
			return false;
		for (const cosTime of courseData.time) {
			if (searchDayOfWeek !== -1 && cosTime.dayOfWeek !== searchDayOfWeek)
				return false;
			if (searchSectionStart !== -1) {
				if (!cosTime.sectionStart)
					continue;
				const sectionStart = cosTime.sectionStart;
				const sectionEnd = cosTime.sectionEnd ? cosTime.sectionEnd : sectionStart;
				if (sectionStart < searchSectionStart || sectionEnd > searchSectionEnd)
					return false;
			}
		}
		return true;
	}

	function updateSearchSection() {
		let searchSection = sectionSelectMenu.getSelectedValue();
		if (searchSection.length !== 0) {
			searchSectionStart = searchSectionEnd = parseInt(searchSection[0]);
			for (let i of searchSection) {
				i = parseInt(i);
				if (i < searchSectionStart)
					searchSectionStart = i;
				else if (i > searchSectionEnd)
					searchSectionEnd = i;
			}
		} else
			searchSectionStart = -1;

		let searchDayOfWeekV = dayOfWeekSelectMenu.getSelectedValue();
		if (searchDayOfWeekV.length !== 0) {
			searchDayOfWeek = parseInt(searchDayOfWeekV[0]);
		} else
			searchDayOfWeek = -1;
	}

	function onchange() {
		updateSearchSection();
		if (searchSectionStart !== -1 || searchDayOfWeek !== -1)
			onFilterUpdate();
	}

	return {
		onFilterStart: onFilterStart,
		condition: condition,
		element: checkBoxOuter,
	}
}

/**
 * @param {function()} onFilterUpdate
 * @return {FilterOption}
 */
function hidePracticeFilter(onFilterUpdate) {
	const checkBoxOuter = checkboxWithName(null, '隱藏實習', true, () => onFilterUpdate());
	const checkBox = checkBoxOuter.input;

	/**@param{CourseData}courseData*/
	function condition([courseData]) {
		if (!checkBox.checked)
			return true;
		return courseData.category !== '實習' && courseData.category !== 'Practice' ||
			courseData.courseName.length > 0 ||
			courseData.serialNumber;
	}

	return {
		condition: condition,
		element: checkBoxOuter,
	}
}

/**
 * @param {function()} onFilterUpdate
 * @param {any[]} courseRenderResult
 * @return {FilterOption & {clear: function()}}
 */
function classFilter(onFilterUpdate, courseRenderResult) {
	const selectMenu = new SelectMenu('班別過濾', 'classFilter', 'classFilter', null, {multiple: true});
	let selectValue = null;
	let isEmpty = false;
	selectMenu.onSelectItemChange = updateSelectValue;

	function clearItems() {
		selectMenu.clearItems();
	}

	function updateSelectValue() {
		selectValue = selectMenu.getSelectedValue();
		onFilterUpdate();
	}

	function onFilterStart(firstRenderAfterSearch) {
		if (!firstRenderAfterSearch)
			return;

		let classCategory = [];
		let courseNoClassInfo = false;
		for (const [i] of courseRenderResult) {
			if (!i.classInfo)
				courseNoClassInfo = true;
			else if (classCategory.indexOf(i.classInfo) === -1)
				classCategory.push(i.classInfo);
		}
		classCategory = classCategory.map(i => [i, i]);
		if (classCategory.length > 0 && courseNoClassInfo)
			classCategory.push(['', '無班別']);

		// Class info empty
		isEmpty = classCategory.length === 0;
		selectMenu.element.style.display = isEmpty ? 'none' : '';
		selectMenu.setItems(classCategory, true);
		selectValue = selectMenu.getSelectedValue();
	}

	/**@param{CourseData}courseData*/
	function condition([courseData]) {
		if (!selectValue || isEmpty)
			return true;
		if (selectValue.length === 0)
			return false;
		if (!courseData.classInfo)
			return selectValue.indexOf('') !== -1;

		return selectValue.indexOf(courseData.classInfo) !== -1;
	}

	return {
		clear: clearItems,
		onFilterStart: onFilterStart,
		condition: condition,
		element: selectMenu.element,
	}
}

/**
 * @param {function()} onFilterUpdate
 * @param {any[]} courseRenderResult
 * @return {FilterOption & {clear: function()}}
 */
function categoryFilter(onFilterUpdate, courseRenderResult) {
	const selectMenu = new SelectMenu('類別過濾', 'categoryFilter', 'categoryFilter', null, {multiple: true});
	let selectValue = null;
	let isEmpty = false;
	selectMenu.onSelectItemChange = updateSelectValue;

	function clearItems() {
		selectMenu.clearItems();
	}

	function updateSelectValue() {
		selectValue = selectMenu.getSelectedValue();
		onFilterUpdate();
	}

	function onFilterStart(firstRenderAfterSearch) {
		if (!firstRenderAfterSearch)
			return;

		let classCategory = [];
		let courseNoClassInfo = false;
		for (const [i] of courseRenderResult) {
			if (!i.category)
				courseNoClassInfo = true;
			else if (classCategory.indexOf(i.category) === -1)
				classCategory.push(i.category);
		}
		classCategory = classCategory.map(i => [i, i]);
		if (classCategory.length > 0 && courseNoClassInfo)
			classCategory.push(['', '無類別']);

		// Category info empty
		isEmpty = classCategory.length === 0;
		selectMenu.element.style.display = isEmpty ? 'none' : '';
		selectMenu.setItems(classCategory, true);
		selectValue = selectMenu.getSelectedValue();
	}

	/**@param{CourseData}courseData*/
	function condition([courseData]) {
		if (!selectValue || isEmpty)
			return true;
		if (selectValue.length === 0)
			return false;
		if (!courseData.category)
			return selectValue.indexOf('') !== -1;
		return selectValue.indexOf(courseData.category) !== -1;
	}

	return {
		clear: clearItems,
		onFilterStart: onFilterStart,
		condition: condition,
		element: selectMenu.element,
	}
}

/**
 * @param {function()} onFilterUpdate
 * @param {any[]} courseRenderResult
 * @return {FilterOption & {clear: function()}}
 */
function tagFilter(onFilterUpdate, courseRenderResult) {
	const selectMenu = new SelectMenu('標籤過濾', 'tagFilter', 'tagFilter', null, {multiple: true});
	let selectValue = null;
	let isEmpty = false;
	selectMenu.onSelectItemChange = updateSelectValue;

	function clearItems() {
		selectMenu.clearItems();
	}

	function updateSelectValue() {
		selectValue = selectMenu.getSelectedValue();
		onFilterUpdate();
	}

	function onFilterStart(firstRenderAfterSearch) {
		if (!firstRenderAfterSearch)
			return;

		let courseTags = [];
		let courseNoTag = false;
		for (const [i] of courseRenderResult) {
			if (!i.tags)
				courseNoTag = true;
			else {
				for (const tag of i.tags)
					if (courseTags.indexOf(tag.name) === -1)
						courseTags.push(tag.name);
			}
		}
		courseTags = courseTags.map(i => [i, i]);
		if (courseTags.length > 0 && courseNoTag)
			courseTags.push(['', '無標籤']);

		// Category info empty
		isEmpty = courseTags.length === 0;
		selectMenu.element.style.display = isEmpty ? 'none' : '';
		selectMenu.setItems(courseTags, true);
		selectValue = selectMenu.getSelectedValue();
	}

	/**@param{CourseData}courseData*/
	function condition([courseData]) {
		if (!selectValue || isEmpty)
			return true;
		if (selectValue.length === 0)
			return false;
		if (!courseData.tags)
			return selectValue.indexOf('') !== -1;
		for (const tag of courseData.tags)
			if (selectValue.indexOf(tag.name) !== -1)
				return true;
		return false;
	}

	return {
		clear: clearItems,
		onFilterStart: onFilterStart,
		condition: condition,
		element: selectMenu.element,
	}
}

/**
 * @param {function()} onFilterUpdate
 * @return {FilterOption & {reset: function()}}
 */
function requireFilter(onFilterUpdate) {
	const selectMenu = new SelectMenu('選必修過濾', 'requireFilter', 'requireFilter', null, {
		multiple: true,
		sortByValue: false
	});
	selectMenu.setItems([[true, '必修'], [false, '選修']], true);
	selectMenu.onSelectItemChange = updateSelectValue;
	let selectValue = null;

	function reset() {
		selectMenu.selectItemByValue([true, false]);
	}

	function updateSelectValue() {
		selectValue = selectMenu.getSelectedValue();
		onFilterUpdate();
	}

	/**@param{CourseData}courseData*/
	function condition([courseData]) {
		if (!selectValue || courseData.required == null)
			return true;
		return selectValue.indexOf(courseData.required) !== -1;
	}

	return {
		reset: reset,
		condition: condition,
		element: selectMenu.element,
	}
}

/**
 * @param {any[]} courseRenderResult
 * @param {function(): HTMLHeadElement} getHeader
 * @return {FilterOption & {resetHeaderHide: function()}}
 */
function hideEmptyColumn(courseRenderResult, getHeader) {
	const checkBoxOuter = checkboxWithName(null, '隱藏空欄位', true, updateHideElement);
	const checkBox = checkBoxOuter.input;
	let headerCols = null;
	const hideElements = [];
	const courseDetailElements = [];
	const emptyColIndex = [];

	function onFilterStart(firstRenderAfterSearch) {
		if (firstRenderAfterSearch)
			updateColumn();
	}

	function updateColumn() {
		console.log('Hide Empty Column');
		hideElements.length = 0;
		courseDetailElements.length = 0;
		emptyColIndex.length = 0;

		for (let i = 1, j = 0; i < headerCols.length - 1; i++) {
			const hCol = headerCols[i];
			if (hCol.classList.contains('hide'))
				continue;

			let allEmpty = true;
			for (let [courseData] of courseRenderResult) {
				if (hCol.noHide) {
					allEmpty = false;
					break;
				}
				if (courseData[hCol.key] !== null) {
					allEmpty = false;
					break;
				}
			}

			if (allEmpty) {
				emptyColIndex.push(j);
				hideElements.push(hCol);
			}
			j++;
		}

		for (let [, elements] of courseRenderResult) {
			courseDetailElements.push(elements[2].firstElementChild);

			for (const i of emptyColIndex)
				hideElements.push(elements[1].children[i + 2]);
		}

		updateHideElement();
	}

	function updateHideElement() {
		if (checkBox.checked) {
			for (let i of hideElements)
				i.classList.add('hide');
			for (let i of courseDetailElements)
				i.colSpan = i.orgColSpan - emptyColIndex.length;
		} else {
			for (let i of hideElements)
				i.classList.remove('hide');
			for (let i of courseDetailElements)
				i.colSpan = i.orgColSpan;
		}
	}

	function resetHeaderHide() {
		if (!headerCols)
			headerCols = getHeader().lastElementChild.children;
		else
			for (const col of headerCols) {
				col.classList.remove('hide');
			}
	}

	return {
		onFilterStart: onFilterStart,
		resetHeaderHide: resetHeaderHide,
		element: checkBoxOuter,
	}
}