'use strict';

/*ExcludeStart*/
const module = {};
const {div, button, Signal, span, State, img, linkStylesheet} = require('../domHelper');
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

    return div('stuIdSysGrades',
        {onRender, onPageOpen, onPageClose},
        State(normalDestImg, i => !i ? div() : img('data:image/svg+xml;base64,' + btoa(i[0]))),

        State(semestersInfo, i => !i ? div() : div('semestersInfo', i.map(j =>
            div(null,
                span('Total Credits: ' + j.totalC),
                span('Earned Credits: ' + j.earnedC),
                span('Require Credits: ' + j.requireC),
                span('Elective Credits: ' + j.electiveC),
                span('Equivalent Credits: ' + j.equivalentC),
                span('Second Major Credits: ' + j.secondMajorC),
                span('Summer Credits: ' + j.summerC),
                span('Weighted Grades: ' + j.weightedGrades),
                span('AverageScore: ' + j.averageScore),
                span('ClassRanking: ' + j.classRanking + '/' + j.classRankingTotal),
                span('DeptRanking: ' + j.deptRanking + '/' + j.deptRankingTotal),
                button(null, j.semID, getSemesterGrade, {semID: j.semID}),
            )
        ))),

        State(semesterGrades, i => !i ? div() : div('semesterGrades', i.map(j =>
            div(null,
                span('Course Name: ' + j.courseName),
                span('Serial Number: ' + j.serialNumber),
                span('Course No: ' + j.courseNo),
                span('Credits: ' + j.credits),
                span('Gpa: ' + j.gpa),
                span('Grade: ' + j.grade),
                span('Remark: ' + j.remark),
                span('Require: ' + j.require),
                button(null, 'Normal Dest Img', getNormalDestImg, {imgQuery: j.imgQuery}),
            )
        ))),
    );
};
