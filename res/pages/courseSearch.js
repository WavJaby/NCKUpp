'use strict';

/**@typedef {string} departmentName */
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
 * @typedef {{
 *     dn: departmentName,
 *     sn: serialNumber,
 *     ca: attributeCode,
 *     cs: systemNumber,
 *     cn: courseName,
 *     ci: courseNote,
 *     cl: courseLimit,
 *     ct: courseType,
 *     g: courseGrade,
 *     co: classInfo,
 *     cg: classGroup,
 *     ts: teachers,
 *     tg: tags,
 *     c: credits,
 *     r: required,
 *     s: selected,
 *     a: available,
 *     t: time,
 *     m: moodle
 * }} CourseData
 */

/*ExcludeStart*/
const {div, input, button, table, thead, tbody, span, text} = require('../domHelper');
/*ExcludeEnd*/
const styles = require('./courseSearch.css');

module.exports = function () {
    console.log('courseSearch Init');
    const searchResult = div('result');
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
     * @param result {{data:CourseData[]}}
     */
    function onSearchResult(result) {
        searchResult.innerHTML = '';
        console.log(result);

        let deptLen = 0;
        for (const data of result.data) {
            data.dn = data.dn.split(' ')[0];
            if (data.dn.length > deptLen)
                deptLen = data.dn.length;
        }
        deptLen = deptLen > 5 ? ' long' : '';


        for (const data of result.data) {
            const info = div('courseInfo',
                span(data.dn, 'departmentName' + deptLen),
                span(data.sn, 'serialNumber'),
                span(data.cn, 'courseName'),
            )
            searchResult.appendChild(info);
        }
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
        searchResult
    );
};