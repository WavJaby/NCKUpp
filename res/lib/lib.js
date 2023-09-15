export const mobileWidth = 700;
export const isLocalNetwork = window.location.hostname === 'localhost' || window.location.hostname.startsWith('192.168.');
const customServer = localStorage.getItem('customServer');
const apiEndPoint = isLocalNetwork
	? customServer || window.location.origin + '/api'
	: 'https://api.simon.chummydns.com/api';

// const isSafari = navigator.userAgent &&
// 	navigator.userAgent.indexOf('Safari') !== -1 && navigator.userAgent.indexOf('Chrome') === -1 &&
// 	navigator.userAgent.indexOf('CriOS') === -1 &&
// 	navigator.userAgent.indexOf('FxiOS') === -1;

const isSafari = navigator.userAgent &&
	navigator.userAgent.indexOf('Safari') !== -1 && navigator.userAgent.indexOf('Chrome') === -1;

export function isMobile() {
	return window.innerWidth <= mobileWidth
}

export function checkLocalStorage() {
	let storage;
	try {
		storage = window.localStorage;
		let testData = '__storage_test__';
		storage.setItem(testData, testData);
		storage.removeItem(testData);
		return true;
	} catch (e) {
		return e instanceof window.DOMException &&
			(e.code === 22 || e.code === 1014 || e.name === 'QuotaExceededError' || e.name === 'NS_ERROR_DOM_QUOTA_REACHED') &&
			(storage && storage.length !== 0);
	}
}

/**
 * @typedef {Object} ApiResponse
 * @property {string} success
 * @property {string[]} err
 * @property {string[]} warn
 * @property {string} msg
 * @property {int} code
 * @property {any} data
 */

/**
 * @typedef {Object} CustomApiFetch
 * @property {int} [timeout] Fetch timeout time millisecond
 *
 * @typedef {RequestInit & CustomApiFetch} ApiFetch
 */

/**
 * @param {string} endpoint
 * @param {string | null} [showState]
 * @param {ApiFetch} [option]
 * @return Promise<ApiResponse>
 */
export function fetchApi(endpoint, showState, option) {
	if (option) option.credentials = 'include';
	else option = {credentials: 'include'};
	let abortTimeout;
	if (window.AbortController && option.timeout) {
		const controller = new AbortController();
		option.signal = controller.signal;
		abortTimeout = setTimeout(() => controller.abort(), option.timeout);
	}
	const stateElement = showState ? requestState.addState(showState) : null;

	if (isSafari) {
		if (endpoint.indexOf('?') === -1)
			endpoint += '?cookie=' + encodeURIComponent(document.cookie);
		else
			endpoint += '&cookie=' + encodeURIComponent(document.cookie);

	}

	return fetch(apiEndPoint + endpoint, option)
		.then(i => {
			if (i.status === 429)
				window.messageAlert.addError('傳送請求過於頻繁', '請稍後再試', 2000);
			if (isSafari) {
				let c = i.headers.get('Content-type');
				if (c) {
					c = c.substring(c.indexOf('c=') + 2);
					c.split(',').forEach(i => document.cookie = i.replace(/Domain=[\w.]+/, 'Domain=' + window.location.hostname));
				}
			}
			return i.json();
		})
		// Handle self defined error
		.then(i => {
			if (abortTimeout)
				clearTimeout(abortTimeout);
			if (stateElement)
				requestState.removeState(stateElement);
			if (!i.success && !i.msg)
				window.messageAlert.addError(
					'Api response error',
					i.err.join('\n'), 2000);
			return i;
		})
		.catch(e => {
			// Timeout error
			if (e.name === 'AbortError') {
				window.messageAlert.addError(
					'Api response timeout',
					'Try again later', 3000);
			}
			// Other error
			else {
				window.messageAlert.addError(
					'Network error',
					e instanceof Error ? e.stack || e || 'Unknown error!' : e, 2000);
			}
			if (stateElement)
				requestState.removeState(stateElement);
			return {success: false, data: null};
		});
}

/**
 * @typedef ScheduleCourse
 * @property {string} serialNumber
 * @property {string} courseName
 * @property {boolean} required
 * @property {float} credits
 * @property {CourseDataTime[]} time
 * @property {string} [delete] Delete action key
 * @property {boolean} [pre] Is pre-schedule
 */

/**
 * @param {CourseData} courseData
 * @param {string} removeKey
 * @param {function(message: string, courseName: string)} [onSuccess]
 * @param {function(message: string, courseName: string)} [onFailed]
 */
export function removePreSchedule(courseData, removeKey, onSuccess, onFailed) {
	const courseName = '[' + courseData.serialNumber + '] ' + courseData.courseName;
	fetchApi('/courseSchedule?pre=true', 'Delete pre schedule',
		{method: 'post', body: 'action=delete&info=' + removeKey}
	).then(response => {
		if (response.success)
			onSuccess && onSuccess(response.msg, courseName);
		else
			onFailed && onFailed(response.msg, courseName);
	});
}

/**
 * @param {CourseData} courseData
 * @param {string} preScheduleKey
 * @param {function(message: string, courseName: string)} [onSuccess]
 * @param {function(message: string, courseName: string)} [onFailed]
 */
export function addPreSchedule(courseData, preScheduleKey, onSuccess, onFailed) {
	const courseName = '[' + courseData.serialNumber + '] ' + courseData.courseName;
	fetchApi('/courseFuncBtn?prekey=' + encodeURIComponent(preScheduleKey), 'Add pre-schedule').then(response => {
		if (response.success)
			onSuccess && onSuccess(response.msg, courseName);
		else
			onFailed && onFailed(response.msg, courseName);
	});
}

/**
 * @param {function(preScheduleData: {schedule: ScheduleCourse[]})} [onSuccess]
 * @param {function(message: string)} [onFailed]
 */
export function getPreSchedule(onSuccess, onFailed) {
	fetchApi('/courseSchedule?pre=true', 'Get pre-schedule').then(response => {
		if (response.success)
			onSuccess && onSuccess(response.data);
		else
			onFailed && onFailed(response.msg);
	});
}

// [default, success, info, primary, warning, danger]
const courseDataTagColor = [
	'gray',
	'#5cb85c',
	'#5bc0de',
	'#337ab7',
	'#f0ad4e',
	'#d9534f'
];

const sectionStr = ['0', '1', '2', '3', '4', 'N', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E'];
const weekStr = ['一', '二', '三', '四', '五', '六', '日'];

/**
 * @param {CourseDataTime} time
 * @return {string}
 */
export function courseDataTimeToString(time) {
	if (time.flexTimeDataKey) return '詳細時間';
	if (time.sectionStart !== null) {
		let section;
		if (time.sectionEnd !== null) {
			section = sectionStr[time.sectionStart] + '~' + sectionStr[time.sectionEnd];
		} else
			section = sectionStr[time.sectionStart];
		return weekStr[time.dayOfWeek] + ' ' + section;
	}
	return weekStr[time.dayOfWeek] + ' ';
}

/**
 * @param {RawCourseData} rawCourseData
 * @param {any[][]} rawUrSchoolData
 */
export function parseRawCourseData(rawCourseData, rawUrSchoolData) {
	const courseData = /**@type CourseData*/ {
		semester: rawCourseData.y,
		departmentName: rawCourseData.dn,
		serialNumber: rawCourseData.sn,
		attributeCode: rawCourseData.ca,
		systemNumber: rawCourseData.cs,
		courseGrade: rawCourseData.g,
		classInfo: rawCourseData.co,
		classGroup: rawCourseData.cg,
		category: rawCourseData.ct,
		courseName: rawCourseData.cn,
		courseNote: rawCourseData.ci,
		courseLimit: rawCourseData.cl,
		tags: null,
		credits: rawCourseData.c,
		required: rawCourseData.r,
		instructors: null,
		selected: rawCourseData.s,
		available: rawCourseData.a,
		time: null,
		timeString: null,
		moodle: rawCourseData.m,
		preferenceEnter: rawCourseData.pe,
		addCourse: rawCourseData.ac,
		preRegister: rawCourseData.pr,
		addRequest: rawCourseData.ar,
		nckuHub: null
	};

	// Parse time
	if (rawCourseData.t != null) {
		courseData.time = rawCourseData.t.map(i => {
			if (i.indexOf(',') === -1)
				return {flexTimeDataKey: i};
			i = i.split(',');
			return {
				dayOfWeek: parseInt(i[0]),
				sectionStart: i[1].length === 0 ? null : parseInt(i[1]),
				sectionEnd: i[2].length === 0 ? null : parseInt(i[2]),
				deptID: i[3].length === 0 ? null : i[3],
				classroomID: i[4].length === 0 ? null : i[4],
				classroomName: i[5].length === 0 ? null : i[5]
			};
		});
	}
	courseData.timeString = courseData.time === null ? '未定' : courseData.time.map(courseDataTimeToString).join(', ');

	// Parse instructors
	if (rawCourseData.i !== null) {
		courseData.instructors = !rawUrSchoolData ? rawCourseData.i : rawCourseData.i.map(i => {
			for (const j of rawUrSchoolData)
				if (j && j[2] === i)
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
	}

	// Parse tags
	if (rawCourseData.tg !== null) {
		courseData.tags = rawCourseData.tg.map(i => {
			i = i.split(',');
			return {
				name: i[0],
				color: i[1].charCodeAt(0) === 0x23 ? i[1] : courseDataTagColor[i[1]],
				link: i[2].length === 0 ? null : i[2],
			}
		});
	}

	return courseData;
}