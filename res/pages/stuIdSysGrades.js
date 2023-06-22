'use strict';

/*ExcludeStart*/
const module = {};
const {div, button, Signal, span, State, img, linkStylesheet, h1, text, ShowIf} = require('../domHelper');
/*ExcludeEnd*/

/**
 * - SemesterGrade: /stuIdSys?m=g&s=[SemID]
 * - CurrentSemesterGrade: /stuIdSys?m=c
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
 * /stuIdSys?m=s
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

module.exports = function (loginState) {
    console.log('StuIdSys grades Init');
    // static element
    const styles = linkStylesheet('./res/pages/stuIdSysGrades.css');
    const loadingState = new Signal(false);
    const currentSemestersInfo = new Signal();
    const semestersInfo = new Signal();
    const semesterGrades = new Signal();
    const normalDestImg = new Signal();
    let semesterLoadingStateCount = 0;

    async function onRender() {
        console.log('StuIdSys grades Render');
        styles.mount();
    }

    function onPageOpen() {
        console.log('StuIdSys grades Open');
        // close navLinks when using mobile devices
        window.navMenu.remove('open');
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
            window.fetchApi('/login?m=i').then(i => {
                // Student Identification System login success
                if (i.success && i.data && i.data.login) {
                    semesterLoadingStateCount = 2;
                    getSemestersInfo();
                    CurrentSemesterGrade();
                }
                // Login failed
                else {
                    window.messageAlert.addError("Identification system login failed", 'Refresh to try again', 3000);
                    loadingState.set(false);
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
            normalDestImg.set(null);
        }
    }

    function getSemestersInfo() {
        window.fetchApi('/stuIdSys?m=s').then(i => {
            semestersInfo.set(i.data);
            if (--semesterLoadingStateCount === 0)
                loadingState.set(false);
        });
    }

    function CurrentSemesterGrade() {
        window.fetchApi('/stuIdSys?m=c').then(i => {
            currentSemestersInfo.set(i.data);
            if (--semesterLoadingStateCount === 0)
                loadingState.set(false);
        });
    }

    function getSemesterGrade() {
        semesterGrades.set(null);
        window.fetchApi('/stuIdSys?m=g&s=' + this.semID).then(i => {
            semesterGrades.set(i.data);
        });
    }

    /**
     * @this {{courseInfo: CourseGrade}}
     */
    function getNormalDestImg() {
        const courseInfo = this.courseInfo;
        if (!courseInfo.imgQuery)
            return;
        window.fetchApi('/stuIdSys?m=i&q=' + courseInfo.imgQuery).then(i =>
            normalDestImg.set({graph: i.data[0], courseInfo: courseInfo})
        );
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
        semester = semester.split(' ');
        return semester[0] + (semester[1] === '0' ? '上學期' : '下學期');
    }

    return div('stuIdSysGrades',
        {onRender, onPageOpen, onPageClose},
        State(normalDestImg, state => !state ? div() : div('graphBackground',
            div('graphVerticalCenter', div('graph',
                h1(state.courseInfo.courseName),
                button('closeButton', null, () => normalDestImg.set(null),
                    div('icon')
                ),
                img('data:image/svg+xml;base64,' + btoa(state.graph))
            ))
        )),

        State(currentSemestersInfo, i => !i ? div()
            : div('semesterInfo', {onclick: () => semesterGrades.set(i.courseGrades)},
                h1('本學期'),
                span(null, 'info', span('學期'), text(': ' + parseSemesterStr(i.semester))),
                span(null, 'info', span('總學分'), text(': ' + i.courseGrades.reduce((a, b) => a + b.credits, 0))),
            )
        ),

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
                    span(null, 'info', span('分數'), text(': ' + (course.grade === -2 ? '成績未到' : course.grade === -1 ? '無' : course.grade))),
                    span(null, 'info', span('課程別'), text(': ' + (course.remark ? course.remark : '無'))),
                    span(null, 'info', span('課程'), text(': ' + course.require)),
                )
            ))
        ),

        ShowIf(loadingState, div('loading', window.loadingElement.cloneNode(true))),
    );
};
