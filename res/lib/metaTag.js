'use strict';

const metaTags = {};

const metaType = {
	AUTHOR: 'author',
	TWITTER_AUTHOR_ACCOUNT: 'twitter:site',
	LOCALE: 'locale',
	TYPE: 'type',
	URL: 'url',
	SITE_NAME: 'site_name',
	TITLE: [
		['itemprop', 'name'],
		['property', 'og:title'],
		['name', 'twitter:title']
	],
	DESCRIPTION: [
		['name', 'description'],
		['itemprop', 'description'],
		['property', 'og:description'],
		['name', 'twitter:description']
	],
	IMAGE: [
		['itemprop', 'image'],
		['property', 'og:image'],
		['name', 'twitter:image']
	],
	THEME_COLOR: 'theme-color',
	TWITTER_CARD: 'twitter:card',
};

const metaTypeMap = {
	'author': metaType.AUTHOR,
	'twitter:site': metaType.TWITTER_AUTHOR_ACCOUNT,
	'og:locale': metaType.LOCALE,
	'og:type': metaType.TYPE,
	'og:url': metaType.URL,
	'og:site_name': metaType.SITE_NAME,

	'name': metaType.TITLE,
	'og:title': metaType.TITLE,
	'twitter:title': metaType.TITLE,

	'description': metaType.DESCRIPTION,
	'og:description': metaType.DESCRIPTION,
	'twitter:description': metaType.DESCRIPTION,

	'image': metaType.IMAGE,
	'og:image': metaType.IMAGE,
	'twitter:image': metaType.IMAGE,

	'theme-color': metaType.THEME_COLOR,
	'twitter:card': metaType.TWITTER_CARD,
};

for (const metaTag of document.head.getElementsByTagName('meta')) {
	const name = metaTag.getAttribute('name');
	const property = metaTag.getAttribute('property');
	const itemprop = metaTag.getAttribute('itemprop');
	if (!name && !property && !itemprop)
		continue;

	const typeKey = name && 'name' || property && 'property' || itemprop && 'itemprop';
	const typeValue = name || property || itemprop;
	if (metaTypeMap[typeValue]) {
		metaTags[typeKey + ' ' + typeValue] = metaTag;
	}
}

/**
 * @param {metaType} type
 * @param {string} content
 */
function metaSet(type, content) {
	if (type instanceof Array) {
		for (const i of type) {
			const metaTag = metaTags[i[0] + ' ' + i[1]];
			if (metaTag) {
				metaTag.setAttribute('content', content);
			}
			// Create if not exist
			else {
				const metaTag = document.createElement('meta');
				metaTag.setAttribute(i[0], i[1]);
				metaTag.setAttribute('content', content);
				document.head.appendChild(metaTag);
			}
		}
	} else {

	}
}

export {
	metaType,
	metaSet,
};