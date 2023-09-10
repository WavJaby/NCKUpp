import {a, div, h1, h3, img, mountableStylesheet, p, Signal, State} from '../lib/domHelper_v003.min.js';
import {fetchApi} from '../lib/lib.js';

/**
 * @typedef Website
 * @property {string} description
 * @property {string} iconUrl
 * @property {string} logoUrl
 * @property {string} name
 * @property {string} url
 * @property {string} [logoSlice]
 * @property {string} [iconSlice]
 */

/**
 * @param {QueryRouter} router
 * @param {Signal} loginState
 * @return {HTMLDivElement}
 */
export default function (router, loginState) {
	console.log('UsefulWebsite Init');
	// static element
	const styles = mountableStylesheet('./res/pages/usefulWebsite.css');
	const websites = new Signal();

	function onRender() {
		console.log('UsefulWebsite Render');
		styles.mount();
	}

	function onPageOpen() {
		console.log('UsefulWebsite Open');
		styles.enable();

		updateSites();
	}

	function onPageClose() {
		console.log('UsefulWebsite schedule Close');
		styles.disable();
	}

	function updateSites() {
		fetchApi('/usefulWebsite').then(response => {
			if (!response || !response.success || !response.data)
				return;
			websites.set(response.data);
		});
	}

	/**
	 * @param {Website[]} state
	 * @return {HTMLDivElement}
	 */
	function renderWebsites(state) {
		if (!state)
			return div();
		return div('websites',
			state.map(info => a(null, info.url, 'site noSelect', null, {target: '_blank'},
				parseImage(info.iconUrl, info.iconSlice, 'icon', 30),
				h3(info.name, 'title'),
				info.logoUrl ? parseImage(info.logoUrl, info.logoSlice, 'logo', 200) : null,
				info.description ? p(info.description, 'description') : null,
			))
		);
	}

	/**
	 * @param {string} url
	 * @param {string} slice
	 * @param {string} className
	 * @param {number} maxWidth
	 * @param {number} [maxHeight]
	 * @return any
	 */
	function parseImage(url, slice, className, maxWidth, maxHeight) {
		const image = img(url, '', null);
		const container = div(className, image);
		if (!slice)
			return container;
		maxHeight = maxHeight || maxWidth;

		const arr = slice.split(',');
		const w = parseFloat(arr[0]);
		const h = parseFloat(arr[1]);
		const t = parseFloat(arr[2]);
		const r = parseFloat(arr[3]);
		const b = parseFloat(arr[4]);
		const l = parseFloat(arr[5]);

		const newWidth = w - (l + r);
		const newHeight = h - (t + b);
		const scaleW = maxWidth / newWidth;
		const scaleH = maxHeight / newHeight;
		const scale = Math.min(scaleW, scaleH);

		container.style.width = toPx(newWidth * scale);
		container.style.height = toPx(newHeight * scale);

		image.style.width = toPx(w * scale);
		image.style.height = toPx(h * scale);

		image.style.margin = toPx(-t * scale) + ' 0 0 ' + toPx(-l * scale);
		return container;
	}

	function toPx(num) {
		return num === 0 ? '0' : num + 'px';
	}

	return div('usefulWebsite',
		{onRender, onPageOpen, onPageClose},
		h1('實用的網站們', 'title'),
		State(websites, renderWebsites),
	);
}