import {button, div, img, p, span} from './lib/domHelper_v003.min.js';

/**
 * @constructor
 */
export function UserGuideTool() {
	const scrollManager = new ScrollManager();
	const guideMaskTop = div('mask maskTop');
	const guideMaskRight = div('mask maskRight');
	const guideMaskBottom = div('mask maskBottom');
	const guideMaskLeft = div('mask maskLeft');
	const guideMaskSquare = div('maskSquare');
	const guideDescription = p();
	const guideNextBtn = button('nextBtn', '下一步');
	const guideCloseBtn = button('closeBtn', '關閉教學', closeGuide);
	const guideMaskTextBlock = div('textBlock', guideDescription, guideNextBtn, guideCloseBtn);
	const guideMask = div('guideMask',
		guideMaskTop,
		guideMaskRight,
		guideMaskBottom,
		guideMaskLeft,
		guideMaskSquare,
		guideMaskTextBlock,
	);
	let /**@type{QueryRouter}*/queryRouter;
	let guiding = false;

	this.element = div('userGuide',
		button('userGuideButton', null, onGuideBtnPress, img('./res/assets/info_icon.svg', 'user guide button icon'), span('使用教學')),
		guideMask,
	);
	this.pageTrigger = {
		CourseSearch: {
			searchStart: hideGuide,
			resultRender: () => multipleGuide([
				{
					element: document.querySelector('body > div > div.router > div > table > thead > tr:nth-child(1) > th'),
					description: '文字篩選: 輸入、序號、課程名、講師等資料進行篩選',
					scrollTo: true,
				},
				{
					element: document.querySelector('body > div > div.router > div > table > thead > tr:nth-child(2) > th'),
					description: '選項篩選: 變更條件將搜尋結果進行篩選',
				},
				{
					element: document.querySelector('body > div > div.router > div > table > thead > tr:nth-child(4)'),
					description: '點選任意標籤進行排序',
				},
				{
					element: document.querySelector('body > div > div.router > div > table > thead > tr:nth-child(4) > .nckuHub'),
					description: '點選 NCKU HUB 排序 (需等待載入完畢後才可進行排序)',
					scrollTo: true,
				}
			]),
			nckuHubSort: () => highlightElement(
				document.querySelector('body > div > div.router > div > table > tbody > tr:nth-child(2) > td.nckuHub'),
				'點擊 NCKU HUB 評分即可查看課程心得 (如果無資料可以先搜尋通識課程看看😊)',
				true,
			),
			nckuHubCommentOpen: hideGuide,
			nckuHubCommentEmpty: closeGuide,
			nckuHubCommentClose: () => highlightElement(
				document.querySelector('body > div > div.router > div > table > tbody > tr > td > div > div > div.instructor > button.instructorBtn'),
				'點教師姓名即可查看評價及評論',
				true,
			),
			urSchoolCommentOpen: hideGuide,
			urSchoolCommentClose: () => window.messageAlert.addSuccess('教學就這樣啦，希望可以幫到大家🥰', null, 2000),
		}
	};

	this.setRouter = function (queryRouter_) {
		queryRouter = queryRouter_;
	}

	function onGuideBtnPress() {
		if (!queryRouter)
			return;
		const pageId = queryRouter.getCurrentPage();
		console.log(pageId);
		guiding = true;

		if (pageId === 'CourseSearch')
			courseSearchGuide();
		else if (pageId === 'Home')
			homeGuide();
	}

	function homeGuide() {
		console.log('Home Guide');
	}

	function courseSearchGuide() {
		const element = document.querySelector('body > div > div.router > div > div.form');
		highlightElement(element, '輸入查詢條件，並按下搜尋', true);
	}

	function multipleGuide(guides) {
		if (!guiding)
			return;

		const steps = [];

		for (let i = guides.length - 1; i > -1; i--) {
			const guide = guides[i];

			// Last item close or hide guide
			if (i === guides.length - 1 && guide.close != null) {
				steps.push(guide.close ? closeGuide : hideGuide);
				continue;
			}

			// Get element
			if (guide.element == null)
				guide.element = document.querySelector(guide.selector);
			const next = steps[steps.length - 1];
			steps.push(() => highlightElement(guide.element, guide.description, guide.scrollTo, next));
		}
		steps[steps.length - 1]();
		console.log(steps);
	}

	function highlightElement(element, description, scrollIntoView, next) {
		if (!guiding)
			return;

		scrollManager.disableScroll();
		guideMask.classList.add('show');

		if (scrollIntoView && element.scrollIntoView)
			element.scrollIntoView({block: 'end', inline: 'nearest'});
		if (!next)
			guideNextBtn.classList.add('hide');
		else
			guideNextBtn.classList.remove('hide');
		guideNextBtn.onclick = next;

		const bound = element.getBoundingClientRect();

		guideMaskTop.style.bottom = (window.innerHeight - bound.top) + 'px';
		guideMaskBottom.style.top = (bound.top + bound.height) + 'px';

		guideMaskRight.style.height = (bound.height) + 'px';
		guideMaskRight.style.left = (bound.left + bound.width) + 'px';
		guideMaskRight.style.top = (bound.top) + 'px';
		guideMaskLeft.style.height = (bound.height) + 'px';
		guideMaskLeft.style.right = (window.innerWidth - bound.left) + 'px';
		guideMaskLeft.style.top = (bound.top) + 'px';

		let leftOffset = 0, topOffset = 0;
		if (bound.left < 2)
			leftOffset = 2;
		if (bound.top < 2)
			topOffset = 2;

		guideMaskSquare.style.width = (bound.width - leftOffset) + 'px';
		guideMaskSquare.style.height = (bound.height - topOffset) + 'px';
		guideMaskSquare.style.left = (bound.left + leftOffset) + 'px';
		guideMaskSquare.style.top = (bound.top + topOffset) + 'px';

		const left = bound.left + 10;
		if (left / window.innerWidth > 0.4) {
			let right = (window.innerWidth - bound.left - bound.width);
			if (window.innerWidth - right < 300)
				right = 0;
			guideMaskTextBlock.style.right = right + 'px';
			guideMaskTextBlock.style.left = null;
		} else {
			guideMaskTextBlock.style.right = null;
			guideMaskTextBlock.style.left = left + 'px';
		}

		const top = bound.top + bound.height + 10;
		if (top / window.innerHeight > 0.6) {
			let bottom = window.innerHeight - bound.top + 10;
			guideMaskTextBlock.style.top = null;
			guideMaskTextBlock.style.bottom = bottom + 'px';
		} else {
			guideMaskTextBlock.style.top = top + 'px';
			guideMaskTextBlock.style.bottom = null;
		}

		guideDescription.textContent = description;
	}

	function hideGuide() {
		scrollManager.enableScroll();
		guideMask.classList.remove('show');
	}

	function closeGuide() {
		scrollManager.enableScroll();
		guideMask.classList.remove('show');
		guiding = false;
	}
}

// From https://stackoverflow.com/a/4770179/12710046
function ScrollManager() {
	const keys = {
		ArrowLeft: 37, ArrowUp: 38, ArrowRight: 39, ArrowDown: 40,
		' ': 32, PageUp: 33, PageDown: 34, End: 35, Home: 36, Tab: 9
	};
	const keysOld = {};
	for (const i in keys)
		keysOld[keys[i]] = true;

	function preventDefault(e) {
		e.preventDefault();
	}

	/**
	 * @param {KeyboardEvent} e
	 * @return {boolean}
	 */
	function preventDefaultForScrollKeys(e) {
		if (e.key) {
			if (keys[e.key]) {
				preventDefault(e);
				return false;
			}
		} else if (keysOld[e.which || e.keyCode]) {
			preventDefault(e);
			return false;
		}
	}

	// modern Chrome requires { passive: false } when adding event
	let supportsPassive = false;
	try {
		window.addEventListener('test', null, Object.defineProperty({}, 'passive', {
			get: function () { supportsPassive = true; }
		}));
	} catch (e) {
	}

	const wheelOpt = supportsPassive ? {passive: false} : false;
	const wheelEvent = 'onwheel' in document.createElement('div') ? 'wheel' : 'mousewheel';

	// call this to Disable
	this.disableScroll = function () {
		window.addEventListener('DOMMouseScroll', preventDefault, false); // older FF
		window.addEventListener(wheelEvent, preventDefault, wheelOpt); // modern desktop
		window.addEventListener('touchmove', preventDefault, wheelOpt); // mobile
		window.addEventListener('keydown', preventDefaultForScrollKeys, false);
	}

	// call this to Enable
	this.enableScroll = function () {
		window.removeEventListener('DOMMouseScroll', preventDefault, false);
		window.removeEventListener(wheelEvent, preventDefault, wheelOpt);
		window.removeEventListener('touchmove', preventDefault, wheelOpt);
		window.removeEventListener('keydown', preventDefaultForScrollKeys, false);
	}
}