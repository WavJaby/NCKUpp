package com.wavjaby.logger;

public class Progressbar {
    protected final String tag;
    protected String message;
    protected float progress;
    private final ProgressEvent event;
    private boolean finish;

    public Progressbar(String tag, ProgressEvent event) {
        this.tag = tag;
        this.event = event;
        this.progress = 0;
    }

    public void setProgress(float progress) {
        if (finish)
            return;
        this.progress = progress;
        if (progress >= 100f) {
            finish = true;
            event.onFinish(this);
        } else
            event.onChange();
    }

    public void setProgress(String message, float progress) {
        setProgress(progress);
        this.message = message;
    }

    public interface ProgressEvent {
        void onChange();

        void onFinish(Progressbar progressbar);
    }
}
