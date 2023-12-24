const UglifyJS = require('uglify-js');

const Bun = this.Bun || (function () {
	const {argv} = require('node:process');
	const fs = require('fs/promises');
	return {
		argv: argv,
		file: fileName => ({text: () => fs.readFile(fileName, {encoding: 'utf8'}), fileName: fileName}),
		write: (file, content) => fs.writeFile(file.fileName, content, {encoding: 'utf8'}),
	}
})();

const argv = Bun.argv;
const inputFilePath = argv[2];
const outputFilePath = argv[3];

const script = Bun.file(inputFilePath);
const fileNameStart = outputFilePath.lastIndexOf('/') + 1;
const outputFileParentPath = outputFilePath.substring(0, fileNameStart);
const outputFileName = outputFilePath.substring(fileNameStart);
const outputJsMapFilePath = outputFilePath + '.map';

(async function () {
	let fileContent = await script.text();
// Edit import
	fileContent = fileContent.replaceAll('from \'' + outputFileParentPath, 'from \'./');
	fileContent = fileContent.replaceAll('from \'./res/', 'from \'./../');
console.log('File read');
// Minify
	const result = UglifyJS.minify(fileContent, {
		compress: {
			toplevel: true,
		},
		sourceMap: {
			root: 'https://localhost:8080/NCKUpp/res',
			filename: inputFilePath,
			url: outputFileName + '.map',
		},
		mangle: true,
		toplevel: true,
	});
console.log('File minify');
	const outputFile = Bun.file(outputFilePath);
	await Bun.write(outputFile, result.code);
	const outputJsMapFile = Bun.file(outputJsMapFilePath);
	await Bun.write(outputJsMapFile, result.map);
})();
console.log('File write');