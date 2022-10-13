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
const {div, input, button, span, svg, Signal, State, ClassList} = require('../domHelper');
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
        // get all course ID
        if (!nckuHubCourseID) {
            fetchApi('/nckuhub').then(i => {
                nckuHubCourseID = i.data;
                searching = false;
                search();
            });
            return;
        }

        // generate query string
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

        // fetch data
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

        searchResult.appendChild(div('header',
            span('Dept', 'departmentName', {style: `width:${deptLen}px`}),
            span('Serial', 'serialNumber'),
            span('Time', 'courseTime', {style: `width:${timeLen}px`}),
            span('Name', 'courseName'),
            div('nckuhub',
                span('Reward', 'reward'),
                span('Sweet', 'sweet'),
                span('Cold', 'cold'),
            ),
        ));
        const body = div('body');
        searchResult.appendChild(body);

        for (const data of result.data) {
            const nckuHubData = new Signal();
            const courseInfo = new Signal();
            const infoClass = new ClassList('info');
            body.appendChild(div(infoClass, {
                    onclick: toggleCourseDetails,
                    onmousedown: (e) => {if (e.detail > 1) e.preventDefault();},
                },
                expendArrow.cloneNode(true),
                span(data.dn, 'departmentName', {style: `width:${deptLen}px`}),
                span(data.sn, 'serialNumber'),
                span(data.parseedTime, 'courseTime', {style: `width:${timeLen}px`}),
                span(data.cn, 'courseName'),

                State(nckuHubData, (state) => {
                    if (state) {
                        if (state.noNckuHubID)
                            return span('No result', 'nckuhub');
                        const reward = parseFloat(state.got);
                        const sweet = parseFloat(state.sweet);
                        const cold = parseFloat(state.cold);
                        if (reward === 0 && sweet === 0 && cold === 0)
                            return span('No result', 'nckuhub');
                        return div('nckuhub',
                            span(reward.toFixed(1), 'reward'),
                            span(sweet.toFixed(1), 'sweet'),
                            span(cold.toFixed(1), 'cold'),
                        );
                    }
                    return span('Loading...', 'nckuhub');
                }),
                courseInfo,
            ));

            // get ncku hub data
            const deptAndID = data.sn.split('-');
            let nckuHubID = nckuHubCourseID[deptAndID[0]];
            if (nckuHubID) nckuHubID = nckuHubID[deptAndID[1]];
            if (data.sn.length > 0 && nckuHubID)
                fetchApi('/nckuhub?id=' + nckuHubID).then(i => nckuHubData.set(i.data));
            else
                nckuHubData.set({noNckuHubID: true});

            toggleCourseDetails();

            function toggleCourseDetails() {
                renderCourseDetails(infoClass.toggle('extend'), courseInfo, data);
            }
        }
        searching = false;
    }

    function renderCourseDetails(show, courseInfo, data) {
        if (courseInfo.state == null)
            courseInfo.set(div('expandable', div('details',
                span(data.ci),
                span(data.cl),
            )));
        if (show)
            courseInfo.state.style.height = courseInfo.state.firstChild.clientHeight + "px";
        else
            courseInfo.state.style.height = null;
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
