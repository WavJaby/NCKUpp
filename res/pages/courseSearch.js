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
 * @property {string} ct - courseType
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
 * @property {string} courseType
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
} from '../domHelper_v001.min.js';

import SelectMenu from '../selectMenu.js';

// [default, success, info, primary, warning, danger]
const courseDataTagColor = [
	'gray',
	'#5cb85c',
	'#5bc0de',
	'#337ab7',
	'#f0ad4e',
	'#d9534f'
];

/**
 * @param {QueryRouter} router
 * @param loginState
 * @return {HTMLDivElement}
 */
export default function (router, loginState) {
	console.log('Course search Init');
	const styles = mountableStylesheet('./res/pages/courseSearch.css');
	const expandArrowImage = img('./res/assets/down_arrow_icon.svg', 'Expand button');
	expandArrowImage.className = 'noSelect noDrag';

	const searchResultSignal = new Signal({loading: false, courseResult: null, nckuhubResult: null});
	const instructorInfoBubble = InstructorInfoBubble();
	const instructorDetailWindow = InstructorDetailWindow();
	const nckuhubDetailWindow = NckuhubDetailWindow();

	// Element
	let courseSearchForm, courseSearchResultCount;
	// Static data
	let nckuHubCourseID = null;
	let urSchoolData = null;

	// query string
	let lastQueryString;
	let searching;

	function onRender() {
		console.log('Course search Render');
		styles.mount();
		window.fetchApi('/alldept').then(response => {
			if (response == null || !response.success || !response.data)
				return;
			deptNameSelectMenu.setItems(response.data.deptGroup.map(i => [i.name, i.dept]));
			loadLastSearch();
		});
	}

	function onPageOpen(isHistory) {
		console.log('Course search Open');
		// close navLinks when using mobile devices
		window.navMenu.remove('open');
		styles.enable();
		if (isHistory)
			loadLastSearch();
	}

	function onPageClose() {
		console.log('Course search Close');
		styles.disable();
	}

	function loadLastSearch() {
		const rawQuery = window.urlHashData['searchRawQuery'];

		for (const node of courseSearchForm.getElementsByTagName('input')) {
			let found = false;
			if (rawQuery)
				for (const rawQueryElement of rawQuery) {
					if (node.id === rawQueryElement[0]) {
						// From select menu
						if (node.parentElement instanceof HTMLLabelElement &&
							node.parentElement.selectItemByValue)
							node.parentElement.selectItemByValue(rawQueryElement[1]);
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

	/**
	 * @param {string[][]} [rawQuery] [key, value][]
	 * @param {boolean} [saveQuery] Will save query string if not provide or true
	 * @return {void}
	 */
	async function search(rawQuery, saveQuery) {
		if (searching) return;
		searching = true;
		// get all course ID
		if (nckuHubCourseID === null) {
			searchResultSignal.set({loading: true, courseResult: null, nckuhubResult: null});
			nckuHubCourseID = (await window.fetchApi('/nckuhub', 'get nckuhub id')).data;
		}

		// get urSchool data
		if (urSchoolData === null) {
			searchResultSignal.set({loading: true, courseResult: null, nckuhubResult: null});
			urSchoolData = (await window.fetchApi('/urschool', 'get urschool data')).data;
		}

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

		// Save query string and create history
		if ((saveQuery === undefined || saveQuery === true) && lastQueryString !== queryString) {
			window.urlHashData['searchRawQuery'] = queryData;
			window.pushHistory();
		}

		// Update queryString
		lastQueryString = queryString;

		console.log('Search:', queryString);
		searchResultSignal.set({loading: true, courseResult: null, nckuhubResult: null});

		// fetch data
		const result = (await window.fetchApi('/search?' + queryString, 'Searching', {timeout: 10000}));

		if (!result || !result.success || !result.data) {
			searchResultSignal.set({loading: false, courseResult: null, nckuhubResult: null, failed: true});
			searching = false;
			return;
		}

		// Parse result
		/**@type CourseData[]*/
		const courseResult = [];
		const nckuhubResult = {};
		if (!(result.data instanceof Array)) {
			const arr = [];
			for (const i of Object.values(result.data)) {
				for (const j of i)
					arr.push(j);
			}
			result.data = arr;
		}
		for (/**@type CourseDataRaw*/const data of result.data) {
			const courseData = /**@type CourseData*/ {
				semester: data.y,
				departmentName: data.dn,
				serialNumber: data.sn,
				attributeCode: data.ca,
				systemNumber: data.cs,
				courseGrade: data.g,
				classInfo: data.co,
				classGroup: data.cg,
				courseType: data.ct,
				courseName: data.cn,
				courseNote: data.ci,
				courseLimit: data.cl,
				tags: null,
				credits: data.c,
				required: data.r,
				instructors: null,
				selected: data.s,
				available: data.a,
				time: null,
				timeString: null,
				moodle: data.m,
				preferenceEnter: data.pe,
				addCourse: data.ac,
				preRegister: data.pr,
				addRequest: data.ar,
				nckuhub: null
			};
			courseResult.push(courseData);

			// Parse time
			if (data.t != null)
				courseData.time = data.t.map(i => {
					if (i.indexOf(',') === -1)
						return {extraTimeDataKey: i};
					i = i.split(',');
					return {
						dayOfWeek: parseInt(i[0]),
						sectionStart: i[1].length === 0 ? null : i[1],
						sectionEnd: i[2].length === 0 ? null : i[2],
						deptID: i[3].length === 0 ? null : i[3],
						classroomID: i[4].length === 0 ? null : i[4],
						classroomName: i[5].length === 0 ? null : i[5]
					};
				});
			courseData.timeString = courseData.time === null ? '未定' : courseData.time.map(i => {
				if (i.extraTimeDataKey) return '';
				if (i.sectionStart !== null) {
					let section;
					if (i.sectionEnd !== null) {
						section = i.sectionStart + '~' + i.sectionEnd;
					} else
						section = i.sectionStart;
					return '[' + (i.dayOfWeek + 1) + ']' + section;
				}
				return '[' + (i.dayOfWeek + 1) + ']';
			}).join(', ');

			// Parse instructors
			if (data.i !== null)
				courseData.instructors = data.i.map(i => {
					for (const j of urSchoolData) if (j && j[2] === i)
						return {
							id: j[0],
							mode: j[1],
							name: j[2],
							department: j[3],
							jobTitle: j[4],
							recommend: parseFloat(j[5]),
							reward: parseFloat(j[6]),
							articulate: parseFloat(j[7]),
							pressure: parseFloat(j[8]),
							sweet: parseFloat(j[9]),
							averageScore: j[10],
							qualifications: j[11],
							note: j[12],
							nickname: j[13],
							rollCallMethod: j[14]
						};
					return i;
				});

			// Parse tags
			if (data.tg !== null)
				courseData.tags = data.tg.map(i => {
					i = i.split(',');
					return {
						name: i[0],
						color: i[1].charCodeAt(0) === 0x23 ? i[1] : courseDataTagColor[i[1]],
						link: i[2].length === 0 ? null : i[2],
					}
				});


			// Nckuhub
			if (data.sn != null) {
				const deptAndID = data.sn.split('-');
				let nckuHubID = nckuHubCourseID[deptAndID[0]];
				if (nckuHubID) nckuHubID = nckuHubID[deptAndID[1]];
				if (nckuHubID) nckuhubResult[data.sn] = {nckuHubID, courseData, signal: new Signal()};
			}
		}

		// Get nckuhub data
		const chunkSize = 10;
		const nckuHubDataArr = Object.values(nckuhubResult);
		for (let i = 0; i < nckuHubDataArr.length; i += chunkSize) {
			const chunk = [];
			for (let j = i; j < i + chunkSize && j < nckuHubDataArr.length; j++)
				chunk.push(nckuHubDataArr[j].nckuHubID);
			window.fetchApi('/nckuhub?id=' + chunk.join(',')).then(response => {
				for (let j = 0; j < chunk.length; j++) {
					const {/**@type CourseData*/courseData, /**@type Signal*/signal} = nckuHubDataArr[i + j];
					/**@type NckuHubRaw*/
					const nckuhub = response.data[j];
					courseData.nckuhub = /**@type NckuHub*/ {
						noData: nckuhub.rate_count === 0 && nckuhub.comment.length === 0,
						got: parseFloat(nckuhub.got),
						sweet: parseFloat(nckuhub.sweet),
						cold: parseFloat(nckuhub.cold),
						rate_count: nckuhub.rate_count,
						comments: nckuhub.comment,
						parsedRates: nckuhub.rates.reduce((a, v) => {
							// nckuhub why
							if (!v.recommend && v['recommand']) {
								v.recommend = v['recommand'];
								delete v['recommand'];
							}
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

	function openInstructorDetailWindow(info) {
		window.pageLoading.set(true);
		window.fetchApi(`/urschool?id=${info.id}&mode=${info.mode}`).then(response => {
			/**@type UrSchoolInstructor*/
			const instructor = response.data;
			instructor.info = info;
			instructorDetailWindow.set(instructor);
			window.pageLoading.set(false);
		});
	}


	// Watched list
	let watchList = null;

	/**
	 * @this {{courseData: CourseData}}
	 */
	function watchedCourseAddRemove() {
		if (!loginState.state || !loginState.state.login || !watchList) return;

		const courseData = this.courseData;
		let serialIndex, result;
		if ((serialIndex = watchList.indexOf(courseData.serialNumber)) === -1) {
			console.log('add watch');
			result = window.fetchApi('/watchdog', 'add course to watch list', {
				method: 'POST',
				body: `studentID=${loginState.state.studentID}&courseSerial=${courseData.serialNumber}`
			});
			this.textContent = 'remove watch';
			watchList.push(courseData.serialNumber);
		} else {
			console.log('remove watch');
			result = window.fetchApi('/watchdog', 'remove course from watch list', {
				method: 'POST',
				body: `studentID=${loginState.state.studentID}&removeCourseSerial=${courseData.serialNumber}`
			});
			this.textContent = '移除關注';
			watchList.splice(serialIndex, 1);
		}
		result.then(i => {
			console.log(i);
		});
	}

	function getWatchCourse() {
		if (!loginState.state || !loginState.state.login) return;
		window.fetchApi(`/watchdog?studentID=${loginState.state.studentID}`).then(i => {
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
		window.fetchApi(`/courseFuncBtn?cosdata=${encodeURIComponent(this.cosdata)}`, 'Send course data').then(i => {
			if (i.success)
				window.messageAlert.addSuccess('Message', i.msg, 5000);
		});
	}

	/**
	 * @this {{prekey: string}}
	 */
	function sendPreKey() {
		window.fetchApi(`/courseFuncBtn?prekey=${encodeURIComponent(this.prekey)}`, 'Send key data').then(i => {
			if (i.success)
				window.messageAlert.addSuccess('Message', i.msg, 5000);
		});
	}

	// Render result
	const courseRenderResult = [];
	let courseRenderResultFilter = [];
	const courseRenderResultDisplay = [];
	const expandButtons = [];
	let waitingResult = false;

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

			sortResultItem(key, this, (a, b) =>
				sortToEnd(a && (a = a[0]) && (a = a.nckuhub) && a[key]) ? 1 : sortToEnd(b && (b = b[0]) && (b = b.nckuhub) && b[key]) ? -1 : (
					Math.abs(b[key] - a[key]) < 1e-10 ? (
						sortToEnd(a[keys[0]]) ? 1 : sortToEnd(b[keys[0]]) ? -1 : (
							Math.abs(b[keys[0]] - a[keys[0]]) < 1e-10 ? (
								sortToEnd(a[keys[1]]) ? 1 : sortToEnd(b[keys[1]]) ? -1 : (
									Math.abs(b[keys[1]] - a[keys[1]]) < 1e-10 ? 0 : b[keys[1]] - a[keys[1]]
								)
							) : b[keys[0]] - a[keys[0]]
						)
					) : b[key] - a[key]
				)
			);
		}
	}


	// Filter
	const filter = new Filter();
	/**@type {FilterOption[]}*/
	const filterOptions = [
		textSearchFilter(updateFilter),
		hideConflictCourseFilter(updateFilter, loginState),
	];
	filter.setOptions(filterOptions);

	/**
	 * @param {boolean} [firstRender]
	 */
	function updateFilter(firstRender) {
		if (courseRenderResult.length === 0)
			return;

		console.log('Update Filter');
		resetSortArrow();
		courseRenderResultFilter = filter.updateFilter(courseRenderResult);
		courseRenderResultDisplay.length = 0;
		for (/**@type{[CourseData, HTMLElement]}*/const i of courseRenderResultFilter)
			courseRenderResultDisplay.push(i[1]);
		if (!firstRender)
			searchResultSignal.update();
	}

	function onkeyup(e) {
		if (e.key === 'Enter') search();
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
			courseSearchResultCount.textContent = 'Loading...';
			return window.loadingElement.cloneNode(true);
		}

		// No result
		if (!state.courseResult || state.courseResult.length === 0 || state.failed) {
			if (state.failed)
				courseSearchResultCount.textContent = 'Failed to search';
			else if (state.courseResult && state.courseResult.length === 0)
				courseSearchResultCount.textContent = 'No result';

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
								? a(i.name, i.link, null, null, {style: 'background-color:' + i.color, target: '_blank'})
								: div(null, text(i.name), {style: 'background-color:' + i.color})
							)
						),

						// Note, limit
						data.courseNote === null ? null : span(data.courseNote, 'note'),
						data.courseLimit === null ? null : span(data.courseLimit, 'limit red'),

						// Instructor
						span('Instructor: ', 'instructor'),
						data.instructors === null ? null : data.instructors.map(instructor =>
							!(instructor instanceof Object)
								? button('instructorBtn', instructor)
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
							if (data.nckuhub.noData) return td('No data', 'nckuhub', {colSpan: 3});
							const options = {colSpan: 3, onclick: openNckuhubDetailWindow};
							if (data.nckuhub.rate_count === 0)
								return td('No rating', 'nckuhub', options);
							return td(null, 'nckuhub', options,
								span(data.nckuhub.got.toFixed(1), 'reward'),
								span(data.nckuhub.sweet.toFixed(1), 'sweet'),
								span(data.nckuhub.cold.toFixed(1), 'cool'),
							);
						}
						return td('Loading...', 'nckuhub', {colSpan: 3});
					})
					: td('No data', 'nckuhub', {colSpan: 3})


				function toggleCourseInfo(forceState) {
					if (forceState instanceof Boolean ? forceState : expandArrowStateClass.toggle('expand')) {
						expandableElement.style.height = expandableHeightReference.offsetHeight + 'px';
						setTimeout(() => expandableElement.style.height = null, 200);
					} else {
						expandableElement.style.height = expandableHeightReference.offsetHeight + 'px';
						setTimeout(() => expandableElement.style.height = '0');
					}
				}

				// Open NCKU Hub detail window
				function openNckuhubDetailWindow() {
					if (!data.nckuhub) return;
					nckuhubDetailWindow.set(data, true);
				}

				// render result item
				const courseResult = [
					tr(),
					// Info
					tr(null,
						// Title sections
						td(null, expandArrowStateClass, expandButton, {onclick: toggleCourseInfo}),
						td(data.departmentName, 'departmentName'),
						td(data.serialNumber, 'serialNumber'),
						td(data.courseType, 'courseType'),
						td(data.courseGrade, 'grade'),
						td(data.classInfo, 'class'),
						td(data.timeString, 'courseTime'),
						td(null, 'courseName',
							a(data.courseName, createSyllabusUrl(data.semester, data.systemNumber), null, null, {target: '_blank'})
						),
						td(data.required ? '必修' : '選修', 'required'),
						td(data.credits, 'credits'),
						td(data.selected === null && data.available === null ? null : `${data.selected}/${data.available}`, 'available'),
						nckuhubInfo,
						td(null, 'options', {rowSpan: 2},
							!data.serialNumber || !loginState.state || !loginState.state.login ? null :
								button(null, watchList && watchList.indexOf(data.serialNumber) !== -1 ? '移除關注' : '加入關注', watchedCourseAddRemove, {courseData: data}),
							!data.preRegister ? null :
								button(null, '加入預排', sendPreKey, {prekey: data.preRegister}),
							!data.preferenceEnter ? null :
								button(null, '加入志願', sendCosData, {cosdata: data.preferenceEnter}),
							!data.addCourse ? null :
								button(null, '單科加選', sendCosData, {cosdata: data.addCourse}),
						),
					),
					tr('courseDetail',
						// Details
						courseDetail,
					)
				];
				courseRenderResult.push([data, courseResult]);
			}
			updateFilter(true);
		}

		// Update display element
		courseSearchResultCount.textContent = courseRenderResultDisplay.length;
		showResultLastIndex = showResultIndexStep - 1;
		for (let i = 0; i < courseRenderResultDisplay.length; i++) {
			const item = courseRenderResultDisplay[i];
			const display = i > showResultLastIndex ? 'none' : 'table-row';
			item[0].style.display = item[1].style.display = item[2].style.display = display;
		}

		return tbody(null, courseRenderResultDisplay);
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

	// Search page
	const deptNameSelectMenu = new SelectMenu('Dept Name', 'dept', 'dept', null, {searchValue: true});
	let tHead;
	const courseSearch = div('courseSearch',
		{onRender, onPageClose, onPageOpen},
		courseSearchForm = div('form',
			// input(null, 'Serial number', 'serial', {onkeyup}),
			input(null, 'Course name', 'courseName', {onkeyup}),
			deptNameSelectMenu,
			input(null, 'Instructor', 'instructor', {onkeyup}),
			new SelectMenu('Grade', 'grade', 'grade', [['1', '1'], ['2', '2'], ['3', '3'], ['4', '4'], ['5', '5'], ['6', '6'], ['7', '7']], {searchBar: false}),
			new SelectMenu('DayOfWeek', 'dayOfWeek', 'dayOfWeek', [['1', '1'], ['2', '2'], ['3', '3'], ['4', '4'], ['5', '5'], ['6', '6'], ['7', '7']], {searchBar: false}),
			new SelectMenu('Section', 'section', 'section', [
				['1', '0'], ['2', '1'], ['3', '2'], ['4', '3'], ['5', '4'], ['6', 'N'], ['7', '5'], ['8', '6'], ['9', '7'], ['10', '8'], ['11', '9'],
				['12', 'A'], ['13', 'B'], ['14', 'C'], ['15', 'D'], ['16', 'E']
			], {multiple: true}),
			button(null, 'search', search),
			button(null, 'get watched course', getWatchCourse),
		),
		table('result', {cellPadding: 0},
			colgroup(null,
				// col(null),
				// col(null, {'style': 'visibility: collapse'}),
			),
			State(searchResultSignal, renderSearchResult),
			tHead = thead('noSelect',
				filter.createElement(),
				tr(null, th(null, 'resultCount', {colSpan: 15},
					span('Result count: '),
					courseSearchResultCount = span()
				)),
				tr(null,
					th(null, null,
						div('expandDownArrow', expandArrowImage.cloneNode()),
					),
					th('Dept', 'departmentName', {key: 'departmentName', onclick: sortStringKey}),
					th('Serial', 'serialNumber', {key: 'serialNumber', onclick: sortStringKey}),
					th('Type', 'courseType', {key: 'courseType', onclick: sortStringKey}),
					th('Grade', 'grade', {key: 'grade', onclick: sortIntKey}),
					th('Class', 'class', {key: 'classInfo', onclick: sortStringKey}),
					th('Time', 'courseTime', {key: 'timeString', onclick: sortStringKey}),
					th('Course name', 'courseName', {key: 'courseName', onclick: sortStringKey}),
					th('Required', 'required', {key: 'required', onclick: sortIntKey}),
					th('Credits', 'credits', {key: 'credits', onclick: sortIntKey}),
					th('Sel/Avail', 'available', {key: 'available', onclick: sortIntKey}),
					// NckuHub
					th('Reward', 'nckuhub', {key: 'got', onclick: sortNckuhubKey}),
					th('Sweet', 'nckuhub', {key: 'sweet', onclick: sortNckuhubKey}),
					th('Cool', 'nckuhub', {key: 'cold', onclick: sortNckuhubKey}),
					// Function buttons
					th('Options', 'options'),
				),
			),
		),
		instructorInfoBubble,
		instructorDetailWindow,
		nckuhubDetailWindow,
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

function InstructorDetailWindow() {
	return PopupWindow(/**@param{UrSchoolInstructor}instructor*/instructor => {
		const instructorInfo = instructorInfoElement(instructor.info);
		return div('instructorDetailWindow',
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
		);
	});
}

function getColor(number) {
	return number < 2 ? 'red' : number < 4 ? 'yellow' : 'blue';
}

function NckuhubDetailWindow() {
	return PopupWindow(/**@param{CourseData}courseData*/courseData => {
		const nckuhub = courseData.nckuhub;
		return div('nckuhubDetailWindow',
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
						span('Reward'),
						span(nckuhub.got.toFixed(1)),
					)),
					div(null, div('rateBox',
						span('Sweetness'),
						span(nckuhub.sweet.toFixed(1)),
					)),
					div(null, div('rateBox',
						span('Cool'),
						span(nckuhub.cold.toFixed(1)),
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
		);
	});
}

function PopupWindow(onDataChange) {
	const popupSignal = new Signal();
	const popupClass = new ClassList('popupWindow');
	const closeButton = button('closeButton', null, () => popupClass.remove('open'), div('icon'));
	const popupWindow = div(popupClass, State(popupSignal, state => {
		if (!state) return div();
		const body = onDataChange(state);
		body.insertBefore(closeButton, body.firstChild);
		popupClass.add('open');
		return body;
	}));
	popupWindow.set = popupSignal.set;
	return popupWindow;
}

function sortToEnd(data) {
	return data === null || data === undefined || data.length === 0;
}

/**
 * @typedef FilterOption
 * @property {function(item: any): boolean} condition Check item to show
 * @property {HTMLElement|HTMLElement[]} element
 * @property {boolean} [fullLine]
 */

/**
 * Filter tool bar
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
	const searchInput = input(null, 'Teacher, Course name, Serial number', null, {
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

	function condition(data) {
		const /**@type{CourseData}*/ courseData = data[0];
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

	function condition(data) {
		const /**@type{CourseData}*/ courseData = data[0];
		if (!checkBox.checked)
			return true;
		if (!courseData.time)
			return true;

		for (const cosTime of courseData.time) {
			if (!cosTime.sectionStart)
				continue;
			const sectionStart = window.timeParseSection(cosTime.sectionStart);
			const sectionEnd = cosTime.sectionEnd ? window.timeParseSection(cosTime.sectionEnd) : sectionStart;

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
				window.fetchApi('/courseSchedule', 'Get schedule').then(response => {

					if (!response || !response.success || !response.data) {
						checkBox.checked = false;
						timeData = null;
						return;
					}

					// Parse time data
					const usedTime = [];
					for (const i of response.data.schedule) {
						for (const info of i.info) {
							const time = window.timeParse(info.time);
							usedTime.push(time);
						}
					}
					timeData = usedTime;
					fetchingData = false;

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