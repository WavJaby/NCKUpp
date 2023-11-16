package com.wavjaby;

public interface Module {
    default void start() {
    }

    default void stop() {
    }

    default boolean api() {
        return true;
    }

    String getTag();
}
