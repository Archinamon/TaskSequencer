package com.archinamon;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import org.jetbrains.annotations.NotNull;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

final class RunnableSequencerImpl implements ISequencer {

    private static final int TASK_PRE_COMPILE  = 0x0016;
    private static final int TASK_DO_COMPILE   = 0x0024;
    private static final int TASK_POST_COMPILE = 0x0032;
    private Activity                            mContext;
    private Set<FloatingRunnable> mTaskSet;
    private FloatingRunnable      mPreCompileTask;
    private FloatingRunnable      mPostCompileTask;
    private Mode                  mExecutingMode;

    final Handler mProcessor = new Handler() {

        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case TASK_PRE_COMPILE:
                    assert msg.obj != null;
                    final FloatingRunnable actionPreCompile = ((FloatingRunnable) msg.obj);
                    preCompile(actionPreCompile);

                    break;
                case TASK_DO_COMPILE:
                    compile();
                    break;
                case TASK_POST_COMPILE:
                    assert msg.obj != null;
                    final FloatingRunnable actionPostCompile = ((FloatingRunnable) msg.obj);
                    postCompile(actionPostCompile);

                    break;
            }
        }

        void preCompile(@NotNull final FloatingRunnable task) {
            Runnable workTask = () -> {
                task.run();
                mProcessor.sendEmptyMessage(TASK_DO_COMPILE);
            };

            if (task.isUiRunning()) {
                mContext.runOnUiThread(workTask);
            } else {
                runOnSeparateThread(workTask);
            }
        }

        void compile() {
            switch (mExecutingMode) {
                case COHERENCE:
                    final Iterator<FloatingRunnable> iterator = mTaskSet.iterator();
                    doCoherenceStep(iterator);
                    break;
                case ONEWAY:
                    for (final FloatingRunnable task : mTaskSet) {
                        if (task.isUiRunning()) {
                            mContext.runOnUiThread(task::run);
                        } else {
                            runOnSeparateThread(task);
                        }
                    }

                    completeCompile();
                    break;
            }
        }

        void postCompile(@NotNull final FloatingRunnable task) {
            if (task.isUiRunning()) {
                mContext.runOnUiThread(task::run);
            } else {
                runOnSeparateThread(task);
            }
        }

        private void completeCompile() {
            if (mPostCompileTask != null) {
                Message message = mProcessor.obtainMessage();
                message.what = TASK_POST_COMPILE;
                message.obj = mPostCompileTask;

                mProcessor.sendMessage(message);
            }
        }

        private void doCoherenceStep(final Iterator<FloatingRunnable> iterator) {
            if (iterator.hasNext()) {
                final FloatingRunnable task = iterator.next();
                task.mCallback = sentenceId -> doCoherenceStep(iterator);

                if (task.isUiRunning()) {
                    mContext.runOnUiThread(task::run);
                } else {
                    runOnSeparateThread(task);
                }
            } else {
                completeCompile();
            }
        }

        private void runOnSeparateThread(Runnable task) {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.setUncaughtExceptionHandler(Thread.currentThread()
                                                     .getUncaughtExceptionHandler());
            thread.start();
        }
    };

    /*package_local*/ RunnableSequencerImpl(SequenceBuilder config) {
        mContext = config.mActivity;
        mTaskSet = Collections.unmodifiableSet(config.mTasks);
        mPreCompileTask = config.mPreCompileTask;
        mPostCompileTask = config.mPostCompileTask;
        mExecutingMode = config.mMode;
    }

    @Override
    public final void exec() {
        if (mPreCompileTask != null) {
            Message msg = mProcessor.obtainMessage();
            msg.what = TASK_PRE_COMPILE;
            msg.obj = mPreCompileTask;

            mProcessor.sendMessage(msg);
        } else {
            mProcessor.sendEmptyMessage(TASK_DO_COMPILE);
        }
    }
}