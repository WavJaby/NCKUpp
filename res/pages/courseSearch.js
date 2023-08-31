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
 * @typedef CourseData
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
 * @property {int} selected
 * @property {int} available
 * @property {CourseDataTime[]|null} time
 * @property {string} timeString
 * @property {string} moodle
 * @property {string} preferenceEnter
 * @property {string} addCourse
 * @property {string} preRegister
 * @property {string} addRequest
 * @property {NckuHub|null} nckuhub
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
 * @property {string | null} extraTimeDataKey
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
 * @property {Object.<int, NckuHubRateObject>} parsedRates
 */
/**
 * @typedef {Object} NckuHubRaw
 * @property {string} got
 * @property {string} sweet
 * @property {string} cold
 * @property {int} rate_count
 * @property {NckuHubCommentObject[]} comment
 * @property {NckuHubRateObject[]} rates
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


import {
	a,
	button,
	checkboxWithName,
	ClassList,
	colgroup,
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
} from '../domHelper_v002.min.js';

import SelectMenu from '../selectMenu.js';
import {courseDataTimeToString, fetchApi, parseRawCourseData, timeParse} from '../lib.js';
import PopupWindow from '../popupWindow.js';

const textColor = {
	red: '#fa2b16',
	orange: '#ff8633',
	green: '#62cc38'
};

/**
 * @param {QueryRouter} router
 * @param {Signal} loginState
 * @return {HTMLDivElement}
 */
export default function (router, loginState) {
	console.log('Course search Init');
	const styles = mountableStylesheet('./res/pages/courseSearch.css');
	const expandArrowImage = img('./res/assets/down_arrow_icon.svg', 'Expand button');
	expandArrowImage.className = 'noSelect noDrag';

	const searchResultSignal = new Signal({loading: false, courseResult: null, nckuhubResult: null});
	const instructorInfoBubble = InstructorInfoBubble();

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
			loadLastSearch();
		});
	}

	function onPageOpen(isHistory) {
		console.log('Course search Open');
		styles.enable();
		if (isHistory)
			loadLastSearch();

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
			if (state)
				window.askForLoginAlert();
			watchList = null;
		}
	}

	function loadLastSearch() {
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

		if (rawQuery && rawQuery.length > 0)
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
		// input(null, 'Serial number', 'serial', {onkeyup}),
		input(null, '課程名稱', 'courseName', {onkeyup}),
		deptNameSelectMenu.element,
		input(null, '教師姓名', 'instructor', {onkeyup}),
		new SelectMenu('年級', 'grade', 'grade', [['1', '1'], ['2', '2'], ['3', '3'], ['4', '4'], ['5', '5'], ['6', '6'], ['7', '7']], {searchBar: false}).element,
		dayOfWeekSelectMenu.element,
		sectionSelectMenu.element,
		button(null, '搜尋', search),
		button(null, '關注列表', getWatchCourse),
	);

	/**
	 * @param {string[][]} [rawQuery] [key, value][]
	 * @param {boolean} [saveQuery] Will save query string if not provide or true
	 * @return {void}
	 */
	async function search(rawQuery, saveQuery) {
		if (searching) return;
		searching = true;
		searchResultSignal.set({loading: true, courseResult: null, nckuhubResult: null});

		// get all course ID
		const getAvalibleNckuHubID = avalibleNckuHubCourseID === null ? fetchApi('/nckuhub', 'get nckuhub id') : null;
		// get urSchool data
		const getUrSchoolData = urSchoolData === null ? fetchApi('/urschool', 'get urschool data') : null;
		if (getAvalibleNckuHubID)
			avalibleNckuHubCourseID = (await getAvalibleNckuHubID).data;
		if (getUrSchoolData)
			urSchoolData = (await getUrSchoolData).data;

		// Get queryData
		let queryData = rawQuery instanceof Event ? null : rawQuery;
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

		if (queryData.length === 0) {
			window.messageAlert.addInfo('課程搜尋', '請輸入搜尋資料', 2000);
			lastQueryString = queryString;
			searchResultSignal.set({loading: false, courseResult: null, nckuhubResult: null, failed: true});
			searching = false;
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

		// fetch data
		const result = (await fetchApi('/search?' + queryString, 'Searching', {timeout: 10000}));

		if (!result || !result.success || !result.data) {
			searchResultSignal.set({loading: false, courseResult: null, nckuhubResult: null, failed: true});
			searching = false;
			return;
		}

		// Parse result
		if (!(result.data instanceof Array)) {
			const arr = [];
			for (const i of Object.values(result.data)) {
				for (const j of i)
					arr.push(j);
			}
			result.data = arr;
		}

		const nckuhubResult = {};
		/**@type CourseData[]*/
		const courseResult = [];
		for (const rawCourseData of result.data) {
			const courseData = parseRawCourseData(rawCourseData, urSchoolData);
			courseResult.push(courseData);

			if (courseData.serialNumber != null) {
				let nckuHubID = avalibleNckuHubCourseID.indexOf(courseData.serialNumber) !== -1;
				if (nckuHubID) nckuhubResult[courseData.serialNumber] = {courseData, signal: new Signal()};
			}
		}

		// Get nckuhub data
		const chunkSize = 20;
		const courseSerialNumbers = Object.keys(nckuhubResult);
		for (let i = 0; i < courseSerialNumbers.length; i += chunkSize) {
			const chunk = courseSerialNumbers.slice(i, i + chunkSize);
			fetchApi('/nckuhub?id=' + chunk.join(',')).then(response => {
				for (let data of Object.entries(response.data)) {
					const {/**@type CourseData*/courseData, /**@type Signal*/signal} = nckuhubResult[data[0]];
					/**@type NckuHubRaw*/
					const nckuhub = data[1];
					courseData.nckuhub = /**@type NckuHub*/ {
						noData: nckuhub.rate_count === 0 && nckuhub.comment.length === 0,
						got: parseFloat(nckuhub.got),
						sweet: parseFloat(nckuhub.sweet),
						cold: parseFloat(nckuhub.cold),
						rate_count: nckuhub.rate_count,
						comments: nckuhub.comment,
						parsedRates: nckuhub.rates.reduce((a, v) => {
							a[v.post_id] = v;
							return a;
						}, {})
					};
					signal.update();
				}
			});
		}

		console.log(courseResult);
		searchResultSignal.set({loading: false, courseResult, nckuhubResult});
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
		result.then(i => {
			console.log(i);
		});
	}

	function getWatchCourse() {
		if (!loginState.state || !loginState.state.login) {
			window.askForLoginAlert();
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

	/**
	 * @this {{cosdata: string}}
	 */
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

	/**
	 * @this {{prekey: string}}
	 */
	function sendPreKey() {
		const title = this.courseData.courseName + ' 加入預排';
		fetchApi(`/courseFuncBtn?prekey=${encodeURIComponent(this.key)}`, 'Send key data').then(i => {
			if (i.success)
				window.messageAlert.addSuccess(title + '成功', i.msg, 5000);
			else {
				const d = div();
				d.innerHTML = i.msg;
				window.messageAlert.addErrorElement(title + '失敗', d, 20000);
			}
		});
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
		let reverse;
		if (sortKey !== key) {
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
		} else
			reverse = sortArrowClass.toggle('reverse');

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

	function sortNckuhubKey() {
		if (courseRenderResult.length > 0) {
			const key = this.key;
			const keys = ['sweet', 'cold', 'got'];
			keys.splice(keys.indexOf(key), 1);
			keys.unshift(key);

			sortResultItem(key, this, ([a], [b]) => {
				return sortNumberKeyOrder(a.nckuhub, b.nckuhub, keys);
			});
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


	// Filter
	const filter = new Filter();
	/**@type {FilterOption[]}*/
	const filterOptions = [
		textSearchFilter(updateFilter),
		hideConflictCourseFilter(updateFilter, loginState),
		insureSectionRangeFilter(updateFilter, dayOfWeekSelectMenu, sectionSelectMenu),
		hidePracticeFilter(updateFilter),
		classFilter(updateFilter, courseRenderResult),
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
			courseSearchResultInfo.textContent = '搜尋中...';
			return window.loadingElement.cloneNode(true);
		}

		// No result
		if (!state.courseResult || state.courseResult.length === 0 || state.failed) {
			if (state.failed)
				courseSearchResultInfo.textContent = '搜尋失敗';
			else if (state.courseResult && state.courseResult.length === 0)
				courseSearchResultInfo.textContent = '沒有結果';

			return div();
		}

		if (waitingResult) {
			waitingResult = false;
			// Render result elements
			for (/**@type{CourseData}*/const data of state.courseResult) {
				const expandArrowStateClass = new ClassList('expandDownArrow', 'expand');
				const nckuhubResultData = state.nckuhubResult[data.serialNumber];
				const expandButton = expandArrowImage.cloneNode();
				expandButtons.push(toggleCourseInfo);

				// Course detail
				let expandableHeightReference, expandableElement;
				const courseDetail = td(null, null, {colSpan: 14},
					expandableElement = div('expandable', expandableHeightReference = div('info',
						div('splitLine'),
						// Course tags
						data.tags === null ? null : div('tags',
							data.tags.map(i => i.link
								? a(i.name, i.link, null, null, {
									style: 'background-color:' + i.color,
									target: '_blank'
								})
								: div(null, text(i.name), {style: 'background-color:' + i.color})
							)
						),

						// Note, limit
						data.courseNote === null ? null : span(data.courseNote, 'note'),
						data.courseLimit === null ? null : span(data.courseLimit, 'limit red'),

						// Instructor
						span('教師姓名: ', 'instructor'),
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
					))
				);

				// nckuhub info
				const nckuhubInfo = nckuhubResultData && nckuhubResultData.signal
					? State(nckuhubResultData.signal, () => {
						if (data.nckuhub) {
							if (data.nckuhub.noData)
								return td('沒有資料', 'nckuhub', {colSpan: 3});
							const options = {colSpan: 3, onclick: openNckuhubDetailWindow};
							if (data.nckuhub.rate_count === 0)
								return td('沒有評分', 'nckuhub', options);
							return td(null, 'nckuhub', options,
								div(null, span('收穫', 'label'), nckuHubScoreToSpan(data.nckuhub.got)),
								div(null, span('甜度', 'label'), nckuHubScoreToSpan(data.nckuhub.sweet)),
								div(null, span('涼度', 'label'), nckuHubScoreToSpan(data.nckuhub.cold)),
							);
						}
						return td('載入中...', 'nckuhub', {colSpan: 3});
					})
					: td('沒有資料', 'nckuhub', {colSpan: 3})


				function toggleCourseInfo(forceState) {
					if (typeof forceState === 'boolean' ? forceState : expandArrowStateClass.contains('expand')) {
						expandableElement.style.height = expandableHeightReference.offsetHeight + 'px';
						setTimeout(() => expandableElement.style.height = '0');
						expandArrowStateClass.remove('expand');
					} else {
						expandableElement.style.height = expandableHeightReference.offsetHeight + 'px';
						setTimeout(() => expandableElement.style.height = null, 200);
						expandArrowStateClass.add('expand');
					}
				}

				// Open NCKU Hub detail window
				function openNckuhubDetailWindow() {
					if (!data.nckuhub) return;
					nckuhubDetailWindow.set(data);
				}

				// render result item
				const courseResult = [
					tr('courseBlockSpacing'),
					// Info
					tr('courseInfoBlock',
						td(null, expandArrowStateClass, expandButton, {onclick: toggleCourseInfo}),
						td(null, 'detailedCourseName',
							a(null, createSyllabusUrl(data.semester, data.systemNumber), null, null, {target: '_blank'},
								span((data.serialNumber ? data.serialNumber + ' ' : '') + data.courseName))
						),
						td(data.departmentName, 'departmentName'),
						td(data.serialNumber, 'serialNumber'),
						td(null, 'category', span('類別:', 'label'), data.category && text(data.category)),
						td(null, 'grade', span('年級:', 'label'), data.courseGrade && text(data.courseGrade.toString())),
						td(null, 'class', span('班別:', 'label'), data.classInfo && text(data.classInfo)),
						td(null, 'courseTime', span('時間:', 'label'),
							data.time && data.time.map(i =>
								button(null, i.extraTimeDataKey ? '詳細時間' : courseDataTimeToString(i))
							) || text(data.timeString)
						),
						td(null, 'courseName',
							a(null, createSyllabusUrl(data.semester, data.systemNumber), null, null, {target: '_blank'}, span(data.courseName))
						),
						td(null, 'required', span('選必修:', 'label'), text(data.required ? '必修' : '選修')),
						td(null, 'credits', span('學分:', 'label'), data.credits === null ? null : text(data.credits.toString())),
						td(null, 'available', span('選/餘:', 'label'), createSelectAvailableStr(data)),
						nckuhubInfo,
						// Title sections
						td(null, 'options', {rowSpan: 2},
							!data.serialNumber || !loginState.state || !loginState.state.login ? null :
								button(null, watchList && watchList.indexOf(data.serialNumber) !== -1 ? '移除關注' : '加入關注', watchedCourseAddRemove, {courseData: data}),
							!data.preRegister ? null :
								button(null, '加入預排', sendPreKey, {courseData: data, key: data.preRegister}),
							!data.preferenceEnter ? null :
								button(null, '加入志願', sendCosData, {courseData: data, key: data.preferenceEnter}),
							!data.addCourse ? null :
								button(null, '單科加選', sendCosData, {courseData: data, key: data.addCourse}),
						),
					),
					// Details
					tr('courseDetailBlock', courseDetail)
				];
				courseRenderResult.push([data, courseResult]);
			}
			updateFilter(true);
		}

		// Update display element
		courseSearchResultInfo.textContent = '搜尋到 ' + courseRenderResultDisplay.length + ' 個結果, 點擊欄位即可排序';
		showResultLastIndex = showResultIndexStep - 1;
		for (let i = 0; i < courseRenderResultDisplay.length; i++) {
			const item = courseRenderResultDisplay[i];
			const display = i > showResultLastIndex ? 'none' : null;
			item[0].style.display = item[1].style.display = item[2].style.display = display;
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
		});
	}

	function createSyllabusUrl(yearSem, sysNumClassCode) {
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

	function createSelectAvailableStr(courseData) {
		if (courseData.selected === null && courseData.available === null)
			return null;
		const selected = courseData.selected === null ? '' : courseData.selected;
		const available = courseData.available === null ? ''
			: courseData.available === -1 ? '不限'
				: courseData.available === -2 ? '洽系所'
					: courseData.available.toString();
		// Colored text
		if (courseData.available !== null) {
			if (courseData.available === 0)
				return span(selected + '/' + available, null, {style: 'color:' + textColor.red});
			else if (courseData.available > 40)
				return span(selected + '/', null, span(available, null, {style: 'color:' + textColor.green}));
			else if (courseData.available > 0)
				return span(selected + '/', null, span(available, null, {style: 'color:' + textColor.orange}));
		}
		return span(selected + '/' + available);
	}

	// Search page
	let tHead;
	const courseSearch = div('courseSearch',
		{onRender, onPageClose, onPageOpen},
		h1('課程查詢', 'title'),
		courseSearchForm,
		table('result', {cellPadding: 0},
			colgroup(null,
				// col(null),
				// col(null, {'style': 'visibility: collapse'}),
			),
			State(searchResultSignal, renderSearchResult),
			tHead = thead('noSelect',
				filter.createElement(),
				tr(null, th(null, 'resultCount', {colSpan: 15}, courseSearchResultInfo)),
				tr(null,
					expandAllButton,
					// th('Dept', 'departmentName', {key: 'departmentName', onclick: sortStringKey}),
					// th('Serial', 'serialNumber', {key: 'serialNumber', onclick: sortStringKey}),
					// th('Category', 'category', {key: 'category', onclick: sortStringKey}),
					// th('Grade', 'grade', {key: 'grade', onclick: sortIntKey}),
					// th('Class', 'class', {key: 'classInfo', onclick: sortStringKey}),
					// th('Time', 'courseTime', {key: 'timeString', onclick: sortStringKey}),
					// th('Course name', 'courseName', {key: 'courseName', onclick: sortStringKey}),
					// th('Required', 'required', {key: 'required', onclick: sortIntKey}),
					// th('Credits', 'credits', {key: 'credits', onclick: sortIntKey}),
					// th('Sel/Avail', 'available', {key: 'available', onclick: sortIntKey}),
					// // NckuHub
					// th('Reward', 'nckuhub', {key: 'got', onclick: sortNckuhubKey}),
					// th('Sweet', 'nckuhub', {key: 'sweet', onclick: sortNckuhubKey}),
					// th('Cool', 'nckuhub', {key: 'cold', onclick: sortNckuhubKey}),
					// // Function buttons
					// th('Options', 'options'),
					th('系所', 'departmentName', {key: 'departmentName', onclick: sortStringKey}),
					th('系-序號', 'serialNumber', {key: 'serialNumber', onclick: sortStringKey}),
					th('類別', 'category', {key: 'category', onclick: sortStringKey}),
					th('年級', 'grade', {key: 'courseGrade', onclick: sortIntKey}),
					th('班別', 'class', {key: 'classInfo', onclick: sortStringKey}),
					th('時間', 'courseTime', {key: 'timeString', onclick: sortStringKey}),
					th('課程名稱', 'courseName', {key: 'courseName', onclick: sortStringKey}),
					th('選必修', 'required', {key: 'required', onclick: sortIntKey}),
					th('學分', 'credits', {key: 'credits', onclick: sortIntKey}),
					th('選/餘', 'available', {key: 'available', onclick: sortIntKey}),
					// NckuHub
					th('收穫', 'nckuhub', {key: 'got', onclick: sortNckuhubKey}),
					th('甜度', 'nckuhub', {key: 'sweet', onclick: sortNckuhubKey}),
					th('涼度', 'nckuhub', {key: 'cold', onclick: sortNckuhubKey}),
					// Function buttons
					th('功能', 'options'),
				),
			),
		),
		instructorInfoBubble,
	);

	router.element.addEventListener('scroll', function () {
		if (courseRenderResultDisplay.length > showResultLastIndex &&
			courseRenderResultDisplay[showResultLastIndex][0].offsetTop - tHead.offsetTop < router.element.offsetHeight) {
			const pShowResultLastIndex = showResultLastIndex;
			showResultLastIndex += showResultIndexStep;
			for (let i = pShowResultLastIndex + 1; i < courseRenderResultDisplay.length; i++) {
				const item = courseRenderResultDisplay[i];
				const display = i > showResultLastIndex ? 'none' : null;
				item[0].style.display = item[1].style.display = item[2].style.display = display;
			}
		}
	});

	const instructorDetailWindow = new InstructorDetailWindow(courseSearch);
	const nckuhubDetailWindow = new NckuhubDetailWindow(courseSearch);

	return courseSearch;
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
						th(instructor.pressure, getColor(5 - instructor.pressure)),
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

function InstructorDetailWindow(courseSearch) {
	const popupWindow = new PopupWindow({root: courseSearch});
	this.set = function (/**@param{UrSchoolInstructor}instructor*/instructor) {
		const instructorInfo = instructorInfoElement(instructor.info);
		popupWindow.setWindowContent(div('instructorDetailWindow',
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

function NckuhubDetailWindow(courseSearch) {
	const popupWindow = new PopupWindow({root: courseSearch});

	this.set = function (/**@param{CourseData}courseData*/courseData) {
		const nckuhub = courseData.nckuhub;
		popupWindow.setWindowContent(div('nckuhubDetailWindow',
			div('courseInfoPanel',
				span(courseData.serialNumber),
				span(courseData.courseName),
				span(courseData.timeString),
			),
			div('nckuhubPanel',
				// rates
				span('課程評分 (' + nckuhub.rate_count + ')', 'title'),
				nckuhub.rate_count === 0 ? div('rates') : div('rates',
					div(null, div('rateBox',
						span('收穫'),
						nckuHubScoreToSpan(nckuhub.got),
					)),
					div(null, div('rateBox',
						span('甜度'),
						nckuHubScoreToSpan(nckuhub.sweet),
					)),
					div(null, div('rateBox',
						span('涼度'),
						nckuHubScoreToSpan(nckuhub.cold),
					)),
				),
				// comment
				span('課程心得 (' + nckuhub.comments.length + ')', 'title'),
				div('comments',
					nckuhub.comments.map(comment => div('commentBlock',
						span(comment.semester, 'semester'),
						p(comment.comment, 'comment'),
					)),
				),
			)
		));
		popupWindow.windowOpen();
	};
}

function nckuHubScoreToSpan(score) {
	const color = score > 7 ? textColor.green : score > 3 ? textColor.orange : textColor.red;
	score = score.toFixed(1);
	if (score === '10.0')
		score = '10';
	return span(score, null, {style: 'color:' + color});
}

function sortToEnd(data) {
	return data === null || data === undefined || data.length === 0;
}

/**
 * @typedef FilterOption
 * @property {function(firstRenderAfterSearch: boolean): void} [onFilterStart] Call before filter start
 * @property {function(item: any): boolean} condition Check item to show
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
				row = tr(null, th(null, 'filterOptions', {colSpan: 15}));
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
		oninput: textSearchFilterChange,
		onpropertychange: textSearchFilterChange
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
	let timeData = null;

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
				if (cosTime.dayOfWeek !== usedCosTime[0])
					continue;

				if (sectionStart >= usedCosTime[1] && sectionStart <= usedCosTime[2] ||
					sectionEnd >= usedCosTime[1] && sectionEnd <= usedCosTime[2] ||
					sectionStart <= usedCosTime[1] && sectionEnd >= usedCosTime[2])
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
						for (const info of i.info)
							usedTime.push(timeParse(info.time));
					}
					timeData = usedTime;
					fetchingData = false;
					console.log(usedTime);

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
 * @return {FilterOption}
 */
function classFilter(onFilterUpdate, courseRenderResult) {
	const selectMenu = new SelectMenu('班別過濾', 'classFilter', 'classFilter', null, {multiple: true});
	let selectValue = null;

	function updateSelectValue() {
		selectValue = selectMenu.getSelectedValue();
		onFilterUpdate();
	}

	function onFilterStart(firstRenderAfterSearch) {
		if (!firstRenderAfterSearch)
			return;

		let classCategory = [];
		for (const [i] of courseRenderResult) {
			if (i.classInfo && classCategory.indexOf(i.classInfo) === -1)
				classCategory.push(i.classInfo);
		}
		classCategory = classCategory.map(i => [i, i]);
		classCategory.push(['', '無班別']);

		selectMenu.setItems(classCategory, true);
		selectValue = selectMenu.getSelectedValue();
		selectMenu.onSelectItemChange = updateSelectValue;
	}

	/**@param{CourseData}courseData*/
	function condition([courseData]) {
		if (!selectValue)
			return true;
		if (!courseData.classInfo)
			return selectValue.indexOf('') !== -1;

		return selectValue.indexOf(courseData.classInfo) !== -1;
	}

	return {
		onFilterStart: onFilterStart,
		condition: condition,
		element: selectMenu.element,
	}
}