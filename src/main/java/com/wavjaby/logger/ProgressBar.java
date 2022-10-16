package com.wavjaby.logger;

public class ProgressBar {
    protected final String tag;
    protected float progress = 0;
    private OnProgress onProgress;

    public ProgressBar(String tag) {
        this.tag = tag;
    }

    public void setProgress(float progress) {
        this.progress = progress;
        onProgress.onChange();
    }

    public void setListener(OnProgress onProgress) {
        this.onProgress = onProgress;
    }

    interface OnProgress {
        void onChange();
    }
}
