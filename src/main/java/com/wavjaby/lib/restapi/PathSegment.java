package com.wavjaby.lib.restapi;

import java.util.ArrayList;
import java.util.List;

public class PathSegment {
    final boolean fixed, matchAllAfter;
    final public String[] pattern;
    final String[] placeholder;

    public PathSegment(String segment) {
        List<String> pattern = new ArrayList<>();
        List<String> placeholder = new ArrayList<>();
        boolean matchAllAfter = false;
        int lastPos = 0;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '*') {
                // **
                if (i > 0 && lastPos == i) {
                    matchAllAfter = true;
                    if (lastPos != 1 || i + 1 < segment.length())
                        throw new IllegalArgumentException("No pattern allow before or after **");
                    break;
                }
                pattern.add(segment.substring(lastPos, i));
                lastPos = i + 1;
            }
        }
        this.matchAllAfter = matchAllAfter;
        this.fixed = pattern.isEmpty();
        if (!matchAllAfter)
            pattern.add(segment.substring(lastPos));

        if (this.fixed) {
            this.pattern = new String[]{segment};
            this.placeholder = null;
        } else {
            this.pattern = pattern.toArray(new String[0]);
            this.placeholder = placeholder.toArray(new String[0]);
        }
        pattern.clear();
        placeholder.clear();
    }

    public boolean isMatch(String segment) {
        int index, lastIndex = -1;
        for (int i = 0; i < pattern.length; i++) {
            String s = pattern[i];
            index = (i + 1 == pattern.length && s.isEmpty()) // Is last pattern empty
                    ? segment.length()
                    : segment.indexOf(s, lastIndex);
            if (index == -1 || index < lastIndex ||
                    lastIndex == -1 && index != 0)
                return false;
            lastIndex = index + s.length();
        }
        return lastIndex == segment.length();
    }
}
