import {div, h1, mountableStylesheet, span} from '../domHelper_v001.min.js';

/**
 * @param {QueryRouter} router
 * @param {Signal} loginState
 * @return {HTMLDivElement}
 */
export default function (router, loginState) {
	console.log('UsefulWebsite Init');
	// static element
	const styles = mountableStylesheet('./res/pages/usefulWebsite.css');

	function onRender() {
		console.log('UsefulWebsite Render');
		styles.mount();
	}

	function onPageOpen() {
		console.log('UsefulWebsite Open');
		// close navLinks when using mobile devices
		window.navMenu.remove('open');
		styles.enable();
	}

	function onPageClose() {
		console.log('UsefulWebsite schedule Close');
		styles.disable();
	}

	return div('usefulWebsite',
		{onRender, onPageOpen, onPageClose},
		h1('實用的網站們', 'title'),
		span('還在製作中😥...'),
	);
}