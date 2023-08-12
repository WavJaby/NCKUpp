if (!Object.fromEntries) {
	Object.fromEntries = function (entries) {
		var obj = {};
		console.log(entries._list);
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