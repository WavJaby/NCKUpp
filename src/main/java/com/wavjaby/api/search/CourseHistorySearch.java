package com.wavjaby.api.search;

public class CourseHistorySearch {
    final int yearBegin, semBegin, yearEnd, semEnd;

    public CourseHistorySearch(int yearBegin, int semBegin, int yearEnd, int semEnd) {
        this.yearBegin = yearBegin;
        this.semBegin = semBegin;
        this.yearEnd = yearEnd;
        this.semEnd = semEnd;
    }
}
