package com.wavjaby.lib;

public class ThreadFactory implements java.util.concurrent.ThreadFactory {
    private final String namePrefix;
    private final ThreadGroup group;
    private final int priority;
    private int id = 0;

    public ThreadFactory(String namePrefix) {
        SecurityManager s = System.getSecurityManager();
        this.group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix + '-';
        this.priority = Thread.NORM_PRIORITY;
    }

    public ThreadFactory(String namePrefix, int priority) {
        SecurityManager s = System.getSecurityManager();
        this.group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix + '-';
        this.priority = priority;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r,
                namePrefix + id++,
                0);
        if (t.isDaemon())
            t.setDaemon(false);
        t.setPriority(this.priority);
        return t;
    }
}
