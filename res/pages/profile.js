import {div, mountableStylesheet} from '../domHelper_v001.min.js';

/**
 * @param {QueryRouter} router
 * @param {Signal} loginState
 * @return {HTMLDivElement}
 */
export default function (router, loginState) {
	console.log('Profile Init');
	// static element
	const styles = mountableStylesheet('./res/pages/profile.css');

	function onRender() {
		console.log('Profile Render');
		styles.mount();
	}

	function onPageOpen() {
		console.log('Profile Open');
		styles.enable();
		onLoginState(loginState.state);
		loginState.addListener(onLoginState);
	}

	function onPageClose() {
		console.log('Profile schedule Close');
		styles.disable();
		loginState.removeListener(onLoginState);
	}

	/**
	 * @param {LoginData} state
	 */
	function onLoginState(state) {
		if (state && state.login) {
		} else {
			if (state)
				window.askForLoginAlert();
		}
	}

	return div('profile',
		{onRender, onPageOpen, onPageClose},
	);
}