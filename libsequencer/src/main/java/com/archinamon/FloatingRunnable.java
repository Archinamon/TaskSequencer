package com.archinamon;

import org.jetbrains.annotations.Contract;
import java.util.Random;

final class FloatingRunnable implements Runnable {

    final long _ID = new Random().nextInt(this.hashCode());
    boolean isUiRunning = false;
    ISentenceCallback mCallback;
    Runnable          mLinkedTask;

    @Contract("-> !null")
    static FloatingRunnable newInstance() {
        return new FloatingRunnable();
    }

    void define(Runnable task) {
        mLinkedTask = task;
    }

    boolean isUiRunning() {
        return isUiRunning;
    }

    void markUiRunning() {
        isUiRunning = true;
    }

    @Override
    public void run() {
        if (mLinkedTask == null) throw new LinkageError("You should use define() method to link executable task");
        mLinkedTask.run();
        if (mCallback != null) mCallback.onComplete(_ID);
    }
}