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
/**@typedef {string[]} teachers */
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
 *             got: float,
 *             sweet: float,
 *             cold: float,
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
const {
    div,
    input,
    button,
    span,
    svg,
    Signal,
    State,
    ClassList,
    br,
    table,
    tr,
    th,
    td,
    p,
    img,
    thead,
    tbody, any, colgroup, col
} = require('../domHelper');
/*ExcludeEnd*/

module.exports = function () {
    console.log('Course search Init');
    // static
    let styles = async_require('./courseSearch.css');
    const expendArrow = img('https://wavjaby.github.io/NCKUpp/res/assets/down_arrow_icon.svg', 'expendDownArrow');
    const searchResult = new Signal();
    const instructorInfoBubble = InstructorInfoBubble();
    const instructorDetailWindow = InstructorDetailWindow();
    const courseDetailWindow = CourseDetailWindow();
    const expandButtons = [];

    let courseSearch, courseSearchForm;
    // data
    let nckuHubCourseID = null;
    let urschoolData = null;

    // quary string
    let lastQueryString;
    let searching;

    async function onRender() {
        console.log('Course search Render');
        search();
        (styles = await styles).add();
    }

    function onDestroy() {
        console.log('Course search Destroy');
        styles.remove();
    }

    function onkeyup(e) {
        if (e.key === 'Enter') search();
    }

    async function search() {
        if (searching) return;
        searching = true;
        // get all course ID
        if (nckuHubCourseID === null)
            nckuHubCourseID = (await fetchApi('/nckuhub')).data;

        // get urschool data
        if (urschoolData === null)
            urschoolData = (await fetchApi('/urschool')).data;

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
        /**
         * @type {{err: string, msg: string, warn: string, login: boolean, data: CourseData[]}}
         */
        const result = (await fetchApi('/search?' + queryString));

        console.log(result);
        if (!result.data) {
            // TODO: handle error
            searching = false;
            return;
        }

        const font = '18px ' + getComputedStyle(document.body).fontFamily;
        const canvas = new Canvas(font);
        const nckuHubRequestIDs = [];
        const nckuHubResponseData = {};

        // parse result
        let deptLen = 0;
        let timeLen = 0;
        for (const data of result.data) {
            data.dn = data.dn.split(' ')[0];
            let cache;
            if ((cache = canvas.measureText(data.dn).width + 1) > deptLen)
                deptLen = cache;

            // parse
            data.ts = data.ts.replace(/\*/g, '').split(' ').map(i => {
                for (const j of urschoolData) if (j[2] === i) return j;
                return i;
            });
            data.parsedTime = data.t.map(i => {
                i = i.split(',');
                return '[' + i[0] + ']' + i[1]
            }).join(', ');
            if ((cache = canvas.measureText(data.parsedTime).width + 1) > timeLen)
                timeLen = cache;
            delete data.t;

            // nckuhub
            const deptAndID = data.sn.split('-');
            let nckuHubID = nckuHubCourseID[deptAndID[0]];
            if (nckuHubID) nckuHubID = nckuHubID[deptAndID[1]];
            if (data.sn.length > 0 && nckuHubID) {
                nckuHubRequestIDs.push(nckuHubID);
                nckuHubResponseData[data.sn] = new Signal();
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

        searchResult.set({data: result.data, nckuHubResponseData, deptLen, timeLen});
        expendAllItem();
        searching = false;
    }

    // expend info
    function expendAllItem() {
        for (const i of expandButtons) i();
    }

    function openInstructorDetailWindow(info) {
        fetchApi(`/urschool?id=${info[0]}&mode=${info[1]}`).then(response => {
            // TODO: handle error
            const data = response.data;
            data.info = info;
            instructorDetailWindow.set(data);
        });
    }

    function renderResult(state) {
        if (!state) return div();
        expandButtons.length = 0;

        // render element
        return tbody(null, state.data.map(data => {
            const resultItemClass = new ClassList();
            const nckuHubData = state.nckuHubResponseData[data.sn];
            const expendButton = expendArrow.cloneNode();
            expendButton.onclick = toggleCourseInfo;
            expandButtons.push(toggleCourseInfo);

            // course short information
            let expandable, measureReference;

            function toggleCourseInfo(e) {
                const show = resultItemClass.toggle('extend');
                if (show) expandable.style.height = measureReference.clientHeight + "px";
                else expandable.style.height = null;
                if (e) e.stopPropagation();
            }

            // open detail window
            function openCourseDetailWindow() {
                if (!nckuHubData || !nckuHubData.state || nckuHubData.state.noData) return;
                courseDetailWindow.set([nckuHubData.state, data]);
            }

            // render result item
            return tr(resultItemClass, {
                    onclick: openCourseDetailWindow,
                    // prevent double click select text
                    onmousedown: preventDoubleClick,
                },
                // title sections
                td(null, null,
                    expendButton,
                    // info
                    expandable = div('expandable', div(null, measureReference = div('info',
                        data.ci.length > 0 ? span(data.ci, 'note') : null,
                        data.cl.length > 0 ? span(data.cl, 'limit red') : null,

                        // Instructor
                        span('Instructor: ', 'instructor'),
                        data.ts.map(i =>
                            button('instructorBtn', i instanceof Array ? i[2] : i,
                                e => {
                                    if (i instanceof Array)
                                        openInstructorDetailWindow(i);
                                    e.stopPropagation();
                                },
                                {
                                    onmouseenter: e => {
                                        if (i instanceof Array)
                                            instructorInfoBubble.set({
                                                target: e.target,
                                                offsetY: courseSearch.scrollTop,
                                                data: i
                                            });
                                    },
                                    onmouseleave: instructorInfoBubble.hide
                                })
                        ),
                    ))),
                ),
                td(data.dn, 'departmentName'),
                td(data.sn, 'serialNumber'),
                td(data.parsedTime, 'courseTime'),
                td(data.cn, 'courseName'),
                // ncku Hub
                nckuHubData === undefined ? td() :
                    State(nckuHubData, /**@param {NckuHub} nckuhub*/nckuhub => {
                        if (nckuhub) {
                            if (nckuhub.noData) return td();

                            const reward = data.got = nckuhub.got;
                            const sweet = data.sweet = nckuhub.sweet;
                            const cool = data.cold = nckuhub.cold;
                            return td(null, 'nckuhub',
                                span(reward.toFixed(1), 'reward'),
                                span(sweet.toFixed(1), 'sweet'),
                                span(cool.toFixed(1), 'cool'),
                            );
                        }
                        return td('Loading...', 'nckuhub');
                    }),
            );
            // return end
        }));
    }

    const sortArrow = expendArrow.cloneNode();
    const sortArrowClass = new ClassList('sortArrow');
    sortArrowClass.init(sortArrow);

    function sortKey(key, element) {
        if (!searchResult.state || !searchResult.state.data || searchResult.state.data.length === 0) return;

        if (searchResult.state.sort !== key) {
            searchResult.state.sort = key;
            element.appendChild(sortArrow);
            searchResult.state.data.sort((a, b) => sortToEnd(a[key]) ? 1 : sortToEnd(b[key]) ? -1 : a[key].localeCompare(b[key]));
            let end = 0;
            for (; end < searchResult.state.data.length; end++)
                if (sortToEnd(searchResult.state.data[end][key])) break;
            searchResult.state.sortLastIndex = end > 0 ? end : null;
            sortArrow.className = 'sortArrow';
        } else {
            if (searchResult.state.sortLastIndex !== null)
                reverseArray(searchResult.state.data, 0, searchResult.state.sortLastIndex);
            else
                searchResult.state.data.reverse();
            sortArrowClass.toggle('reverse');
        }
        searchResult.update();
        expendAllItem();
    }

    function sortNckuhubKey(key, element) {
        if (!searchResult.state || !searchResult.state.data || searchResult.state.data.length === 0) return;
        const keys = ['sweet', 'cold', 'got'];
        keys.splice(keys.indexOf(key), 1);

        if (searchResult.state.sort !== key) {
            searchResult.state.sort = key;
            element.appendChild(sortArrow);
            searchResult.state.data.sort((a, b) =>
                sortToEnd(a[key]) ? 1 : sortToEnd(b[key]) ? -1 : (
                    Math.abs(b[key] - a[key]) < 1e-10 ? (
                        sortToEnd(a[keys[0]]) ? 1 : sortToEnd(b[keys[0]]) ? -1 : (
                            Math.abs(b[keys[0]] - a[keys[0]]) < 1e-10 ? (
                                sortToEnd(a[keys[1]]) ? 1 : sortToEnd(b[keys[1]]) ? -1 : (
                                    Math.abs(b[keys[1]] - a[keys[1]]) < 1e-10 ? 0 : b[keys[1]] - a[keys[1]]
                                )
                            ) : b[keys[0]] - a[keys[0]]
                        )
                    ) : b[key] - a[key]
                ));
            let end = 0;
            for (; end < searchResult.state.data.length; end++)
                if (sortToEnd(searchResult.state.data[end][key])) break;
            searchResult.state.sortLastIndex = end > 0 ? end : null;
            sortArrow.className.baseVal = 'sortArrow';
        } else {
            if (searchResult.state.sortLastIndex !== null)
                reverseArray(searchResult.state.data, 0, searchResult.state.sortLastIndex);
            else
                searchResult.state.data.reverse();
            sortArrowClass.toggle('reverse');
        }
        searchResult.update();
        expendAllItem();
    }

    return courseSearch = div('courseSearch',
        {onRender, onDestroy},
        courseSearchForm = div('form',
            input(null, 'Serial number', 'serialNumber', {onkeyup, name: 'serial'}),
            input(null, 'Course name', 'courseName', {onkeyup, name: 'course'}),
            input(null, 'Dept ID', 'deptId', {onkeyup, name: 'dept', value: 'F7'}),
            input(null, 'Instructor', 'instructor', {onkeyup, name: 'teacher'}),
            input(null, 'Day', 'day', {onkeyup, name: 'day'}),
            input(null, 'Grade', 'grade', {onkeyup, name: 'grade'}),
            input(null, 'Section', 'section', {onkeyup, name: 'section'}),
            button(null, 'search', search),
        ),
        table('result', {'cellPadding': 0},
            colgroup(null,
                // col(null, {'span': '2', 'style': 'visibility: collapse'}),
            ),
            thead('noSelect',
                tr(null,
                    th(null, null, expendArrow.cloneNode()),
                    th('Dept', 'departmentName', {onclick: (e) => sortKey('dn', e.target)}),
                    th('Serial', 'serialNumber', {onclick: (e) => sortKey('sn', e.target)}),
                    th('Time', 'courseTime', {onclick: (e) => sortKey('parsedTime', e.target)}),
                    th('Course name', 'courseName', {onclick: (e) => sortKey('cn', e.target)}),
                    th(null, 'nckuhub',
                        div(null, span('Reward', 'reward'), {onclick: (e) => sortNckuhubKey('got', e.target)}),
                        div(null, span('Sweet', 'sweet'), {onclick: (e) => sortNckuhubKey('sweet', e.target)}),
                        div(null, span('Cool', 'cool'), {onclick: (e) => sortNckuhubKey('cold', e.target)}),
                    ),
                ),
                tr('filterSection', {'colSpan': '100'},
                    img('./res/assets/funnel_icon.svg'),
                    div('options',
                        input('filter', 'Teacher, Course name, Serial number'),
                    ),
                ),
            ),
            State(searchResult, renderResult),
        ),
        instructorInfoBubble,
        instructorDetailWindow,
        courseDetailWindow,
    );
};

function InstructorInfoElement(
    [id, mod,
        name, dept, job,
        recommend, reward, articulate, pressure, sweet,
        averageScore, academicQualifications, note, nickname, rollCall
    ]) {
    return div(null,
        div('rate',
            recommend !== -1 && reward !== -1 && articulate !== -1 && pressure !== -1 && sweet !== -1
                ? table(null,
                    tr(null, th('Recommend'), th('Reward'), th('Articulate'), th('Pressure'), th('Sweet')),
                    tr(null, td(recommend, getColor(recommend)), td(reward, getColor(reward)), td(articulate, getColor(articulate)), th(pressure, getColor(5 - pressure)), td(sweet, getColor(sweet))),
                )
                : null,
        ),
        div('info',
            table(null,
                tr(null, th('Average score'), td(averageScore)),
                tr(null, th('Average score'), td(averageScore)),
                tr(null, th('Note'), td(note)),
                tr(null, th('Nickname'), td(nickname)),
                tr(null, th('Department'), td(dept)),
                tr(null, th('Job title'), td(job)),
                tr(null, th('Roll call method'), td(rollCall)),
            ),
        ),
        span('Academic qualifications: ' + academicQualifications),
    );
}

function InstructorInfoBubble() {
    const signal = new Signal();
    const classList = new ClassList('instructorInfo');
    const state = State(signal, state => {
        if (!state) return div(classList);

        const bound = state.target.getBoundingClientRect();
        const element = InstructorInfoElement(state.data);
        element.insertBefore(span(state.data[2]), element.firstChild);
        element.style.left = bound.left + 'px';
        classList.init(element);

        setTimeout(() => {
            classList.add('show');
            element.style.top = (bound.top + state.offsetY - 15 - element.offsetHeight - state.target.offsetHeight) + 'px';
        }, 0);
        return element;
    });
    state.set = signal.set;
    state.hide = () => classList.remove('show');
    return state;
}

function InstructorDetailWindow() {
    return PopupWindow(({id, info, tags, comments, reviewerCount, takeCourseCount, takeCourseUser}) => {
        const instructorInfo = InstructorInfoElement(info);
        instructorInfo.className = 'instructorInfo';
        return div('instructorDetailWindow',
            div('title',
                span('Evaluation for'),
                div('name',
                    span(info[3]),
                    span(info[2]),
                    span(info[4]),
                ),
            ),
            div('tags',
                tags.map(i => {
                    return span(i[1]);
                })
            ),
            div('reviewerCount',
                span('Total votes'),
                span(reviewerCount),
            ),
            instructorInfo,
            div('comments',
                comments.map(i => {
                    return div('item',
                        img(`https://graph.facebook.com/v2.8/${i.profile}/picture?type=square`, 'profile'),
                        div('body',
                            span(i.created_at, 'createDate'),
                            span(i.body, 'message'),
                        ),
                    );
                })
            ),
        );
    });
}

function getColor(number) {
    return number < 2 ? 'red' : number < 4 ? 'yellow' : 'blue';
}

function CourseDetailWindow() {
    return PopupWindow(/**@param {[NckuHub, CourseData]} data*/([nckuhub, ncku]) => {
        return div('courseDetailWindow',
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
                    p(comment.comment, 'comment'),
                )),
            ),
            br(),
            br(),
            br(),
            br(),
            span(JSON.stringify(nckuhub, null, 2)),
        );
    });
}

function PopupWindow(onDataChange) {
    const popupSignal = new Signal();
    const popupClass = new ClassList('popupWindow');
    const closeButton = button('closeButton', '', () => popupClass.remove('open'), div('icon'));
    const popupWindow = div(popupClass, State(popupSignal, state => {
        if (!state) return div();
        const body = onDataChange(state);
        body.insertBefore(closeButton, body.firstChild);
        popupClass.add('open');
        return body;
    }));
    popupWindow.set = popupSignal.set;
    return popupWindow;
}

function Canvas(font) {
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d");
    context.font = font;
    return context;
}

function preventDoubleClick(e) {
    if (e.detail > 1)
        e.preventDefault();
}

function sortToEnd(data) {
    return data === undefined || data.length === 0;
}

function reverseArray(array, start, end) {
    while (start < end) {
        let t = array[start];
        array[start++] = array[--end];
        array[end] = t;
    }
}