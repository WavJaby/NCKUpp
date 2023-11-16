package com.wavjaby.api.search;

public class CourseSearchID {
    final String searchID, historySearchID, historySearchPHPSESSID;

    public CourseSearchID(String searchID, String historySearchID, String historySearchPHPSESSID) {
        this.searchID = searchID;
        this.historySearchID = historySearchID;
        this.historySearchPHPSESSID = historySearchPHPSESSID;
    }

    public CourseSearchID(String searchID) {
        this.searchID = searchID;
        this.historySearchID = null;
        this.historySearchPHPSESSID = null;
    }

    public CourseSearchID(String historySearchID, String historySearchPHPSESSID) {
        this.searchID = null;
        this.historySearchID = historySearchID;
        this.historySearchPHPSESSID = historySearchPHPSESSID;
    }

    public String getAsCookieValue() {
        if (searchID == null && historySearchID == null)
            return "|";
        if (historySearchID != null)
            return '|' + historySearchID + ',' + historySearchPHPSESSID;
        if (searchID != null)
            return searchID + '|';
        return searchID + '|' + historySearchID;
    }
}
