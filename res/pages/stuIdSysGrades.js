'use strict';

/*ExcludeStart*/
const module = {};
const {div, button, Signal, span, State, img, linkStylesheet, h1, text} = require('../domHelper');
/*ExcludeEnd*/

module.exports = function (loginState) {
    console.log('StuIdSys Grades Init');
    // static element
    const styles = linkStylesheet('./res/pages/stuIdSysGrades.css');
    const semestersInfo = new Signal();
    const semesterGrades = new Signal();
    const normalDestImg = new Signal();

    onLoginState(loginState.state);

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
        if (state && state.login)
            fetchApi('/login?m=i').then(i => {
                if (i.success && i.data && i.data.login) getSemestersInfo();
            });
    }

    function getSemestersInfo() {
        fetchApi('/stuIdSys?m=s').then(i => semestersInfo.set(i.data));
    }

    function getSemesterGrade() {
        fetchApi('/stuIdSys?m=g&s=' + this.semID).then(i => semesterGrades.set(i.data));
    }

    function getNormalDestImg() {
        fetchApi('/stuIdSys?m=i&q=' + this.imgQuery).then(i => normalDestImg.set(i.data));
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

    return div('stuIdSysGrades',
        {onRender, onPageOpen, onPageClose},
        State(normalDestImg, i => !i ? div() : img('data:image/svg+xml;base64,' + btoa(i[0]))),

        State(semestersInfo, i => !i ? div() : div('semesters', i.map(semInfo =>
            div('semesterInfo', {onclick: getSemesterGrade, semID: semInfo.semID},
                h1(semInfo.semID),
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
        ))),

        State(semesterGrades, i => !i ? div() : div('semesterGrades', i.map(j =>
            div(null, {onclick: getNormalDestImg, imgQuery: j.imgQuery},
                h1(j.courseName),
                // span(null, 'info', span('Serial Number'), text(': ' + j.serialNumber)),
                // span(null, 'info', span('System Number'), text(': ' + j.courseNo)),
                // span(null, 'info', span('Credits'), text(': ' + j.credits)),
                // span(null, 'info', span('Gpa'), text(': ' + j.gpa)),
                // span(null, 'info', span('Grade'), text(': ' + j.grade)),
                // span(null, 'info', span('Remark'), text(': ' + j.remark)),
                // span(null, 'info', span('Require'), text(': ' + j.require)),
                span(null, 'info', span('課程序號', null, {title: 'Serial Number'}), text(': ' + j.serialNumber)),
                span(null, 'info', span('課程碼', null, {title: 'System Number'}), text(': ' + j.courseNo)),
                span(null, 'info', span('學分'), text(': ' + j.credits)),
                span(null, 'info', span('Gpa', null, {title: j.gpa}), text(': ' + gpaPointCalculate(j.gpa))),
                span(null, 'info', span('分數'), text(': ' + (j.grade > -1 ? j.grade : '無'))),
                span(null, 'info', span('課程別'), text(': ' + (j.remark ? j.remark : '無'))),
                span(null, 'info', span('課程'), text(': ' + j.require)),
            )
        ))),
    );
};
