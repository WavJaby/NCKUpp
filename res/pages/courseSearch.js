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
    th, td, TextState, p, img
} = require('../domHelper');
/*ExcludeEnd*/
/**@type {{add:function(), remove: function(), rules: CSSStyleRule}}*/
const styles = require('./courseSearch.css');

module.exports = function () {
    console.log('courseSearch Init');
    const expendArrow = svg('./res/assets/down_arrow_icon.svg', 'expendDownArrow');
    const searchResult = new Signal();
    const instructorInfoBubble = InstructorInfoBubble();
    const instructorDetailWindow = InstructorDetailWindow();
    const courseDetailWindow = CourseDetailWindow();
    let expandElements;
    let courseSearch;

    console.log('Course search Init');
    // data
    let nckuHubCourseID = null;
    let urschoolData = null;

    // quary string
    let lastQueryString;
    let searching;

    function onRender() {
        console.log('Course search Render');
        styles.add();
        search();
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
        if (nckuHubCourseID == null)
            nckuHubCourseID = (await fetchApi('/nckuhub')).data;

        // get urschool data
        if (urschoolData == null)
            urschoolData = (await fetchApi('/urschool')).data;

        // generate query string
        const queryData = [];
        for (const /**@type HTMLElement*/ node of courseSearch.childNodes) {
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
         * @type {{data:CourseData[]}}
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
            data.parseedTime = data.t.map(i => {
                i = i.split(',');
                return '[' + i[0] + ']' + i[1]
            }).join(', ');
            if ((cache = canvas.measureText(data.parseedTime).width + 1) > timeLen)
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

        expandElements = [];
        searchResult.set({data: result.data, nckuHubResponseData, deptLen, timeLen});
        for (const i of expandElements) i();
        expandElements = null;
        searching = false;
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

        return div('result',
            div('header',
                span('Dept', 'departmentName', {style: `width:${state.deptLen}px`}),
                span('Serial', 'serialNumber'),
                span('Time', 'courseTime', {style: `width:${state.timeLen}px`}),
                span('Name', 'courseName'),
                div('nckuhub',
                    span('Reward', 'reward'),
                    span('Sweet', 'sweet'),
                    span('Cool', 'cool'),
                ),
            ),
            ...state.data.map(data => {
                const resultItemClass = new ClassList('item');
                const nckuHubData = state.nckuHubResponseData[data.sn];
                const expendButton = expendArrow.cloneNode(true);
                expendButton.onclick = toggleCourseInfo;
                expandElements.push(toggleCourseInfo);

                // course short information
                let courseInfo;

                function toggleCourseInfo(e) {
                    const show = resultItemClass.toggle('extend');
                    if (show) courseInfo.style.height = courseInfo.firstChild.clientHeight + "px";
                    else courseInfo.style.height = null;
                    if (e) e.stopPropagation();
                }

                // open detail window
                function openCourseDetailWindow() {
                    if (!nckuHubData || !nckuHubData.state || nckuHubData.state.noData) return;
                    courseDetailWindow.set([nckuHubData.state, data]);
                }

                // render result item
                return div(resultItemClass, {
                        onclick: openCourseDetailWindow,
                        // prevent double click select text
                        onmousedown: preventDoubleClick,
                    },
                    div('header',
                        // title sections
                        expendButton,
                        span(data.dn, 'departmentName', {style: `width:${state.deptLen}px`}),
                        span(data.sn, 'serialNumber'),
                        span(data.parseedTime, 'courseTime', {style: `width:${state.timeLen}px`}),
                        span(data.cn, 'courseName'),

                        // ncku Hub
                        State(nckuHubData, /**@param {NckuHub} nckuhub*/nckuhub => {
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
                        }),
                    ),

                    // info
                    courseInfo = div('expandable', div('info',
                        data.ci.length > 0 ? span(data.ci, 'note') : null,
                        data.cl.length > 0 ? span(data.cl, 'limit red') : null,

                        // Instructor
                        span('Instructor: ', 'instructor'),
                        ...data.ts.map(i =>
                            button('instructorBtn', i instanceof Array ? i[2] : i,
                                e => {
                                    openInstructorDetailWindow(i);
                                    e.stopPropagation();
                                },
                                {
                                    onmouseenter: e => {
                                        if (i instanceof Array) {
                                            instructorInfoBubble.set({
                                                target: e.target,
                                                offsetY: courseSearch.scrollTop,
                                                data: i
                                            });
                                            setTimeout(instructorInfoBubble.show, 0);
                                        }
                                    },
                                    onmouseleave: instructorInfoBubble.hide
                                })
                        ),
                    )),
                );
            }),
        );
    }

    return courseSearch = div('courseSearch',
        {onRender, onDestroy},
        input(null, 'Serial number', 'serialNumber', {onkeyup, name: 'serial'}),
        input(null, 'Course name', 'courseName', {onkeyup, name: 'course'}),
        input(null, 'Dept ID', 'deptId', {onkeyup, name: 'dept', value: 'F7'}),
        input(null, 'Instructor', 'instructor', {onkeyup, name: 'teacher'}),
        input(null, 'Day', 'day', {onkeyup, name: 'day'}),
        input(null, 'Grade', 'grade', {onkeyup, name: 'grade'}),
        input(null, 'Section', 'section', {onkeyup, name: 'section'}),
        button(null, 'search', search),
        State(searchResult, renderResult),
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
                    tr(null, td(recommend, getColor(recommend)), td(reward, getColor(reward)), td(articulate, getColor(articulate)), td(pressure, getColor(pressure)), td(sweet, getColor(sweet))),
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
        element.style.top = (bound.top + state.offsetY - 340) + 'px';
        element.style.left = bound.left + 'px';
        element.insertBefore(span('Name: ' + state.data[2]), element.firstChild);
        classList.init(element);
        return element;
    });
    state.set = signal.set;
    state.hide = () => classList.remove('show');
    state.show = () => classList.add('show');
    return state;
}

function InstructorDetailWindow() {
    return PopupWindow(({id, info, tags, comments, takeCourseCount, takeCourseUser}) => {
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