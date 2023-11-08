'use strict';

import {div, h1, img, mountableStylesheet, ShowIf, Signal, span, State, text} from '../minjs_v000/domHelper.min.js';
import {fetchApi} from '../lib/lib.js';
import PopupWindow from '../popupWindow.js';

/**
 * - SemesterGrade: /stuIdSys?mode=semCourse&semId=SemID
 * - CurrentSemesterGrade: /stuIdSys?mode=currentSemInfo
 * @typedef CourseGrade
 * @property {string} serialNumber
 * @property {string} systemNumber
 * @property {string} courseName
 * @property {string} remark
 * @property {float} credits
 * @property {string} require
 * @property {float | -1 | -2} grade
 * - -1: no grade
 * - -2: not yet determined
 * @property {string | null} gpa
 * @property {string | null} imgQuery
 */

/**
 * /stuIdSys?mode=semInfo
 * @typedef SemesterInfo
 * @property {string} semester
 * @property {string} semID
 * @property {float} requireC
 * @property {float} electiveC
 * @property {float} summerC
 * @property {float} secondMajorC
 * @property {float} equivalentC
 * @property {float} earnedC
 * @property {float} totalC
 * @property {float} weightedGrades
 * @property {float} averageScore
 * @property {int} classRanking
 * @property {int} classRankingTotal
 * @property {int} deptRanking
 * @property {int} deptRankingTotal
 */

/**
 * @param {QueryRouter} router
 * @param {Signal} loginState
 * @return {HTMLDivElement}
 */
export default function (router, loginState) {
	console.log('StuIdSys grades Init');
	// static element
	const styles = mountableStylesheet('./res/pages/stuIdSysGrades.css');
	const loadingState = new Signal(false);
	const currentSemestersInfo = new Signal();
	const semestersInfo = new Signal();
	const semesterGrades = new Signal();
	const normalDestImgWindow = new PopupWindow({root: router.element});
	let stuIdLoadingCount = 0;
	let stuIdLoading = false;

	function onRender() {
		console.log('StuIdSys grades Render');
		styles.mount();
	}

	function onPageOpen() {
		console.log('StuIdSys grades Open');
		styles.enable();
		loginState.addListener(onLoginState);
		onLoginState(loginState.state);
	}


	function onPageClose() {
		console.log('StuIdSys grades Close');
		styles.disable();
		loginState.removeListener(onLoginState);
	}

	/**
	 * @param {LoginData} state
	 */
	function onLoginState(state) {
		// Login
		if (state && state.login) {
			loadingState.set(true);
			if (stuIdLoading)
				return;
			stuIdLoading = true;
			fetchApi('/login?mode=stuId', 'Login identification system').then(i => {
				// Student Identification System login success
				if (i.success && i.data && i.data.login) {
					stuIdLoadingCount = 2;
					getSemestersInfo();
					CurrentSemesterGrade();
				}
				// Login failed
				else {
					window.messageAlert.addError('Identification system login failed', 'Refresh to try again', 3000);
					loadingState.set(false);
					stuIdLoading = false;
				}
			});
		}
		// Logout
		else {
			if (state)
				window.askForLoginAlert();
			currentSemestersInfo.set(null);
			semestersInfo.set(null);
			semesterGrades.set(null);
		}
	}

	function getSemestersInfo() {
		fetchApi('/stuIdSys?mode=semInfo').then(i => {
			semestersInfo.set(i.data);
			if (--stuIdLoadingCount === 0) {
				loadingState.set(false);
				stuIdLoading = false;
			}
		});
	}

	function CurrentSemesterGrade() {
		fetchApi('/stuIdSys?mode=currentSemInfo').then(i => {
			currentSemestersInfo.set(i.data);
			if (--stuIdLoadingCount === 0) {
				loadingState.set(false);
				stuIdLoading = false;
			}
		});
	}

	function getSemesterGrade() {
		semesterGrades.set(null);
		fetchApi('/stuIdSys?mode=semCourse&semId=' + this.semID).then(i => {
			semesterGrades.set(i.data);
		});
	}

	/**
	 * @this {{courseInfo: CourseGrade}}
	 */
	function getNormalDestImg(inCourseInfo) {
		const courseInfo = this && this.courseInfo || inCourseInfo;
		if (!courseInfo.imgQuery)
			return;
		fetchApi('/stuIdSys?mode=courseNormalDist&imgQuery=' + courseInfo.imgQuery).then(response => {
			if (response.success) {
				normalDestImgWindow.windowSet(
					div('normalDest',
						h1(courseInfo.courseName),
						createDestImage(response.data.studentCount),
					)
				);
				normalDestImgWindow.windowOpen();
			} else {
				// // Normal distribution graph not exist
				// if (i.msg) {
				//     // Try
				//     if (courseInfo.imgQuery.endsWith(','))
				//         courseInfo.imgQuery += '1';
				//     else {
				//         const index = courseInfo.imgQuery.lastIndexOf(',');
				//         const classCodeTry = parseInt(courseInfo.imgQuery.substring(index + 1)) + 1;
				//         if (classCodeTry > 5)
				//             return;
				//         courseInfo.imgQuery = courseInfo.imgQuery.substring(0, index + 1) + classCodeTry;
				//     }
				//     getNormalDestImg(courseInfo);
				// }
			}
		});
	}

	/**
	 * @param {int[]} counts
	 */
	function createDestImage(counts) {
		const bars = [];
		const sum = counts.reduce((i, j) => i + j, 0);
		const barWidth = 100 / counts.length;
		for (let i = 0; i < counts.length; i++) {
			const count = counts[i];
			const bar = div('bar',
				span(count, 'count')
			);
			bar.style.width = barWidth + '%';
			bar.style.height = 100 * count / sum + '%';
			bar.style.left = barWidth * i + '%';
			bars.push(bar);
		}

		return div('destImage', bars);
	}

	const gpaPoint = {A: 4, B: 4, C: 3, D: 2, E: 1, F: 0, X: 0};

	function gpaPointCalculate(gpa) {
		let point = gpaPoint[gpa.charAt(0)];
		for (let i = 1; i < gpa.length; i++)
			if (gpa.charAt(i) === '+')
				point += 0.3;
			else if (gpa.charAt(i) === '-')
				point -= 0.3;
		return point;
	}

	function parseSemesterStr(semester) {
		return semester.substring(0, semester.length - 1) +
			(semester.charAt(semester.length - 1) === '0' ? '上學期' : '下學期');
	}

	function gradeToText(grade) {
		return grade === -1 ? '無'
			: grade === -2 ? '成績未到'
				: grade === -3 ? '通過'
					: grade === -4 ? '抵免'
						: grade === -5 ? "退選"
							: grade === -6 ? "優良"
								: grade === -7 ? "不通"
									: grade;
	}

	return div('stuIdSysGrades',
		{onRender, onPageOpen, onPageClose},
		State(semestersInfo, /**@param{SemesterInfo[]}i*/i => !i ? div()
			// If Semesters data, render
			: div('semesters', i.map(semInfo =>
				div('semesterInfo', {onclick: getSemesterGrade, semID: semInfo.semID},
					h1(parseSemesterStr(semInfo.semester)),
					// span(null, 'info', span('Total Credits'), text(': ' + semInfo.totalC)),
					// span(null, 'info', span('Earned Credits'), text(': ' + semInfo.earnedC)),
					// span(null, 'info', span('Require Credits'), text(': ' + semInfo.requireC)),
					// span(null, 'info', span('Elective Credits'), text(': ' + semInfo.electiveC)),
					// span(null, 'info', span('Equivalent Credits'), text(': ' + semInfo.equivalentC)),
					// span(null, 'info', span('Second Major Credits'), text(': ' + semInfo.secondMajorC)),
					// span(null, 'info', span('Summer Credits'), text(': ' + semInfo.summerC)),
					// span(null, 'info', span('Weighted Grades'), text(': ' + semInfo.weightedGrades)),
					// span(null, 'info', span('AverageScore'), text(': ' + semInfo.averageScore)),
					// span(null, 'info', span('ClassRanking'), text(': ' + semInfo.classRanking + '/' + semInfo.classRankingTotal)),
					// span(null, 'info', span('DeptRanking'), text(': ' + semInfo.deptRanking + '/' + semInfo.deptRankingTotal)),
					span(null, 'info', span('總學分'), text(': ' + semInfo.totalC)),
					span(null, 'info', span('修得'), text(': ' + semInfo.earnedC)),
					span(null, 'info', span('必修學分'), text(': ' + semInfo.requireC)),
					span(null, 'info', span('選修學分'), text(': ' + semInfo.electiveC)),
					span(null, 'info', span('抵修學分'), text(': ' + semInfo.equivalentC)),
					span(null, 'info', span('輔系、雙主修學分'), text(': ' + semInfo.secondMajorC)),
					span(null, 'info', span('暑修學分'), text(': ' + semInfo.summerC)),
					span(null, 'info', span('加權總分'), text(': ' + semInfo.weightedGrades)),
					span(null, 'info', span('平均分數'), text(': ' + semInfo.averageScore)),
					span(null, 'info', span('班排'), text(': ' + semInfo.classRanking + '/' + semInfo.classRankingTotal)),
					span(null, 'info', span('系排'), text(': ' + semInfo.deptRanking + '/' + semInfo.deptRankingTotal)),
				)
			))
		),

		State(currentSemestersInfo, i => !i ? div()
			: div('semesterInfo', {onclick: () => semesterGrades.set(i.courseGrades)},
				h1('本學期'),
				span(null, 'info', span('學期'), text(': ' + parseSemesterStr(i.semester))),
				span(null, 'info', span('總學分'), text(': ' + i.courseGrades.reduce((a, b) => a + b.credits, 0))),
			)
		),

		State(semesterGrades, /**@param{CourseGrade[]}i*/i => !i ? div()
			// If CourseGrade data, render
			: div('semesterGrades', i.map(course =>
				div(null, {onclick: getNormalDestImg, courseInfo: course},
					h1(course.courseName),
					// span(null, 'info', span('Serial Number'), text(': ' + course.serialNumber)),
					// span(null, 'info', span('System Number'), text(': ' + course.systemNumber)),
					// span(null, 'info', span('Credits'), text(': ' + course.credits)),
					// span(null, 'info', span('Gpa'), text(': ' + course.gpa)),
					// span(null, 'info', span('Grade'), text(': ' + course.grade)),
					// span(null, 'info', span('Remark'), text(': ' + course.remark)),
					// span(null, 'info', span('Require'), text(': ' + course.require)),
					span(null, 'info', span('課程序號', null, {title: 'Serial Number'}), text(': ' + course.serialNumber)),
					span(null, 'info', span('課程碼', null, {title: 'System Number'}), text(': ' + course.systemNumber)),
					span(null, 'info', span('學分'), text(': ' + course.credits)),
					course.gpa === null ? null :
						span(null, 'info', span('Gpa', null, {title: course.gpa}), text(': ' + gpaPointCalculate(course.gpa))),
					span(null, 'info', span('分數'), text(': ' + gradeToText(course.grade))),
					span(null, 'info', span('課程別'), text(': ' + (course.remark ? course.remark : '無'))),
					span(null, 'info', span('課程'), text(': ' + course.require)),
				)
			))
		),

		ShowIf(loadingState, div('loading', window.loadingElement.cloneNode(true))),
	);
};
