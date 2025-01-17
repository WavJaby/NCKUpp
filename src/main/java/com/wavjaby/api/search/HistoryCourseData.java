package com.wavjaby.api.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class HistoryCourseData {
    final String semester;
    final String departmentId;
    final Integer serialNumber; // Nullable
    final String attributeCode;
    final String systemCode;
    final Integer forGrade;  // Nullable
    final String forClass; // Nullable
    final String forClassGroup;  // Nullable
    final String category;  // Nullable
    final String courseName; // Maybe empty
    final String courseNote; // Nullable
    final String courseLimit; // Nullable
    final Object[] tagIds; // Nullable
    final Float credits; // Nullable
    final Boolean required; // Nullable
    final Object[] instructorIds; // Nullable
    final Integer selected; // Nullable
    final Integer available; // Nullable
    final String[][] timeList; // Nullable

    public HistoryCourseData(String semester, Object[] tagIds, Object[] instructorIds, String[][] timeList, ResultSet result) throws SQLException {
        this.semester = semester;
        this.departmentId = result.getString("department_id");
        this.serialNumber = result.getObject("serial_number", Integer.class);
        this.attributeCode = result.getString("attribute_code");
        this.systemCode = result.getString("system_code");
        this.forGrade = result.getObject("for_grade", Integer.class);
        this.forClass = result.getString("for_class");
        this.forClassGroup = result.getString("for_class_group");
        this.category = result.getString("category_tw");
        this.courseName = result.getString("name_tw");
        this.courseNote = result.getString("note_tw");
        this.courseLimit = result.getString("limit_tw");
        this.tagIds = tagIds;
        this.credits = result.getObject("credits", Float.class);
        this.required = result.getObject("required", Boolean.class);
        this.instructorIds = instructorIds;
        this.selected = result.getObject("selected", Integer.class);
        this.available = result.getObject("available", Integer.class);
        this.timeList = timeList;
    }

    public CourseData toCourseData(Map<Integer, CourseData.TagData> tags, Map<Integer, String> instructors, Map<String, String> roomNames) {
        CourseData.TagData[] finalTags = tagIds.length == 0 ? null : new CourseData.TagData[tagIds.length];
        for (int i = 0; i < tagIds.length; i++)
            finalTags[i] = tags.get((int) tagIds[i]);
        String[] finalInstructors = instructorIds.length == 0 ? null : new String[instructorIds.length];
        for (int i = 0; i < instructorIds.length; i++)
            finalInstructors[i] = instructors.get((int) instructorIds[i]);
        CourseData.TimeData[] finalTimeList = timeList.length == 0 ? null : new CourseData.TimeData[timeList.length];
        for (int i = 0; i < timeList.length; i++) {
            String[] time = timeList[i];
            if (time.length == 1)
                finalTimeList[i] = new CourseData.TimeData(time[0]);
            else
                finalTimeList[i] = new CourseData.TimeData(
                        time[0] == null ? null : Byte.parseByte(time[0]),
                        time[1] == null ? null : Byte.parseByte(time[1]),
                        time[2] == null ? null : Byte.parseByte(time[2]),
                        time[3], time[4], time[3] == null ? null: roomNames.get(time[3] + '_' + time[4]));
        }


        return new CourseData(
                this.semester,
                this.departmentId,
                this.serialNumber,
                this.systemCode,
                this.attributeCode,
                this.forGrade,
                this.forClass,
                this.forClassGroup,
                this.category,
                this.courseName,
                this.courseNote,
                this.courseLimit,
                finalTags,
                this.credits,
                this.required,
                finalInstructors,
                this.selected,
                this.available,
                finalTimeList,
                null, null, null, null
        );
    }
}
