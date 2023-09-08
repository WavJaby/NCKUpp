'use strict';

import {
	a,
	button,
	div,
	doomHelperDebug,
	footer,
	h1,
	h2,
	h3,
	img,
	input,
	li,
	mountableStylesheet,
	nav,
	QueryRouter,
	RouterLazyLoad,
	ShowIf,
	Signal,
	span,
	svg,
	text,
	TextState,
	ul
} from './res/lib/domHelper_v002.min.js';

import {metaSet, metaType} from './res/lib/metaTag.js';
import {fetchApi, isLocalNetwork} from './res/lib/lib.js';
import {UserGuideTool} from './res/userGuide.js';

window.askForLoginAlert = () => window.messageAlert.addInfo('需要登入來使用此頁面', '右上角登入按鈕登入', 3000);
window.loadingElement = svg('<circle class="path" cx="25" cy="25" r="20" fill="none" stroke-width="5" stroke-linecap="square"/>', '0 0 50 50', 'loaderCircle');
window.pageLoading = new Signal(false);

/**
 * @typedef {Object} LoginData
 * @property {boolean} login
 * @property {string} name
 * @property {string} studentID
 * @property {int} year
 * @property {int} semester
 */
// Main function
(function main() {
	console.log('index.js Init');
	window.messageAlert = new MessageAlert();
	window.requestState = new RequestStateObject();
	const font = mountableStylesheet('https://fonts.googleapis.com/css2?family=JetBrains+Mono&display=swap');
	if (document.requestStorageAccess)
		document.requestStorageAccess().then(
			() => console.log("StorageAccess granted"),
			() => console.log("StorageAccess denied")
		);
	const userGuide = new UserGuideTool();

	// debug
	const debugModeEnable = isLocalNetwork || window.localStorage && !!localStorage.getItem('debug');
	let debugWindow = null;
	if (debugModeEnable) {
		console.log('Debug enabled');
		doomHelperDebug();

		// Memory usage (for chrome)
		if (window.performance && window.performance.memory) {
			const memoryUpdate = new Signal(window.performance.memory);
			setInterval(() => memoryUpdate.set(window.performance.memory), 1000);
			debugWindow = span(
				TextState(memoryUpdate, state => (state.usedJSHeapSize / 1000 / 1000).toFixed(2) + 'MB'),
				null,
				{style: 'position: absolute; top: 0; z-index: 100; background: black; font-size: 10px; opacity: 0.5;'});
		}
	} else
		console.log('Debug disable');

	// main code
	const pageIdName = {
		// search: 'Search',
		// schedule: 'Schedule',
		// grades: 'Grades',
		Home: '成功大學課程資訊及選課系統',
		CourseSearch: '課程查詢',
		Schedule: '課表',
		GradeInquiry: '成績查詢',
		CourseSelectionPreference: '志願排序',
		UsefulWebsite: '實用網站',
		Profile: '使用者資料',
	};
	const navBarBtnPageId = ['CourseSearch', 'Schedule', 'GradeInquiry', 'CourseSelectionPreference', 'UsefulWebsite'];
	const defaultPage = 'Home';
	const userLoginData = new Signal();
	const showLoginWindow = new Signal(false);
	const queryRouter = new QueryRouter('NCKU++', pageIdName, defaultPage,
		{
			Home: new RouterLazyLoad('./res/pages/home_v002.min.js'),
			CourseSearch: new RouterLazyLoad('./res/pages/courseSearch.js', userLoginData),
			Schedule: new RouterLazyLoad('./res/pages/courseSchedule.js', userLoginData),
			GradeInquiry: new RouterLazyLoad('./res/pages/stuIdSysGrades.js', userLoginData),
			CourseSelectionPreference: new RouterLazyLoad('./res/pages/preferenceAdjust.js', userLoginData),
			UsefulWebsite: new RouterLazyLoad('./res/pages/usefulWebsite.js', userLoginData),
			Profile: new RouterLazyLoad('./res/pages/profile.js', userLoginData),
		},
		footer(
			div('borderLine'),
			div('source',
				h2('資料來源'),
				a(null, 'https://course.ncku.edu.tw/', 'noSelect', null, {target: '_blank'},
					img('res/assets/courseNcku_logo.png', '國立成功大學課程資訊及選課系統', 'noDrag')
				),
				a(null, 'https://nckuhub.com/', 'noSelect', null, {target: '_blank'},
					img('res/assets/nckuHub_logo.svg', 'NCKUHub', 'noDrag')
				),
				a(null, 'https://urschool.org/', 'noSelect', null, {target: '_blank'},
					img('res/assets/UrSchool_logo.png', 'UrSchool', 'noDrag')
				),
				div('splitLine'),
			),
			h3('By WavJaby'),
			h3('Email: WavJaby@gmail.com'),
			a(null, 'https://github.com/WavJaby/NCKUpp', 'openRepo noSelect', null, {target: '_blank'},
				img('./res/assets/github_icon.svg', 'GitHub icon', 'githubIcon noDrag'),
				span('GitHub repo'),
			),
		)
	);
	enableGoogleAnalytics(debugModeEnable);

	const pageButtons = {};
	for (const pageId of navBarBtnPageId) {
		pageButtons[pageId] = li(null, a(pageIdName[pageId], './?page=' + pageId, null, pageButtonClick, {pageId: pageId}));
	}
	queryRouter.onPageOpen = function (lastPageId, pageId) {
		const lastPageButton = pageButtons[lastPageId];
		if (lastPageId && lastPageButton)
			lastPageButton.classList.remove('opened');
		const nextPageButton = pageButtons[pageId];
		if (nextPageButton)
			nextPageButton.classList.add('opened');
		metaSet(metaType.TITLE, document.title);
	}
	const navbarLinks = ul('links',
		Object.values(pageButtons),
		li(null, NavSelectList('arrow', text('0w0'), [
			['嗨嗨', null],
			['🥰', null],
		])),
	);
	const navBarMobileBackground = ul('navBarMobileBG', {onclick: navMenuClose});
	window.navMenuClose = navMenuClose;

	function navMenuClose() {
		navbarLinks.classList.remove('open');
		navBarMobileBackground.classList.remove('open');
	}

	function navMenuToggle() {
		navbarLinks.classList.toggle('open');
		navBarMobileBackground.classList.toggle('open');
	}

	// check login
	fetchApi('/login?mode=course', 'Check login').then(onLoginStateChange);

	const root = div('root',
		// Navbar
		nav('navbar noSelect',
			NavSelectList('loginBtn',
				span(TextState(userLoginData, /**@param{LoginData}state*/state =>
					state && state.login ? state.studentID : 'Login'
				)),
				[
					['Profile', () => queryRouter.openPage('Profile')],
					['Logout', () => fetchApi('/logout').then(onLoginStateChange)],
				],
				false,
				() => {
					navMenuClose();
					// Is login
					if (userLoginData.state && userLoginData.state.login)
						return true; // Open select list
					// Not login, open login window
					showLoginWindow.set(!showLoginWindow.state);
					return false; // Not open select list
				}
			),
			ul('hamburgerMenu', li(null,
				img('./res/assets/burger_menu_icon.svg', 'mobile menu button', 'noDrag noSelect', {onclick: navMenuToggle}),
			)),
			ul('homePage', li(null, a('NCKU++', './?page=Home', null, pageButtonClick, {pageId: 'Home'}))),
			navBarMobileBackground,
			navbarLinks,
		),
		// Pages
		queryRouter.element,
		userGuide.element,
		ShowIf(showLoginWindow, LoginWindow(onLoginStateChange)),
		ShowIf(window.pageLoading, div('loading', window.loadingElement.cloneNode(true))),
		window.messageAlert.element,
		window.requestState.element,
		debugWindow,
	);

	window.onload = () => {
		console.log('Window onload');
		font.mount();
		queryRouter.initFirstPage();
		while (document.body.firstChild) document.body.removeChild(document.body.firstChild);
		document.body.appendChild(root);
	};

	// functions
	function pageButtonClick(e) {
		e.preventDefault();
		const pageId = this.pageId;
		queryRouter.openPage(pageId);
		navMenuClose();
		return false;
	}

	function onLoginStateChange(response) {
		if (!response) {
			window.messageAlert.addError('Network error', 'Try again later', 3000);
			return;
		}
		/**@type LoginData*/
		const loginData = response.data;
		if (!loginData) {
			window.messageAlert.addError('Unknown login error', 'Try again later', 3000);
			return;
		}
		// Check if login error
		if (!loginData.login && response.msg) {
			window.messageAlert.addError('Login failed', response.msg, 5000);
			return;
		}

		// Success, set login data
		userLoginData.set(loginData);
		showLoginWindow.set(false);
	}

	/**
	 * @param {string} classname
	 * @param {Text|Element|Element[]} title
	 * @param {[string, function()][]} items
	 * @param {boolean} [enableHover]
	 * @param {function(): boolean} [onOpen]
	 * @return {HTMLUListElement}
	 */
	function NavSelectList(classname, title, items, enableHover, onOpen) {
		const itemsElement = ul(null);
		const list = ul(
			classname ? 'list ' + classname : 'list',
			enableHover ? {onmouseleave: closeSelectList, onmouseenter: openSelectList} : null,
			// Element
			li('items', itemsElement),
			li('title', title, {onclick: toggleSelectList}),
		);

		for (const item of items) {
			const onItemClick = item[1];
			itemsElement.appendChild(
				li(null, text(item[0]), {
					onItemClick: onItemClick,
					onclick: closeSelectList
				})
			);
		}

		function openSelectList() {
			list.style.height = (list.firstElementChild.offsetHeight + list.lastElementChild.offsetHeight) + 'px';
			window.addEventListener('pointerup', checkOutFocus);
		}

		function closeSelectList() {
			if (this && this.onItemClick)
				this.onItemClick();
			list.style.height = null;
			window.removeEventListener('pointerup', checkOutFocus);
		}

		function toggleSelectList() {
			if (onOpen && !onOpen() && !list.style.height) return;

			if (list.style.height)
				closeSelectList();
			else
				openSelectList();
		}

		function checkOutFocus(e) {
			let parent = e.target;
			do {
				if (parent === list)
					return;
			} while ((parent = parent.parentElement) !== document.body);
			closeSelectList();
		}

		return list;
	}

	/**
	 * @constructor
	 */
	function MessageAlert() {
		const messageBoxRoot = div('messageBox');
		this.element = messageBoxRoot;

		this.addInfo = function (title, description, removeTimeout) {
			createMessageBox('info', title, description, null, removeTimeout);
		}

		this.addError = function (title, description, removeTimeout) {
			createMessageBox('error', title, description, null, removeTimeout);
		}

		this.addErrorElement = function (title, htmlElement, removeTimeout) {
			createMessageBox('error', title, null, htmlElement, removeTimeout);
		}

		this.addSuccess = function (title, description, removeTimeout) {
			createMessageBox('success', title, description, null, removeTimeout);
		}

		function onMessageBoxTap() {
			removeMessageBox(this);
		}

		function createMessageBox(classname, title, description, htmlElement, removeTimeout) {
			const messageBox = div(classname, {onclick: onMessageBoxTap},
				span(title, 'title'),
				span(description, 'description', htmlElement),
				div('closeButton')
			);
			if (removeTimeout !== undefined)
				messageBox.removeTimeout = setTimeout(() => removeMessageBox(messageBox), removeTimeout);
			if (messageBoxRoot.childElementCount > 0)
				messageBoxRoot.insertBefore(messageBox, messageBoxRoot.firstElementChild);
			else
				messageBoxRoot.appendChild(messageBox);
			messageBox.style.marginTop = -messageBox.offsetHeight + 'px';

			// Slide in
			setTimeout(() => {
				messageBox.classList.add('animation');
				messageBox.style.marginTop = '0';
			}, 10);
		}

		function removeMessageBox(messageBox) {
			clearTimeout(messageBox.removeTimeout);
			messageBox.style.opacity = '0';
			messageBox.style.marginTop = (-messageBox.offsetHeight - 10) + 'px';
			setTimeout(() => {
				messageBoxRoot.removeChild(messageBox);
			}, 500);
		}
	}

	function RequestStateObject() {
		const stateBox = this.element = div('stateBox noSelect');

		this.addState = function (title) {
			return stateBox.appendChild(div(null,
				window.loadingElement.cloneNode(true),
				h1(title, 'title')
			));
		};

		this.removeState = function (stateElement) {
			stateBox.removeChild(stateElement);
		};
	}

	function LoginWindow(onLoginStateChange) {
		const username = input('loginField', 'Account', null, {onkeyup, type: 'email', autocomplete: 'username'});
		const password = input('loginField', 'Password', null, {onkeyup, type: 'password', autocomplete: 'current-password'});
		let loading = false;

		function onkeyup(e) {
			if (e.key === 'Enter') login();
		}

		function login() {
			if (!loading) {
				loading = true;
				window.pageLoading.set(true);
				const usr = username.value.endsWith('@ncku.edu.tw') ? username : username.value + '@ncku.edu.tw';
				fetchApi('/login?mode=course', 'login', {
					method: 'POST',
					body: `username=${encodeURIComponent(usr)}&password=${encodeURIComponent(password.value)}`,
					timeout: 10000,
				}).then(i => {
					loading = false;
					window.pageLoading.set(false);
					onLoginStateChange(i);
				});
			}
		}

		// element
		return div('loginWindow', {onRender: () => username.focus()},
			span('帳密與成功入口相同', 'description'),
			username,
			password,
			button('loginField', 'Login', login, {type: 'submit'}),
		);
	}
})();