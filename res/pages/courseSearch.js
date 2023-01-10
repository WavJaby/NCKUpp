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
/**@typedef {int} available */
/**@typedef {string[]} time */
/**@typedef {string} moodle */
/**@typedef {string} outline */
/**@typedef {string} preferenceEnter */
/**@typedef {string} addCourse */
/**@typedef {string} preRegister */
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
 *     m: moodle,
 *     o: outline,
 *     pe: preferenceEnter,
 *     ac: addCourse,
 *     pr: preRegister,
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
    tbody, colgroup, TextState, ShowIf, col
} = require('../domHelper');
/*ExcludeEnd*/

module.exports = function () {
    console.log('Course search Init');
    // static
    let styles = async_require('./courseSearch.css');
    const expendArrow = img('./res/assets/down_arrow_icon.svg', 'expendDownArrow');
    const searchResult = new Signal();
    const showPageLoading = new Signal();
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
        // close navLinks when using mobile devices
        navLinksClass.remove('open');
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
        searchResult.set({loading: true});

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

        const nckuHubRequestIDs = [];
        const nckuHubResponseData = {};

        // parse result
        for (const data of result.data) {
            data.dn = data.dn.split(' ')[0];

            // parse
            data.ts = data.ts.split(' ').map(i => {
                if (i.length === 0) return 'undecided';
                for (const j of urschoolData) if (j[2] === i) return j;
                return i;
            });
            data.parsedTime = data.t.map(i => {
                i = i.split(',');
                return '[' + i[0] + ']' + i[1]
            }).join(', ');
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
        const chunkSize = 20;
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

        searchResult.set({data: result.data, nckuHubResponseData});
        expendAllItem();
        searching = false;
    }

    // Expend info
    function expendAllItem() {
        for (const i of expandButtons) i();
    }

    function openInstructorDetailWindow(info) {
        showPageLoading.set(true);
        fetchApi(`/urschool?id=${info[0]}&mode=${info[1]}`).then(response => {
            // TODO: handle error
            const data = response.data;
            data.info = info;
            instructorDetailWindow.set(data);
            showPageLoading.set(false);
        });
    }

    // Render result
    function renderResult(state) {
        if (!state) return div();
        if (state.loading) return div('loading', loadingElement.cloneNode(true));
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
                if (resultItemClass.toggle('extend'))
                    expandable.style.height = measureReference.clientHeight + "px";
                else
                    expandable.style.height = null;
                if (e) e.stopPropagation();
            }

            // open detail window
            function openCourseDetailWindow() {
                if (!nckuHubData || !nckuHubData.state || nckuHubData.state.noData) return;
                courseDetailWindow.set([nckuHubData.state, data]);
            }

            // render result item
            return tr(resultItemClass,
                // title sections
                td(null, null,
                    expendButton,
                    // info
                    expandable = div('expandable',
                        div('container', measureReference = div('info',
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
                        )),
                        div('splitLine', div()),
                    ),
                ),
                td(data.dn, 'departmentName'),
                td(data.sn, 'serialNumber'),
                td(data.ct, 'courseType'),
                td(data.parsedTime, 'courseTime'),
                td(data.cn, 'courseName'),
                td(`${data.s}/${data.a}`, 'available'),
                // ncku Hub
                nckuHubData === undefined ? td() :
                    State(nckuHubData, /**@param {NckuHub} nckuhub*/nckuhub => {
                        if (nckuhub) {
                            if (nckuhub.noData) return td();

                            data.got = nckuhub.got;
                            data.sweet = nckuhub.sweet;
                            data.cold = nckuhub.cold;
                            if (nckuhub.rate_count === 0) return td('No rating', 'nckuhub');

                            return td(null, 'nckuhub', {onclick: openCourseDetailWindow},
                                span(data.got.toFixed(1), 'reward'),
                                span(data.sweet.toFixed(1), 'sweet'),
                                span(data.cold.toFixed(1), 'cool'),
                            );
                        }
                        return td('Loading...', 'nckuhub');
                    }),
            );
            // return end
        }));
    }

    // Sort
    const sortArrow = expendArrow.cloneNode();
    const sortArrowClass = new ClassList('sortArrow');
    sortArrowClass.init(sortArrow);

    function sortResultItem(key, element, method) {
        if (searchResult.state.sort !== key) {
            searchResult.state.sort = key;
            searchResult.state.data.sort(method);
            let end = 0;
            for (; end < searchResult.state.data.length; end++)
                if (sortToEnd(searchResult.state.data[end][key])) break;
            searchResult.state.sortLastIndex = end > 0 ? end : null;
            sortArrowClass.remove('reverse');
            element.appendChild(sortArrow);
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

    function sortKey() {
        if (!searchResult.state || !searchResult.state.data || searchResult.state.data.length === 0) return;
        const key = this.key;
        sortResultItem(key, this, (a, b) => sortToEnd(a[key]) ? 1 : sortToEnd(b[key]) ? -1 : a[key].localeCompare(b[key]));
    }

    function sortIntKey() {
        if (!searchResult.state || !searchResult.state.data || searchResult.state.data.length === 0) return;
        const key = this.key;
        sortResultItem(key, this, (a, b) => (sortToEnd(a[key]) ? 1 : sortToEnd(b[key]) ? -1 : b[key] - a[key]));
    }

    function sortNckuhubKey() {
        if (!searchResult.state || !searchResult.state.data || searchResult.state.data.length === 0) return;
        const key = this.key;
        const keys = ['sweet', 'cold', 'got'];
        keys.splice(keys.indexOf(key), 1);

        sortResultItem(key, this, (a, b) =>
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
            ))
    }

    // Filter
    let expendTimeout;
    let lastFilterKey;

    function filterChange() {
        if ((!searchResult.state || !searchResult.state.data || searchResult.state.data.length === 0) && !searchResult.state.orignalData) return;
        const key = this.value.trim();
        // if word not finish
        if (key.length > 0 && !key.match(/^[\u4E00-\u9FFF（）\w-]+$/g)) return;

        // if same
        if (lastFilterKey === key) return;
        lastFilterKey = key;

        if (!searchResult.state.orignalData)
            searchResult.state.orignalData = searchResult.state.data;


        searchResult.state.data = searchResult.state.orignalData.filter(i =>
            i.cn.indexOf(key) !== -1 ||
            i.sn.indexOf(key) !== -1 ||
            i.ts.find(i => (i instanceof Array ? i[2] : i).indexOf(key) !== -1)
        );
        searchResult.update();
        if (expendTimeout)
            clearTimeout(expendTimeout);
        expendTimeout = setTimeout(expendAllItem, 500);
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
                col(null),
                // col(null, {'style': 'visibility: collapse'}),
            ),
            State(searchResult, renderResult),
            thead('noSelect',
                tr(null,
                    th(null, null,
                        // filter options
                        div('filterSection',
                            div(null, div('options',
                                img('./res/assets/funnel_icon.svg'),
                                div('filter', input(null, 'Teacher, Course name, Serial number', null, {
                                    oninput: filterChange,
                                    onpropertychange: filterChange
                                })),
                                div('resultCount',
                                    span('Result count: '),
                                    span(TextState(searchResult, (state) => state && !state.loading ? state.data.length : ''))
                                ),
                            )),
                        ),
                        div('extendAll', expendArrow.cloneNode()),
                    ),
                    th('Dept', 'departmentName', {key: 'dn', onclick: sortKey}),
                    th('Serial', 'serialNumber', {key: 'sn', onclick: sortKey}),
                    th('Type', 'courseType', {key: 'ct', onclick: sortKey}),
                    th('Time', 'courseTime', {key: 'parsedTime', onclick: sortKey}),
                    th('Course name', 'courseName', {key: 'cn', onclick: sortKey}),
                    th('Sel/Avail', 'available', {key: 'a', onclick: sortIntKey}),
                    th(null, 'nckuhub',
                        div(null, span('Reward', 'reward'), {key: 'got', onclick: sortNckuhubKey}),
                        div(null, span('Sweet', 'sweet'), {key: 'sweet', onclick: sortNckuhubKey}),
                        div(null, span('Cool', 'cool'), {key: 'cold', onclick: sortNckuhubKey}),
                    ),
                ),
            ),
        ),
        instructorInfoBubble,
        instructorDetailWindow,
        courseDetailWindow,
        ShowIf(showPageLoading, div('loading', loadingElement.cloneNode(true))),
    );
};

function InstructorInfoElement(
    [id, mode,
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
        classList.add('show');

        setTimeout(() => {
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
            nckuhub.rate_count === 0 ? div('rates') : div('rates',
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

function sortToEnd(data) {
    return data === undefined || data.length === 0 || data < 1e-10;
}

function reverseArray(array, start, end) {
    while (start < end) {
        let t = array[start];
        array[start++] = array[--end];
        array[end] = t;
    }
}