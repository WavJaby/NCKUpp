'use strict';

/*ExcludeStart*/
const {div, input, button, table, thead, tbody} = require('../domHelper');
/*ExcludeEnd*/
const styles = require('./courseSearch.css');

module.exports = function () {
    console.log('courseSearch Init');
    const tableHead = thead();
    const tableBody = tbody();
    let courseSearchForm;
    let lastQueryString;

    function onRender() {
        document.head.appendChild(styles);
    }

    function onDestroy() {
        document.head.removeChild(styles);
    }

    function onkeyup(e) {
        if (e.key === 'Enter') search();
    }

    function search() {
        const queryData = [];
        for (const /**@type HTMLElement*/ node of courseSearchForm.childNodes) {
            if (!(node instanceof HTMLInputElement)) continue;
            const value = node.value.trim();
            if (value.length > 0)
                queryData.push(node.name + '=' + encodeURIComponent(value));
        }
        const queryString = queryData.join('&');
        if (queryString === lastQueryString) return;
        lastQueryString = queryString;
        console.log(queryString);
        fetchApi('/search?' + queryString).then(onSearchResult);
    }

    /**
     * DeptCode - Course serial number
     * @example F7-010
     * @typedef {string} serialNumber
     */
    /**
     * Course attribute code
     * @example CSIE1001
     * @typedef {string} attributeCode
     */
    /**
     * Course system number - Class code
     * @typedef {string} systemNumber
     */
    /**@typedef {string} courseName */
    /**@typedef {string} courseNote */
    /**@typedef {string} courseLimit */
    /**@typedef {string} courseType */
    /**@typedef {int} courseGrade */
    /**@typedef {string} classInfo */
    /**@typedef {string} classGroup */
    /**@typedef {string} teachers */
    /**@typedef {string[]} tags */
    /**@typedef {float} credits */
    /**@typedef {boolean} required */
    /**@typedef {int} selected */
    /**@typedef {int|string} available */
    /**@typedef {string[]} time */
    /**@typedef {string} moodle */

    /**
     * @param result {{data:{
     *     sn: serialNumber,
     *     ca: attributeCode,
     *     cs: systemNumber,
     *     cn: courseName,
     *     ci: courseNote,
     *     cl: courseLimit,
     *     ct: courseType,
     *     g: courseGrade,
     *     ci: classInfo,
     *     cg: classGroup,
     *     ts: teachers,
     *     tg: tags,
     *     c: credits,
     *     r: required,
     *     s: selected,
     *     a: available,
     *     t: time,
     *     m: moodle
     * }[]}}
     */
    function onSearchResult(result) {
        console.log(result.data[0].tg);

    }

    return courseSearchForm = div('courseSearch',
        {onRender, onDestroy},
        input(null, 'Serial number', 'serialNumber', {onkeyup, name: 'serial'}),
        input(null, 'Course name', 'courseName', {onkeyup, name: 'course'}),
        input(null, 'Dept ID', 'deptId', {onkeyup, name: 'dept'}),
        input(null, 'Instructor', 'instructor', {onkeyup, name: 'teacher'}),
        input(null, 'Day', 'day', {onkeyup, name: 'day'}),
        input(null, 'Grade', 'grade', {onkeyup, name: 'grade'}),
        input(null, 'Section', 'section', {onkeyup, name: 'section'}),
        button(null, 'search', search),
        table(null,
            tableHead,
            tableBody,
        ),
        div('result',
            div(''),
        )
    );
};