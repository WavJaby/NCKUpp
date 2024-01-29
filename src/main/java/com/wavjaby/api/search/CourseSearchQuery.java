package com.wavjaby.api.search;

import java.util.Set;

public class CourseSearchQuery {
    final String searchID;
    final String deptNo;            // 系所 A...
    final String courseName;        // 課程名稱
    final String instructor;        // 教師姓名
    final String grade;             // 年級 1 ~ 4
    final TimeQuery time;
    final CourseHistorySearchQuery historySearch;
    final Set<String> serialFilter;

    public CourseSearchQuery(SearchQuery searchQuery, TimeQuery time) {
        this.searchID = searchQuery.searchID;
        this.deptNo = searchQuery.deptNo;
        this.serialFilter = null;
        this.courseName = searchQuery.courseName;
        this.instructor = searchQuery.instructor;
        this.grade = searchQuery.grade;
        this.time = time;
        this.historySearch = searchQuery.historySearch;
    }


    public CourseSearchQuery(SearchQuery searchQuery, String deptNo, Set<String> serialFilter) {
        this.searchID = searchQuery.searchID;
        this.deptNo = deptNo;
        this.serialFilter = serialFilter;

        this.courseName = null;
        this.instructor = null;
        this.grade = null;
        this.time = null;
        this.historySearch = null;
    }

    public boolean historySearch() {
        return historySearch != null;
    }


    public static class TimeQuery {
        final byte dayOfWeek;           // 星期 0 ~ 6
        final byte[] sectionOfDay;      // 節次 [0 ~ 15]

        public TimeQuery(byte dayOfWeek, byte[] sectionOfDay) {
            this.dayOfWeek = dayOfWeek;
            this.sectionOfDay = sectionOfDay;
        }

        public TimeQuery(TimeQuery timeQuery) {
            this.dayOfWeek = timeQuery.dayOfWeek;
            this.sectionOfDay = timeQuery.sectionOfDay.clone();
        }

        public static TimeQuery[] singleSection(byte dayOfWeek, Byte sectionOfDay) {
            return new TimeQuery[]{new TimeQuery(dayOfWeek, new byte[]{sectionOfDay})};
        }
    }
}
