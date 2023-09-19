const UglifyJS = require('uglify-js');

const argv = Bun.argv;
const inputFilePath = argv[2];
const outputFilePath = argv[3];

const script = Bun.file(inputFilePath);
const fileNameStart = outputFilePath.lastIndexOf('/') + 1;
const outputFileParentPath = outputFilePath.substring(0, fileNameStart);
const outputFileName = outputFilePath.substring(fileNameStart);
const outputJsMapFilePath = outputFilePath + '.map';

let fileContent = await script.text();
// Edit import
fileContent = fileContent.replaceAll('from \'' + outputFileParentPath, 'from \'./');
fileContent = fileContent.replaceAll('from \'./res/', 'from \'./../');
// Minify
const result = UglifyJS.minify(fileContent, {
	compress: {
		toplevel: true,
	},
	sourceMap: {
		root: 'https://wavjaby.github.io/NCKUpp',
		filename: inputFilePath,
		url: outputFileName + '.map',
	},
	mangle: true,
	toplevel: true,
});

const outputFile = Bun.file(outputFilePath);
await Bun.write(outputFile, result.code);
const outputJsMapFile = Bun.file(outputJsMapFilePath);
await Bun.write(outputJsMapFile, result.map);