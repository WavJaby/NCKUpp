package com.wavjaby.api.search;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SearchQuery {
    final CourseSearchID searchID;
    final CourseHistorySearch historySearch;
    final String deptNo;            // 系所 AX
    final String courseName;        // 課程名稱
    final String instructor;        // 教師姓名
    final String grade;             // 年級 1 ~ 4
    final CourseSearchQuery.TimeQuery[] time;

    final Map<String, Set<String>> serialIdNumber;     // <系號,[序號]>
    final boolean getAll;
    final String error;

    SearchQuery(CourseSearchID courseSearchID,
                Map<String, Set<String>> serialIdNumber,
                CourseHistorySearch historySearch,
                String deptNo,
                CourseSearchQuery.TimeQuery[] time,
                String courseName, String instructor, String grade) {
        this.searchID = courseSearchID;
        if ("ALL".equals(deptNo)) {
            this.getAll = true;
            this.deptNo = null;
        } else {
            this.getAll = false;
            this.deptNo = deptNo;
        }
        // TODO: Add warn if other query given

        this.serialIdNumber = serialIdNumber;
        // TODO: Add warn if other query given

        this.historySearch = historySearch;

        this.time = time;
        this.courseName = courseName;
        this.instructor = instructor;
        this.grade = grade;
        this.error = null;
    }

    public SearchQuery(CourseData courseData) {
        this.deptNo = courseData.serialNumber == null
                ? null
                : courseData.serialNumber.substring(0, courseData.serialNumber.indexOf('-'));
        // TODO: Get search ID
        this.searchID = null;
        this.courseName = courseData.courseName;
        this.instructor = courseData.instructors == null ? null : courseData.instructors[0];
        this.grade = courseData.forGrade == null ? null : String.valueOf(courseData.forGrade);
        Byte dayOfWeek = null, sectionOfDay = null;
        if (courseData.timeList != null) {
            for (CourseData.TimeData time : courseData.timeList) {
                if (time.sectionStart == null) continue;
                dayOfWeek = time.dayOfWeek;
                sectionOfDay = time.sectionStart;
                break;
            }
            // if no section
            if (dayOfWeek == null)
                dayOfWeek = courseData.timeList[0].dayOfWeek;
            this.time = CourseSearchQuery.TimeQuery.singleSection(dayOfWeek, sectionOfDay);
        } else
            this.time = null;

        this.getAll = false;
        this.serialIdNumber = null;
        // TODO: Support history course
        this.historySearch = null;
        this.error = null;
    }

    public SearchQuery(SearchQuery searchQuery) {
        this.searchID = searchQuery.searchID;
        this.deptNo = searchQuery.deptNo;
        this.courseName = searchQuery.courseName;
        this.instructor = searchQuery.instructor;
        this.grade = searchQuery.grade;

        if (searchQuery.time != null) {
            this.time = new CourseSearchQuery.TimeQuery[searchQuery.time.length];
            for (int i = 0; i < this.time.length; i++)
                this.time[i] = new CourseSearchQuery.TimeQuery(searchQuery.time[i]);
        } else
            this.time = null;

        if (searchQuery.serialIdNumber != null) {
            this.serialIdNumber = new HashMap<>(searchQuery.serialIdNumber.size());
            // Deep clone
            for (Map.Entry<String, Set<String>> entry : searchQuery.serialIdNumber.entrySet())
                this.serialIdNumber.put(entry.getKey(), new HashSet<>(entry.getValue()));
        } else
            serialIdNumber = null;

        this.getAll = searchQuery.getAll;

        this.historySearch = searchQuery.historySearch;
        this.error = null;
    }

    private SearchQuery(String message) {
        error = message;

        this.searchID = null;
        this.historySearch = null;
        this.deptNo = null;
        this.courseName = null;
        this.instructor = null;
        this.grade = null;
        this.time = null;
        this.serialIdNumber = null;
        this.getAll = false;
    }

    public CourseSearchQuery[] toCourseQueriesSerial() {
        CourseSearchQuery[] searchQueries = new CourseSearchQuery[serialIdNumber.size()];
        int i = 0;
        for (Map.Entry<String, Set<String>> entry : serialIdNumber.entrySet()) {
            searchQueries[i++] = new CourseSearchQuery(this, entry.getKey(), entry.getValue());
        }
        return searchQueries;
    }

    public CourseSearchQuery[] toCourseQueriesMultiTime() {
        CourseSearchQuery[] searchQueries = new CourseSearchQuery[time.length];
        for (int i = 0; i < time.length; i++) {
            searchQueries[i] = new CourseSearchQuery(this, time[i]);
        }
        return searchQueries;
    }

    public CourseSearchQuery toCourseQuery() {
        return new CourseSearchQuery(this, time == null ? null : time[0]);
    }

    boolean noQuery() {
        return !getAll &&
                deptNo == null &&
                courseName == null &&
                instructor == null &&
                grade == null &&
                time == null &&
                serialIdNumber == null;
    }

    boolean getAll() {
        return getAll;
    }

    boolean getSerial() {
        return serialIdNumber != null;
    }

    public CourseSearchID getSearchID() {
        return searchID;
    }

    boolean multipleTime() {
        return time != null && time.length > 1;
    }

    // Warn Error message
    static SearchQuery newError(String message) {
        return new SearchQuery(message);
    }

    public String getError() {
        return error;
    }
}
