'use strict';

import {button, div, h1, h2, p, mountableStylesheet, ShowIf, Signal, span, text, img} from '../minjs_v000/domHelper.min.js';
import {fetchApi} from '../lib/lib.js';
import PopupWindow from '../popupWindow.js';
import SelectMenu from '../selectMenu.js';

/**
 * @typedef CourseGrade
 * @property {string} serialNumber
 * @property {string} systemNumber
 * @property {string | null} classCode
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
 * - /stuIdSys?mode=semInfo
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
 * - /stuIdSys?mode=currentSemInfo
 * @typedef CurrentSemesterInfo
 * @property {string} semester
 * @property {CourseGrade[]} courseGrades
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
	const myGrades = new MyGrades(router);
	const allDistribution = new AllDistribution();
	const myContribute = new MyContribute(router, loginState);

	function onRender() {
		console.log('StuIdSys grades Render');
		styles.mount();
	}

	function onPageOpen() {
		console.log('StuIdSys grades Open');
		styles.enable();
		loginState.addListener(onLoginState);
		allDistribution.updateAllDistribution();
		// onLoginState(loginState.state);
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
			myGrades.loading();
			fetchApi('/login?mode=stuId', 'Login identification system').then(i => {
				// Student Identification System login success
				if (i.success && i.data && i.data.login) {
					myGrades.updateMyGrades();
					myContribute.updateContributeState();
				}
				// Login failed
				else {
					window.messageAlert.addError('Identification system login failed', 'Refresh to try again', 3000);
				}
			});
		}
		// Logout
		else {
			if (state)
				window.askForLoginAlert();
			myGrades.clear();
			myContribute.clear();
		}
	}

	return div('stuIdSysGrades',
		{onRender, onPageOpen, onPageClose},
		myContribute.element,
		myGrades.elements,
		allDistribution.element,
	);
};

function MyGrades(router) {
	const loadingState = new Signal(false);
	const normalDistImgWindow = new PopupWindow({root: router.element});
	let stuIdLoadingCount = 0;
	let stuIdLoading = false;

	let /**@type{SemesterInfo[]|null}*/semestersInfoData = null,
		/**@type{CurrentSemesterInfo|null}*/currentSemInfoData = null;
	let lastSemID = null;

	const semesterInfoElements = div('semesterInfos');
	const semesterGradesElement = div('semesterGrades');
	this.elements = div('myGrades',
		h1('成績分佈'),
		semesterInfoElements,
		semesterGradesElement,
		ShowIf(loadingState, div('loading', window.loadingElement.cloneNode(true))),
	);

	this.loading = function () {
		loadingState.set(true);
	}

	this.updateMyGrades = function () {
		if (stuIdLoading)
			return;
		stuIdLoading = true;
		stuIdLoadingCount = 2;
		getSemestersInfo();
		getCurrentSemesterInfo();
	}

	this.clear = function () {
		while (semesterInfoElements.firstChild)
			semesterInfoElements.removeChild(semesterInfoElements.firstChild);
		while (semesterGradesElement.firstChild)
			semesterGradesElement.removeChild(semesterGradesElement.firstChild);
		semestersInfoData = null;
		currentSemInfoData = null;
		stuIdLoading = false;
	}

	function renderMyGrades() {
		while (semesterInfoElements.firstChild)
			semesterInfoElements.removeChild(semesterInfoElements.firstChild);
		// Semesters
		for (const semInfo of semestersInfoData) {
			semesterInfoElements.appendChild(div('tableCell', div('semesterInfo', {onclick: getSemesterGrades, semID: semInfo.semID},
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
			)));
		}
		// Current
		semesterInfoElements.appendChild(div('tableCell', div('semesterInfo', {onclick: () => openSemesterGrades(currentSemInfoData.courseGrades)},
			h1('本學期'),
			span(null, 'info', span('學期'), text(': ' + parseSemesterStr(currentSemInfoData.semester))),
			span(null, 'info', span('總學分'), text(': ' + currentSemInfoData.courseGrades.reduce((a, b) => a + b.credits, 0))),
		)));
		loadingState.set(false);
	}

	function openSemesterGrades(courseGrades) {
		while (semesterGradesElement.firstChild)
			semesterGradesElement.removeChild(semesterGradesElement.firstChild);
		for (const course of courseGrades) {
			semesterGradesElement.appendChild(div(null, {courseInfo: course, onclick: createDistWindow},
					span(course.serialNumber, 'serialNumber', {title: 'Serial Number'}),
					h1(course.courseName),
					// span(null, 'info', span('System Number'), text(': ' + course.systemNumber)),
					// span(null, 'info', span('Credits'), text(': ' + course.credits)),
					// span(null, 'info', span('Gpa'), text(': ' + course.gpa)),
					// span(null, 'info', span('Grade'), text(': ' + course.grade)),
					// span(null, 'info', span('Remark'), text(': ' + course.remark)),
					// span(null, 'info', span('Require'), text(': ' + course.require)),
					span(course.systemNumber + (course.classCode ? '-' + course.classCode : ''), 'systemNumber'),
					course.gpa === null ? null :
						span(null, 'info', span('Gpa', null, {title: course.gpa}), text(': ' + gpaPointCalculate(course.gpa))),
					span(null, 'info', span('分數'), text(': ' + gradeToText(course.grade))),
					createDistImage(course),
					span(null, 'info', span('學分'), text(': ' + course.credits)),
					span(null, 'info', span('課程別'), text(': ' + (course.remark ? course.remark : '無') + ', ' + course.require)),
				),
			);
		}
	}

	function createDistImage(courseInfo) {
		const element = div('normalDist');
		getNormalDistImg(courseInfo, (courseDistData) => {
			element.appendChild(createDistImageFromData(courseDistData.studentCount, true));
		});
		return element;
	}

	function createDistWindow() {
		getNormalDistImg(this.courseInfo, (courseDistData) => {
			// Open window
			normalDistImgWindow.windowSet(
				div('normalDist',
					h1(this.courseInfo.courseName),
					createDistImageFromData(courseDistData.studentCount),
				),
			);
			normalDistImgWindow.windowOpen();
		});
	}

	/**
	 * @param {int[] | null} counts
	 * @param {boolean} [noLabel]
	 */
	function createDistImageFromData(counts, noLabel) {
		if (counts == null) {
			return div('distImage',
				span('無成績分布圖', 'nodata'),
			);
		}

		const bars = [];
		const labels = [];
		let max;
		if (noLabel) {
			// Get max
			max = counts.reduce((i, j) => j > i ? j : i, 0);
		} else {
			// Get sum
			max = counts.reduce((i, j) => i + j, 0);
		}

		const barWidth = 100 / counts.length;
		for (let i = 0; i < counts.length; i++) {
			const count = counts[i];
			const bar = div(null,
				noLabel && count === 0 ? null : span(count, 'count'),
			);
			bar.style.width = barWidth + '%';
			bar.style.height = 90 * count / max + '%';
			bar.style.left = barWidth * i + '%';
			bars.push(bar);
			if (noLabel)
				continue;

			const line = div();
			line.style.left = barWidth * i + '%';
			labels.push(line);

			const label = span(i * 10, i === 0 ? null : 'center');
			label.style.left = barWidth * i + '%';
			labels.push(label);
		}

		return div('distImage' + (noLabel ? ' noLabel' : ''),
			div('bars', bars),
			noLabel ? null : div('labels', labels),
		);
	}

	function getSemesterGrades() {
		if (lastSemID === this.semID)
			return;
		lastSemID = this.semID;
		fetchApi('/stuIdSys?mode=semCourse&semId=' + this.semID).then(i => {
			openSemesterGrades(i.data);
		});
	}

	function getSemestersInfo() {
		fetchApi('/stuIdSys?mode=semInfo').then(i => {
			if (i.success)
				semestersInfoData = i.data;
			if (--stuIdLoadingCount === 0) {
				stuIdLoading = false;
				renderMyGrades();
			}
		});
	}

	function getCurrentSemesterInfo() {
		fetchApi('/stuIdSys?mode=currentSemInfo').then(i => {
			if (i.success)
				currentSemInfoData = i.data;
			if (--stuIdLoadingCount === 0) {
				stuIdLoading = false;
				renderMyGrades();
			}
		});
	}

	/**
	 * @param courseInfo {CourseGrade}
	 * @param callback {function(any)}
	 */
	function getNormalDistImg(courseInfo, callback) {
		if (!courseInfo.imgQuery)
			return;
		fetchApi('/stuIdSys?mode=courseGradesDistribution&imgQuery=' + courseInfo.imgQuery).then(response => {
			if (response.success)
				callback(response.data);
			else {
				callback({studentCount: null});
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
				//     getNormalDistImg(courseInfo);
				// }
			}
		});
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

	function gradeToText(grade) {
		switch (grade) {
			case -1:
				return '無'
			case -2:
				return '成績未到'
			case -3:
				return '通過'
			case -4:
				return '抵免'
			case -5:
				return "退選"
			case -6:
				return "優良"
			case -7:
				return "不通"
			default:
				return grade.toString();
		}
	}
}

function parseSemesterStr(semester) {
	return semester.substring(0, semester.length - 1) +
		(semester.charAt(semester.length - 1) === '0' ? '上學期' : '下學期');
}

function MyContribute(router, loginState) {
	const contributeSelectWindow = new PopupWindow({
		root: router.element, windowType: PopupWindow.WIN_TYPE_DIALOG, conformButton: '確定', cancelButton: '取消', onclose: onClose,
	});
	// MyContribute state
	const total = span('--');
	const contributeBtn = button(null, '貢獻', contributeSelect, {disabled: true});
	let selectMenu = null;

	this.element = div('myContribute',
		h1('我的貢獻 (Beta)'),
		span(null, 'total', span('總貢獻: '), total, span('筆')),
		contributeBtn,
	);

	this.updateContributeState = function () {
		contributeBtn.disabled = false;
		fetchApi('/stuIdSys?mode=myContribute').then(i => {
			if (i.success) updateContributeState(i.data);
		});
	}

	this.clear = function () {
		total.textContent = '--';
		contributeBtn.disabled = true;
	}

	/** @param {SemesterInfo[]} semesterInfo*/
	function updateSelectMenuItem(semesterInfo) {
		selectMenu.setItems(semesterInfo.map(i => [i.semID, parseSemesterStr(i.semester)]), true);
	}

	function contributeSelect() {
		selectMenu = new SelectMenu('選擇學期', 'contributeSemesterSelect', 'contributeSemesterSelect', null, {multiple: true});
		fetchApi('/stuIdSys?mode=semInfo').then(i => {
			if (i.success)
				updateSelectMenuItem(i.data)
		});
		contributeSelectWindow.windowSet(div('contributeSemester',
			h2('請選擇學期'),
			p(null, null,
				img('./res/assets/info_icon.svg', 'info'), span('本網站僅會蒐集選擇學期內的課程，\n並只會儲存該課程的成績分布圖'),
			),
			selectMenu.element,
		));
		contributeSelectWindow.windowOpen();
	}

	function onClose(conform) {
		if (!conform || !loginState.state || !loginState.state.login)
			return;
		fetchApi('/stuIdSys?mode=addContribute&semesterIds=' +
			selectMenu.getSelectedValue().join(',') +
			'&studentId=' + loginState.state.studentID,
		).then(i => {

		});
	}

	function updateContributeState(data) {
		total.textContent = data.length;
	}
}

function AllDistribution() {
	const total = span('--');

	this.element = div('allDistribution',
		h1('所有成績分佈 (Beta)'),
		span(null, 'total', span('共收錄: '), total, span('筆')),
	);

	this.updateAllDistribution = function () {
		fetchApi('/stuIdSys?mode=allDistribution').then(i => {
			if (i.success) updateAllDistribution(i.data);
		});
	};

	function updateAllDistribution() {

	}
}