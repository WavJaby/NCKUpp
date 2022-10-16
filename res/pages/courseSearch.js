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

/**
 * @typedef {{
 *     got: float,
 *     sweet: float,
 *     cold: float,
 *     rate_count: int,
 *     comment: [{
 *         id: int,
 *         comment: string,
 *         semester: string
 *     }],
 *
 *     parsedRates: {
 *         post_id: {
 *             id: int,
 *             user_id: int,
 *             post_id: int,
 *             got: int,
 *             sweet: int,
 *             cold: int,
 *             like: int,
 *             dislike: int,
 *             hard: int,
 *             recommand: int,
 *             give: int,
 *             course_name: string,
 *             teacher: string
 *         }
 *     },
 *     noData: boolean
 * }} NckuHub
 */

/*ExcludeStart*/
const {div, input, button, span, svg, Signal, State, ClassList, br} = require('../domHelper');
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
        if (queryString === lastQueryString) {
            searching = false;
            return;
        }
        lastQueryString = queryString;

        console.log('Search');
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
        const nckuHubRequestIDs = [];
        const nckuHubResponseData = {};
        const popupWindow = PopupWindow();

        // parse result
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

            // nckuhub
            const deptAndID = data.sn.split('-');
            let nckuHubID = nckuHubCourseID[deptAndID[0]];
            if (nckuHubID) nckuHubID = nckuHubID[deptAndID[1]];
            if (data.sn.length > 0 && nckuHubID) {
                nckuHubRequestIDs.push(nckuHubID);
                nckuHubResponseData[data.sn] = (new Signal());
            }
        }

        // get nckuhub data
        const chunkSize = 5;
        const nckuHubResponseDataArr = Object.values(nckuHubResponseData);
        for (let i = 0; i < nckuHubRequestIDs.length; i += chunkSize) {
            const chunk = nckuHubRequestIDs.slice(i, i + chunkSize);
            fetchApi('/nckuhub?id=' + chunk.join(',')).then(({data}) => {
                for (let j = 0; j < chunk.length; j++) {
                    /**@type NckuHub*/
                    const nckuhub = data[j];
                    nckuhub.got = parseFloat(nckuhub.got);
                    nckuhub.sweet = parseFloat(nckuhub.sweet);
                    nckuhub.cold = parseFloat(nckuhub.cold);

                    nckuhub.noData = nckuhub.rate_count === 0 && nckuhub.comment.length === 0;

                    nckuhub.parsedRates = nckuhub.rates.reduce((a, v) => {
                        a[v.post_id] = v;
                        return a;
                    }, {});
                    delete data[j].rates;
                    nckuHubResponseDataArr[i + j].set(nckuhub);
                }
            });
        }

        // add header
        searchResult.appendChild(div('header',
            span('Dept', 'departmentName', {style: `width:${deptLen}px`}),
            span('Serial', 'serialNumber'),
            span('Time', 'courseTime', {style: `width:${timeLen}px`}),
            span('Name', 'courseName'),
            div('nckuhub',
                span('Reward', 'reward'),
                span('Sweet', 'sweet'),
                span('Cool', 'cool'),
            ),
        ));
        // add body
        const body = div('body');
        searchResult.appendChild(body);
        searchResult.appendChild(popupWindow);

        // add result
        for (const data of result.data) {
            const infoClass = new ClassList('info');
            const expendButton = expendArrow.cloneNode(true);
            expendButton.onclick = toggleCourseDetails;
            const nckuHubData = nckuHubResponseData[data.sn];

            let courseDetails;

            body.appendChild(div(infoClass, {
                    onclick: openNckuHubDetails,
                    onmousedown: (e) => {if (e.detail > 1) e.preventDefault();},
                },
                div(null,
                    expendButton,
                    span(data.dn, 'departmentName', {style: `width:${deptLen}px`}),
                    span(data.sn, 'serialNumber'),
                    span(data.parseedTime, 'courseTime', {style: `width:${timeLen}px`}),
                    span(data.cn, 'courseName'),
                ),

                // ncku Hub
                nckuHubData
                    ? State(nckuHubData, /**@param {NckuHub} nckuhub*/(nckuhub) => {
                        if (nckuhub) {
                            if (nckuhub.noData) return div();

                            const reward = nckuhub.got;
                            const sweet = nckuhub.sweet;
                            const cool = nckuhub.cold;
                            return div('nckuhub',
                                span(reward.toFixed(1), 'reward'),
                                span(sweet.toFixed(1), 'sweet'),
                                span(cool.toFixed(1), 'cool'),
                            );
                        }
                        return span('Loading...', 'nckuhub');
                    })
                    : div(),

                // details
                courseDetails = div('expandable', div('details',
                    data.ci.length > 0 ? span(data.ci, 'info') : null,
                    data.cl.length > 0 ? span(data.cl, 'limit red') : null,
                    span('Instructor: ' + data.ts, 'instructor'),
                )),
            ));

            toggleCourseDetails();

            function toggleCourseDetails() {
                const show = infoClass.toggle('extend');
                if (show)
                    courseDetails.style.height = courseDetails.firstChild.clientHeight + "px";
                else
                    courseDetails.style.height = null;
            }

            function openNckuHubDetails() {
                if (!nckuHubData || !nckuHubData.state || nckuHubData.state.noData) return;
                popupWindow.set([nckuHubData.state, data]);
            }
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

function PopupWindow() {
    const popupSignal = new Signal();
    const popupClass = new ClassList('popupWindow');
    const popupState = State(popupSignal, /**@param {[NckuHub, CourseData]} data*/(data) => {
        if (!data) return div();
        const [nckuhub, ncku] = data;
        popupClass.add('open');
        return div(null,
            button(null, 'x', () => popupClass.remove('open')),
            // rates
            span(`Evaluation(${nckuhub.rate_count})`, 'title'),
            div('rates',
                div(null, div('rateBox',
                    span('Reward'),
                    span(nckuhub.got.toFixed(1)),
                )),
                div(null, div('rateBox',
                    span('Sweetness'),
                    span(nckuhub.sweet.toFixed(1)),
                )),
                div(null, div('rateBox',
                    span('Cool'),
                    span(nckuhub.cold.toFixed(1)),
                )),
            ),
            // comment
            span(`Comments(${nckuhub.comment.length})`, 'title'),
            div('comments',
                ...nckuhub.comment.map(comment => div('commentBlock',
                    span(comment.semester, 'semester'),
                    span(comment.comment, 'comment'),
                )),
            ),
            br(),
            br(),
            br(),
            br(),
            span(JSON.stringify(nckuhub, null, 2)),
        );
    });
    const popupWindow = div(popupClass, popupState);
    popupWindow.set = popupSignal.set;
    return popupWindow;
}

function Canvas(font) {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d");
    context.font = font;
    return context;
}