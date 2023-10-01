const pagesData = JSON.parse(document.getElementById('__NEXT_DATA__').textContent).props.pageProps.textbook.tableOfContents;
const myRootUrl = '/api/quizlet';
const loadedResources = window.loadedResources || (window.loadedResources = {});

// await getPage({_webUrl: window.location.href, page: 'home'});

let chapter = 0;
const sections = pagesData[chapter].children;
for (const section of sections) {
	const exercises = section.children
		? section.children.reduce((i, page) => (i.push(...page.exercises), i), [])
		: section.exercises;
	for (const exercise of exercises) {
		await getPage(exercise);
		console.log(`page:${exercise.page}, exercise:${exercise.exerciseName} ${exercise.mediaExerciseId}`);
	}
	console.log(exercises);
	break;
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
			setTimeout(() => fetchPage(resolve), 3000);
			return;
		}
		const data = await response.text();
		const page = parseFile(data);
		await extractResources(page);

		// Save page html
		const output = {
			filePath: filePath,
			data: page,
		};
		await fetch('https://localhost:8080/api/quizlet/save', {method: 'POST', body: JSON.stringify(output), mode: 'no-cors'});
		resolve();
	}

	return new Promise(fetchPage);

	// const parser = new DOMParser();
	// const doc = parser.parseFromString(page, 'text/html');
}

async function extractResources(file) {
	const assetsRegex = /\/api\/quizlet\/assets\/([^")]+)/g;
	const assetsUrlMatch = execAll(assetsRegex, file);
	for (const match of assetsUrlMatch) {
		const url = 'https://assets.quizlet.com/' + match[1];
		console.log('Getting ' + url);
		if (loadedResources[url])
			return;

		try {
			const response = await fetch(url).then(i => ({data: i.blob(), headers: i.headers, code: i.status}));
			const headers = [...response.headers];
			let data = await response.data;

			const contentType = response.headers.get("Content-type");
			if (contentType === 'text/css') {
				data = (await data.text()).replaceAll(/url\(\/_next\//g, 'url(' + myRootUrl + '/assets/_next/');
				await extractResources(data);
				data = btoa(toUnicode(data));
			} else if (contentType === 'application/javascript') {
				data = (await data.text());
				data = parseFile(data);
				if (file.indexOf('webpack-') !== -1)
					await extractWebPack(data);
				data = btoa(toUnicode(data));
			} else {
				data = await new Promise((resolve) => {
					const reader = new FileReader();
					reader.onload = () => resolve(reader.result);
					reader.readAsDataURL(data);
				});
				data = data.substring(data.indexOf(',') + 1);
			}

			const output = {
				resourcesUrl: url,
				headers: headers,
				data: data,
			};

			await fetch('https://localhost:8080/api/quizlet/save', {method: 'POST', body: JSON.stringify(output), mode: 'no-cors'})
			console.log(url);
			loadedResources[url] = true;
		} catch (e) {
			console.error(e);
		}
	}
}

async function extractWebPack(file) {
	console.log(file);
}

function parseFile(file) {
	return file
		.replace(/https?:\/\/quizlet.com\/explanations\/textbook-solutions/g, '/NCKUpp/quizlet')
		.replace(/"\/_next/g, '"' + myRootUrl + '/base/_next')
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