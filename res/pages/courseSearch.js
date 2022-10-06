'use strict';

/*ExcludeStart*/
const {div, input} = require('../domHelper');
/*ExcludeEnd*/
const styles = require('./courseSearch.css');

module.exports = function () {
    function onRender() {
        document.head.appendChild(styles);
    }

    function onDestroy() {
        document.head.removeChild(styles);
    }

    let courseSearchForm;

    function search(e) {
        const queryData = [];
        for (const /**@type HTMLInputElement*/ node of courseSearchForm) {
            const value = node.value.trim();
            if (value.length > 0)
                queryData.push(node.name + '=' + encodeURIComponent(value));
        }
        fetchApi('/search?' + queryData.join('&')).then(i=>console.log(i))
    }

    return courseSearchForm = div('courseSearch',
        {onRender, onDestroy},
        input(null, 'Course name', {name: 'cosname'}),
        input(null, 'Serial number', {name: 'serial'}),
        input(null, 'Dept ID', {name: 'dept'}),
        input(null, 'Instructor', {name: 'teaname'}),
        input(null, 'Day', {name: 'wk'}),
        input(null, 'Grade', {name: 'degree'}),
        input(null, 'Section', {name: 'section'}),
    );
};