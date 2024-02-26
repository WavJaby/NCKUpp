chrome.runtime.onInstalled.addListener(() => {
	chrome.action.setBadgeText({text: null});
});
