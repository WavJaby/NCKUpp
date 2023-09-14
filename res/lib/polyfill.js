'use strict';

if (!Object.fromEntries) {
	Object.fromEntries = function (entries) {
		var obj = {};
		if (entries._list)
			for (var i in entries._list) {
				obj[entries._list[i].name] = entries._list[i].value;
			}
		else
			for (var key in entries) {
				obj[key] = entries[key];
			}
		return obj;
	};
}

if (!String.prototype.startsWith) {
	String.prototype.startsWith = function (searchString, position) {
		return this.indexOf(searchString) === (position == null ? 0 : position);
	};
}