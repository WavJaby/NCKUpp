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
const {div, input, button, table, thead, tbody, span, text, svg} = require('../domHelper');
/*ExcludeEnd*/
const styles = require('./courseSearch.css');

module.exports = function () {
    console.log('courseSearch Init');
    const expendArrow = svg('./res/assets/expand_down_arrow_icon.svg', 'expendDownArrow');
    const searchResult = div('result');
    let nckuHubCourseID;
    let courseSearchForm;
    let lastQueryString;
    let searching;

    function onRender() {
        document.head.appendChild(styles);
        search();
    }

    function onDestroy() {
        document.head.removeChild(styles);
    }

    function onkeyup(e) {
        if (e.key === 'Enter') search();
    }

    function search() {
        if (searching) return;
        searching = true;
        if (!nckuHubCourseID) {
            fetchApi('/nckuhub').then(i => {
                nckuHubCourseID = i;
                searching = false;
                search();
            });
            return;
        }

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

        const font = getComputedStyle(searchResult).font;
        const canvas = new Canvas(font);

        let deptLen = 0;
        let timeLen = 0;
        for (const data of result.data) {
            data.dn = data.dn.split(' ')[0];
            let cache;
            if ((cache = canvas.measureText(data.dn).width + 1) > deptLen)
                deptLen = cache;
            data.t = data.t.map(i => i.split(','));

            // parse
            data.parseedTime = data.t.map(i => '[' + i[0] + ']' + i[1]).join(', ');
            if ((cache = canvas.measureText(data.parseedTime).width + 1) > timeLen)
                timeLen = cache;
        }

        for (const data of result.data) {
            const info = div('courseInfo',
                expendArrow.cloneNode(true),
                span(data.dn, 'departmentName', {style: `width:${deptLen}px`}),
                span(data.sn, 'serialNumber'),
                span(data.parseedTime, 'courseTime', {style: `width:${timeLen}px`}),
                span(data.cn, 'courseName'),
            )
            searchResult.appendChild(info);
        }
        searching = false;
    }

    return courseSearchForm = div('courseSearch',
        {onRender, onDestroy},
        input(null, 'Serial number', 'serialNumber', {onkeyup, name: 'serial'}),
        input(null, 'Course name', 'courseName', {onkeyup, name: 'course'}),
        input(null, 'Dept ID', 'deptId', {onkeyup, name: 'dept', value: 'F7'}),
        input(null, 'Instructor', 'instructor', {onkeyup, name: 'teacher'}),
        input(null, 'Day', 'day', {onkeyup, name: 'day'}),
        input(null, 'Grade', 'grade', {onkeyup, name: 'grade'}),
        input(null, 'Section', 'section', {onkeyup, name: 'section'}),
        button(null, 'search', search),
        searchResult
    );
};

function Canvas(font) {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d");
    context.font = font;
    return context;
}
