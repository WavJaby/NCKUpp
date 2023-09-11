'use strict';

import {a, br, div, h1, h2, img, mountableStylesheet, p, span, text} from '../lib/domHelper_v003.min.js';
import {fetchApi, isMobile} from '../lib/lib.js';

/**
 * @param {QueryRouter} router
 * @return {HTMLDivElement}
 */
export default function (router) {
	console.log('Home Init');
	const titleAnimation = span(null, 'slideOut', span('++'));
	const styles = mountableStylesheet('./res/pages/home.css');
	const introduction = div('introduction',
		div('block',
			img('./res/assets/page_home/sort_function.png'),
			h2('排序功能', 'title'),
			p('可對搜尋結果的任意欄位進行排序')
		),
		div('block',
			img('./res/assets/page_home/ncku_hub_comment_function.png'),
			h2('NCKU HUB評論', 'title'),
			p('點擊課程評分即可查看評論')
		),
		div('block',
			img('./res/assets/page_home/urschool_instructor_info_function.png'),
			h2('UrSchool教授評價', 'title'),
			p('點擊教師姓名查看講師評價、詳細資料及評論')
		),
		div('block',
			img('./res/assets/page_home/category_filter_function.png'),
			h2('搜尋結果篩選', 'title'),
			p('可以自由選擇篩選條件，提供衝堂、精確節次、班別等篩選器')
		),
		div('block',
			img('./res/assets/page_home/schedule_download_function.png'),
			h2('課表下載', 'title'),
			p('提供預排課表檢視，課表下載功能，下載漂亮的課表🥰')
		),
		div('block',
			img('./res/assets/page_home/add_course_function.png'),
			h2('支援預排、選課', 'title'),
			p('登入後可跟選課網站連動，進行預排、志願登記、單科加選等操作'),
			p('搶課一律以成大系統為主，若使用本網站搶課未成功一概不負責', 'small')
		)
	);
	const siteInfo = div('siteInfo',
		h1(null, 'title',
			img('res/assets/icon/icon_64.svg', ''), span('NCKU'), titleAnimation
		),
		p(null, 'description',
			text('集合'),
			img('res/assets/NCKU_course_system_logo.png', '國立成功大學課程資訊及選課系統'),
			img('res/assets/nckuHub_logo.svg', 'NCKUHub'),
			img('res/assets/UrSchool_logo.png', 'UrSchool'),
			br(),
			text('眾多功能，提供更好的選課環境')
		),
		introduction,
		a(null, './?page=CourseSearch', 'toCourseSearchLink', toCourseSearchBtnClick, span('前往課程查詢')),
	);
	const scrollDownIndicator = div('scrollDownIndicator', {onclick: scrollDown},
		img('./res/assets/down_arrow_icon.svg', '', 'scrollDownArrow'),
		h1('最新消息', 'title')
	);
	const newsPanel = div('newsPanel',
		div('splitLine'),
		div('items')
	);
	const bulletinPanel = div('bulletinPanel',
		h1('資訊', 'title'),
		div('splitLine'),
		div('items')
	);
	let pageOpened = false;
	let scrollDownIndicatorState = false;
	let lastScrollTime = 0;

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

	let introductionHover = false;
	let introductionScrollTarget = 0;
	introduction.onmouseenter = function () {introductionHover = true;};
	introduction.onmouseleave = function () {introductionHover = false;};

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
		styles.enable();
		setTimeout(() => titleAnimation.style.width = titleAnimation.firstElementChild.offsetWidth + 'px', 700);
		document.head.appendChild(linkCanonical);

		router.element.addEventListener('scroll', onscroll);
		router.element.addEventListener('wheel', onwheel);
		pageOpened = true;
		lastScrollTime = Date.now();
		introductionAnimation();
	}

	function onPageClose() {
		console.log('Home Close');
		styles.disable();
		titleAnimation.style.width = null;
		document.head.removeChild(linkCanonical);

		router.element.removeEventListener('scroll', onscroll);
		router.element.removeEventListener('wheel', onwheel);
		pageOpened = false;
	}

	let introductionAnimationRollingTemp = 0, introductionAnimationLastScrollPos = 0;
	let introductionAnimationDirection = false, introductionAnimationPause = true;

	function introductionAnimation() {
		const now = Date.now();
		const time = now - lastScrollTime;

		// Interrupt by user
		if (introduction.scrollLeft !== introductionAnimationLastScrollPos) {
			introductionAnimationLastScrollPos = introduction.scrollLeft;
			introductionAnimationPause = true;
			lastScrollTime = now;
			requestAnimationFrame(introductionAnimation);
			return;
		}
		// Pausing
		if (introductionAnimationPause) {
			if (time > 2000) {
				introductionAnimationPause = false;
				lastScrollTime = now;
			}
			requestAnimationFrame(introductionAnimation);
			return;
		}

		// Update scroll
		lastScrollTime = now;
		introductionAnimationRollingTemp += time / 1000 * 40;
		if (introductionAnimationRollingTemp > 1) {
			if (introductionAnimationDirection)
				introduction.scrollLeft -= 1;
			else
				introduction.scrollLeft += 1;
			introductionAnimationLastScrollPos = introduction.scrollLeft;
			introductionAnimationRollingTemp %= 1;
		}

		if (introduction.scrollLeft === 0)
			introductionAnimationDirection = false;
		else if (introduction.scrollWidth - introduction.clientWidth - introduction.scrollLeft < 1) {
			introductionAnimationDirection = true;
			introductionAnimationPause = true;
			// if (introduction.scrollTo) {
			// 	introduction.scrollTo({left: 0, behavior: 'smooth'});
			// } else
			// 	introduction.scrollLeft = 0;
		}

		if (pageOpened)
			requestAnimationFrame(introductionAnimation);
	}

	function scrollDown() {
		introductionAnimationPause = true;
		if (isMobile()) {
			if (this.lastElementChild.scrollIntoView)
				this.lastElementChild.scrollIntoView({behavior: 'smooth'});
		} else if (this.scrollIntoView)
			this.scrollIntoView({behavior: 'smooth'});
	}

	function onwheel(e) {
		if (introductionHover) {
			e.preventDefault();
			if (!introductionAnimationPause)
				introductionScrollTarget = introduction.scrollLeft;
			introductionAnimationPause = true;
			if (introduction.scrollTo) {
				introductionScrollTarget += e.deltaY;
				if (introductionScrollTarget < 0) {
					introductionScrollTarget = 0;
				}
				if (introduction.scrollWidth - introduction.clientWidth - introductionScrollTarget < 0) {
					introductionScrollTarget = introduction.scrollWidth - introduction.clientWidth;
				}
				if (introduction.scrollLeft !== introductionScrollTarget)
					introduction.scrollTo({left: introductionScrollTarget, behavior: 'smooth'});
			} else {
				introduction.scrollLeft += e.deltaY;
			}
		}
	}

	function onscroll() {
		const percent = router.element.scrollTop / siteInfo.offsetHeight;
		if (percent <= 1) {
			siteInfo.style.opacity = (1 - percent * 1.2).toString();
		}

		if (percent > 0.5) {
			if (!scrollDownIndicatorState)
				scrollDownIndicator.classList.add('toLeft');
			scrollDownIndicatorState = true;
		} else {
			if (scrollDownIndicatorState)
				scrollDownIndicator.classList.remove('toLeft');
			scrollDownIndicatorState = false;
		}
	}

	function toCourseSearchBtnClick(e) {
		e.preventDefault();
		router.openPage('CourseSearch');
		window.navMenuClose();
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
		scrollDownIndicator,
		div('panels',
			newsPanel,
			bulletinPanel,
		)
	);
};