export const mobileWidth = 700;
const apiEndPoint = window.location.hostname === 'localhost'
	? 'https://localhost/api'
	: 'https://api.simon.chummydns.com/api';

export function isMobile() {
	return window.innerWidth <= mobileWidth
}

/**
 * @typedef {Object} ApiResponse
 * @property {string} success
 * @property {string[]} err
 * @property {string[]} warn
 * @property {string} msg
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

	return fetch(apiEndPoint + endpoint, option)
		.then(i => i.json())
		// Handle self defined error
		.then(i => {
			if (abortTimeout)
				clearTimeout(abortTimeout);
			if (!i.success && !i.msg)
				window.messageAlert.addError(
					'Api response error',
					i.err.join('\n'), 2000);
			if (stateElement)
				requestState.removeState(stateElement);
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
			return null;
		});
}

export function timeParse(timeStr) {
	const time = timeStr.split(',');
	const parsedTime = new Int8Array(3);

	parsedTime[0] = parseInt(time[0]);
	if (time.length > 1)
		parsedTime[1] = parseInt(time[1]);
	if (time.length > 2)
		parsedTime[2] = parseInt(time[2]);
	// Make section end equals to section start
	else if (time.length > 1)
		parsedTime[2] = parsedTime[1];
	return parsedTime;
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
	if (time.extraTimeDataKey) return '';
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
		nckuhub: null
	};

	// Parse time
	if (rawCourseData.t != null) {
		courseData.time = rawCourseData.t.map(i => {
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