const pagesData = JSON.parse(document.getElementById('__NEXT_DATA__').textContent).props.pageProps.textbook.tableOfContents;
const myRootUrl = '/api/quizlet';
const loadedResources = window.loadedResources || (window.loadedResources = {});
// const loadedResources = {};

// await getPage({_webUrl: window.location.href, page: 'home'});

let chapter = 3;
const sections = pagesData[chapter].children;
let sectionCount = 1;
for (const section of sections) {
	const exercises = section.children
		? section.children.reduce((i, page) => (i.push(...page.exercises), i), [])
		: section.exercises;
	let j = 0;
	for (const exercise of exercises) {
		if (!exercise.hasSolution)
			continue;
		await getPage(exercise);
		console.log(`page:${exercise.page}, exercise:${exercise.exerciseName} ${exercise.mediaExerciseId} ${j}/${exercises.length} ${sectionCount}/${sections.length}`);
		++j;
	}
	++sectionCount;
	// console.log(exercises);
}

function getPage(exercise) {
	const url = exercise._webUrl;
	// Get page url
	const rootUrl = 'https://quizlet.com/explanations/textbook-solutions';
	exercise._webUrl = url.replaceAll(rootUrl, myRootUrl + '/solutions');

	let filePath = url.substring(rootUrl.length);
	if (filePath.lastIndexOf('.') < filePath.lastIndexOf('/'))
		filePath += '.html';

	// console.log(filePath);

	async function fetchPage(resolve) {
		const response = await fetch(url);
		// Retry
		if (response.status !== 200) {
			console.log(`page:${exercise.page}, exercise:${exercise.exerciseName} ${exercise.mediaExerciseId} Retry`);
			setTimeout(() => fetchPage(resolve), 4000);
			return;
		}
		const data = await response.text();
		const page = parseFile(data)
			.replace(/"\/_next/g, '"' + myRootUrl + '/base/_next');
		await extractFileResources(page);

		// Save page html
		const output = {
			filePath: filePath,
			data: page,
		};
		await fetch('https://localhost/api/quizlet/save', {method: 'POST', body: JSON.stringify(output), mode: 'no-cors'});
		resolve();
	}

	return new Promise(fetchPage);

	// const parser = new DOMParser();
	// const doc = parser.parseFromString(page, 'text/html');
}

async function extractFileResources(file) {
	const assetsRegex = /\/api\/quizlet\/assets\/([^")]+)/g;
	const baseAssetsRegex = /\/api\/quizlet\/base\/([^")]+)/g;
	const assetsUrlMatch = [];
	for (let match of execAll(assetsRegex, file))
		assetsUrlMatch.push('https://assets.quizlet.com/' + match[1]);
	for (let match of execAll(baseAssetsRegex, file))
		assetsUrlMatch.push('https://quizlet.com/' + match[1]);
	await saveResources(assetsUrlMatch);
}

async function saveResources(urls) {
	let i = 0;
	for (const url of urls) {
		if (loadedResources[url]) {
			++i;
			continue;
		}

		try {
			const response = await fetch(url);
			const headers = [];
			for (const entry of response.headers.entries()) {
				if (entry[0] !== 'content-encoding') headers.push(entry);
			}
			let data = await response.blob();

			const contentType = response.headers.get("Content-type");
			if (contentType === 'text/css') {
				data = (await data.text()).replaceAll(/url\(\/_next\//g, 'url(' + myRootUrl + '/assets/_next/');
				await extractFileResources(data);
				data = btoa(toUnicode(data));
			} else if (contentType === 'application/javascript' || contentType === 'text/javascript') {
				data = (await data.text());
				data = parseFile(data);
				if (url.indexOf('webpack-') !== -1)
					data = await extractWebPack(data);
				data = btoa(toUnicode(data));
			} else {
				data = await new Promise((resolve, reject) => {
					const reader = new FileReader();
					reader.onload = function () {
						resolve(reader.result);
					};
					reader.onerror = reject;
					reader.readAsDataURL(data);
				});
				data = data.substring(data.indexOf(',') + 1);
			}

			const output = {
				resourcesUrl: url,
				headers: headers,
				data: data,
			};

			await fetch('https://localhost/api/quizlet/save', {method: 'POST', body: JSON.stringify(output), mode: 'no-cors'});
			console.log(url, (++i) + '/' + urls.length);
			loadedResources[url] = true;
		} catch (e) {
			console.error(e);
		}
	}
}

async function extractWebPack(file) {
	const fileFuncRegex = /\w+\.\w+=(function\(\w+\)\{return \d+===.+"\.js"})/g;
	const [fileCreator, numbersRaw] = await parseFileCreator(file, fileFuncRegex);
	let fileUrlsFilter = [];
	let fileUrls = [];
	for (const i of numbersRaw) {
		if (fileUrlsFilter.indexOf(i[1]) === -1) {
			fileUrlsFilter.push(i[1]);
			fileUrls.push('https://assets.quizlet.com/_next/' + fileCreator(parseInt(i[1])));
		}
	}
	await saveResources(fileUrls);

	const cssFileFuncRegex = /\w+\.\w+=(function\(\w+\){return ?"[\w/]+"\+\{.+}\[\w+]\+"\.css"})/g;
	const [cssFileCreator, cssNumbersRaw] = await parseFileCreator(file, cssFileFuncRegex);
	fileUrlsFilter = [];
	fileUrls = [];
	for (const i of cssNumbersRaw) {
		if (fileUrlsFilter.indexOf(i[1]) === -1) {
			fileUrlsFilter.push(i[1]);
			fileUrls.push('https://assets.quizlet.com/_next/' + cssFileCreator(parseInt(i[1])));
		}
	}
	await saveResources(fileUrls);

	return file;
}

async function parseFileCreator(file, regex) {
	const fileCreatorText = regex.exec(file)[1];

	const fileCreator = await new Promise(resolve => {
		let script = document.createElement('script');
		script.src = 'data:application/javascript;base64,' + btoa('window.fileCreator=' + fileCreatorText);
		script.onload = () => resolve(window.fileCreator);
		document.head.appendChild(script);
	});

	const numberRegex = /[ {:,](\d+)[=:]/g;
	return [fileCreator, execAll(numberRegex, fileCreatorText)];
}

function parseFile(file) {
	return file
		.replace(/https?:\/\/quizlet.com\/explanations\/textbook-solutions/g, '/NCKUpp/quizlet')
		.replace(/="\/_next/g, '="' + myRootUrl + '/base/_next')
		.replace(/\/manifest.webmanifest/g, myRootUrl + '/base/manifest.webmanifest')
		.replace(/https?:\/\/quizlet\.com/g, myRootUrl + '/base')
		.replace(/https?:\/\/assets\.quizlet\.com/g, myRootUrl + '/assets')
		.replace(/https?:\/\/www\.googletagmanager\.com\/gtm\.js\?id=[\w-]+/g, '')
		;
}

function execAll(regex, text) {
	const result = [];
	let match;
	while ((match = regex.exec(text)) !== null)
		result.push(match);
	return result;
}

function toUnicode(text) {
	return text.replace(/[^\x00-\xFF]/g, function (ch) {
		return '\\u' + ch.charCodeAt(0).toString(16).padStart(4, '0');
	});
}