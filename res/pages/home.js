'use strict';

/*ExcludeStart*/
const module = {};
const {div, linkStylesheet, span, p, a, h1} = require('../domHelper');
/*ExcludeEnd*/

module.exports = function () {
    console.log('Home Init');
    const styles = linkStylesheet('./res/pages/home.css');
    const newsPanel = div('newsPanel',
        h1('最新消息', 'title'),
        div('splitLine')
    );
    const bulletinPanel = div('bulletinPanel',
        h1('資訊', 'title'),
        div('splitLine')
    );
    const bulletinTitleMap = {
        enrollmentAnnouncement: '選課公告',
        enrollmentInformation: '選課資訊',
        enrollmentFAQs: '選課FAQs',
        exploringTainan: '踏溯台南路線選擇系統',
        serviceRecommended: '服務學習推薦專區',
        contactInformation: '課程資訊服務聯絡窗口',
    };

    function onRender() {
        console.log('Home Render');
        styles.mount();

        // Get home info
        window.fetchApi('/homeInfo').then(response => {
            if (response == null || !response.success || !response.data)
                return;
            renderHomeInfo(response.data);
        });
    }

    function onPageOpen(isHistory) {
        console.log('Home Open');
        // close navLinks when using mobile devices
        window.navMenu.remove('open');
        styles.enable();
    }

    function onPageClose() {
        console.log('Home Close');
        styles.disable();
    }

    function renderHomeInfo(data) {
        const news = data.news;
        for (const i of news) {
            const content = i.contents.map(info => {
                return info instanceof Object
                    ? a(info.content, info.url, null, null, {target: '_blank'})
                    : span(info)
            });
            newsPanel.appendChild(div('news',
                span(i.department, 'department'),
                p(null, 'content', content),
                span(i.date, 'date'),
            ));
        }

        const bulletin = data.bulletin;
        for (const i in bulletin) {
            bulletinPanel.appendChild(a(bulletinTitleMap[i], bulletin[i], 'bulletin', null, {target: '_blank'}));
        }
    }

    return div('home',
        {onRender, onPageClose, onPageOpen},
        newsPanel,
        bulletinPanel,
    );
};