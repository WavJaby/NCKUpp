package com.wavjaby;

import java.util.concurrent.ThreadFactory;

public class SimpleThreadFactory implements ThreadFactory {
    private final String name;
    private int id = 0;

    public SimpleThreadFactory(String name) {
        this.name = name;
    }

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, name + id++);
    }
}
