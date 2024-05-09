chrome.runtime.onInstalled.addListener(() => {
    chrome.action.setBadgeText({text: null});
});

chrome.tabs.onActivated.addListener(async function (tab) {
    // chrome.scripting.executeScript({
    //     target: {tabId: tab.tabId},
    //     file: 'inject.js'
    // });

});

function findStringBetween(input, where, from, end, includeFromEnd) {
    let startIndex = input.indexOf(where), endIndex = -1;
    if (startIndex !== -1) startIndex = input.indexOf(from, startIndex + where.length());
    if (startIndex !== -1) endIndex = input.indexOf(end, startIndex + from.length());
    return startIndex === -1 || endIndex === -1 ? null : includeFromEnd
        ? input.substring(startIndex, endIndex + end.length())
        : input.substring(startIndex + from.length(), endIndex);
}

async function openLoginPage() {
    const response = await fetch('https://course.ncku.edu.tw/index.php?c=auth&m=oauth&time=' +
        (Date.now() / 1000), {redirect: 'follow'});
    if (!response.url.startsWith('https://fs.ncku.edu.tw'))
        return;
    const tab = await chrome.tabs.create({url: response.url});
    tab
}

chrome.webNavigation.onCompleted.addListener(async function (details) {
    if (details.url !== 'https://course.ncku.edu.tw/index.php?c=portal')
        return;
    console.log(details);
}, {url: [{hostContains: 'course.ncku.edu.tw'}]});

// Main page
chrome.webNavigation.onCompleted.addListener(async function (details) {
    const response = await fetch('https://course.ncku.edu.tw/index.php?c=auth')
    const pageRaw = await response.text();

    const login = pageRaw.indexOf('./index.php?c=auth&m=logout') !== -1;
    if (!login) {
        await openLoginPage();
    }
    console.log(login);
}, {url: [{hostContains: 'localhost'}]});









