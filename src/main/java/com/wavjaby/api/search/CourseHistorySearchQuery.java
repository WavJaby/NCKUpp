package com.wavjaby.api.search;

public class CourseHistorySearchQuery {
    final int yearBegin, semBegin, yearEnd, semEnd;

    public CourseHistorySearchQuery(int yearBegin, int semBegin, int yearEnd, int semEnd) {
        this.yearBegin = yearBegin;
        this.semBegin = semBegin;
        this.yearEnd = yearEnd;
        this.semEnd = semEnd;
    }

    @Override
    public String toString() {
        return yearBegin + "_" + semBegin + "_" + yearEnd + "_" + semEnd;
    }
}
