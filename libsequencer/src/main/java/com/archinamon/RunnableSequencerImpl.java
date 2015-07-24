package com.archinamon;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import org.jetbrains.annotations.NotNull;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

final class RunnableSequencerImpl implements ISequencer {

    private static final int TASK_PRE_COMPILE  = 0x0016;
    private static final int TASK_DO_COMPILE   = 0x0024;
    private static final int TASK_POST_COMPILE = 0x0032;
    private static final int TASK_TERMINATE    = 0xdead;
    private Set<FloatingRunnable> mTaskSet;
    private FloatingRunnable      mPreCompileTask;
    private FloatingRunnable      mPostCompileTask;
    private Mode                  mExecutingMode;

    final Handler mUiProcessor = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(final Message msg) {
            if (msg.obj != null) {
                FloatingRunnable task = (FloatingRunnable) msg.obj;
                task.run();
            }
        }
    };

    {Looper.prepare();}
    final Handler mProcessor = new Handler(Looper.myLooper()) {

        {Looper.loop();}

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
                    Looper.myLooper().quit();

                    break;
                case TASK_TERMINATE:
                    Looper.myLooper()
                          .quit();
                    //noinspection unchecked
                    mTaskSet = Collections.EMPTY_SET;
                    mPreCompileTask = null;
                    mPostCompileTask = null;
                    break;
            }
        }

        void preCompile(@NotNull final FloatingRunnable task) {
            Runnable workTask = () -> {
                task.run();
                mProcessor.sendEmptyMessage(TASK_DO_COMPILE);
            };

            if (task.isUiRunning()) {
                Message msg = mUiProcessor.obtainMessage();
                msg.obj = workTask;
                mUiProcessor.sendMessageAtFrontOfQueue(msg);
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
                            Message msg = mUiProcessor.obtainMessage();
                            msg.obj = task;
                            mUiProcessor.sendMessage(msg);
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
                Message msg = mUiProcessor.obtainMessage();
                msg.obj = task;
                mUiProcessor.sendMessageAtFrontOfQueue(msg);
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
            } else {
                Looper.myLooper()
                      .quit();
            }
        }

        private void doCoherenceStep(final Iterator<FloatingRunnable> iterator) {
            if (iterator.hasNext()) {
                final FloatingRunnable task = iterator.next();
                task.mCallback = sentenceId -> doCoherenceStep(iterator);

                if (task.isUiRunning()) {
                    Message msg = mUiProcessor.obtainMessage();
                    msg.obj = task;
                    mUiProcessor.sendMessageAtFrontOfQueue(msg);
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

    @Override
    public final void terminate() {
        mProcessor.sendEmptyMessage(TASK_TERMINATE);
    }
}