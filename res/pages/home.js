'use strict';

import {a, br, div, h1, img, mountableStylesheet, p, span, text} from '../domHelper_v001.min.js';
import {fetchApi} from '../lib.js';

/**
 * @param {QueryRouter} router
 * @return {HTMLDivElement}
 */
export default function (router) {
	console.log('Home Init');
	const titleAnimation = span(null, 'slideOut', span('++'));
	const styles = mountableStylesheet('./res/pages/home.css');
	const newsPanel = div('newsPanel',
		h1('最新消息', 'title'),
		div('splitLine'),
		div('items')
	);
	const bulletinPanel = div('bulletinPanel',
		h1('資訊', 'title'),
		div('splitLine'),
		div('items')
	);
	const siteInfo = div('siteInfo',
		h1(null, 'title',
			img('res/assets/icon.svg', ''), span('NCKU'), titleAnimation
		),
		p(null, 'description',
			text('集合'),
			img('res/assets/courseNcku_logo.png', '國立成功大學課程資訊及選課系統'),
			img('res/assets/nckuHub_logo.svg', 'NCKUHub'),
			img('res/assets/UrSchool_logo.png', 'UrSchool'),
			br(),
			text('眾多功能，提供更好的選課環境')
		)
	);
	const bulletinTitleMap = {
		enrollmentAnnouncement: '選課公告',
		enrollmentInformation: '選課資訊',
		enrollmentFAQs: '選課FAQs',
		exploringTainan: '踏溯台南路線選擇系統',
		serviceRecommended: '服務學習推薦專區',
		contactInformation: '課程資訊服務聯絡窗口',
	};
	const linkCanonical = document.createElement('link');
	linkCanonical.rel = 'canonical';
	linkCanonical.href = 'https://wavjaby.github.io/NCKUpp/';

	function onRender() {
		console.log('Home Render');
		styles.mount();

		// Get home info
		fetchApi('/homeInfo').then(response => {
			if (response == null || !response.success || !response.data)
				return;
			renderHomeInfo(response.data);
		});
	}

	function onPageOpen() {
		console.log('Home Open');
		// close navLinks when using mobile devices
		window.navMenuClose();
		styles.enable();
		setTimeout(() =>
				titleAnimation.style.width = titleAnimation.firstElementChild.offsetWidth + 'px'
			, 700);
		document.head.appendChild(linkCanonical);

		router.element.addEventListener('scroll', onscroll);
	}

	function onPageClose() {
		console.log('Home Close');
		styles.disable();
		titleAnimation.style.width = null;
		document.head.removeChild(linkCanonical);

		router.element.removeEventListener('scroll', onscroll);
	}

	function onscroll() {
		const percent = 1 - router.element.scrollTop / siteInfo.offsetHeight;
		siteInfo.style.opacity = percent.toString();
	}

	function renderHomeInfo(data) {
		const newsItems = newsPanel.lastElementChild;
		const news = data.news;
		for (const i of news) {
			const content = i.contents.map(info => {
				return info instanceof Object
					? a(info.content, info.url, null, null, {target: '_blank'})
					: span(info)
			});
			newsItems.appendChild(div('news',
				span(i.department, 'department'),
				p(null, 'content', content),
				span(i.date, 'date'),
			));
		}

		const bulletinItems = bulletinPanel.lastElementChild;
		const bulletin = data.bulletin;
		bulletinItems.appendChild(a('原選課系統', 'https://course.ncku.edu.tw/', 'bulletin', null, {target: '_blank'}));
		for (const i in bulletin) {
			bulletinItems.appendChild(a(bulletinTitleMap[i], bulletin[i], 'bulletin', null, {target: '_blank'}));
		}
	}

	return div('home',
		{onRender, onPageClose, onPageOpen},
		siteInfo,
		div('panels',
			newsPanel,
			bulletinPanel,
		)
	);
};