package com.archinamon;

import android.util.Log;
import com.archinamon.SequenceTask.Type;
import org.jetbrains.annotations.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SequenceBuilder {

    public static final String TAG = SequenceBuilder.class.getSimpleName();
    /*package_local*/ Set<FloatingRunnable> mTasks;
    /*package_local*/ FloatingRunnable      mPreCompileTask;
    /*package_local*/ FloatingRunnable      mPostCompileTask;
    /*package_local*/ Mode                  mMode;

    public SequenceBuilder() {
        mTasks = new LinkedHashSet<>();
    }

    public SequenceBuilder addSyncTask(Runnable task) {
        addTask(task, true);
        return this;
    }

    public SequenceBuilder addAsyncTask(Runnable task) {
        addTask(task, false);
        return this;
    }

    public SequenceBuilder setOnPreCompileSyncTask(Runnable task) {
        addOnPreCompileTask(task, true);
        return this;
    }

    public SequenceBuilder setOnPreCompileAsyncTask(Runnable task) {
        addOnPreCompileTask(task, false);
        return this;
    }

    public SequenceBuilder setOnPostCompileSyncTask(Runnable task) {
        addOnPostCompileTask(task, true);
        return this;
    }

    public SequenceBuilder setOnPostCompileAsyncTask(Runnable task) {
        addOnPostCompileTask(task, false);
        return this;
    }

    public SequenceBuilder parseSequence(@NotNull final Object type) {
        Class objClass = type.getClass();
        Annotation[] annotations = objClass.getAnnotations();

        boolean isSequenceAnnotationPresent = false;
        for (Annotation annotation : annotations) {
            if (annotation instanceof Sequence) {
                isSequenceAnnotationPresent = true;
                break;
            }
        }

        if (!isSequenceAnnotationPresent)
            throw new IllegalArgumentException("argument should be annotated as Sequence");

        Sequence seqAnnotation = (Sequence) objClass.getAnnotation(Sequence.class);
        if (objClass.isSynthetic() && !seqAnnotation.synthetic())
            throw new IllegalArgumentException("sequence class should be annotated as synthetic or avoid synth access");

        for (final Method method : objClass.getDeclaredMethods()) {
            SequenceTask sequenceTask = method.getAnnotation(SequenceTask.class);
            if (sequenceTask == null)
                continue;

            if (!method.isAccessible()) {
                method.setAccessible(true);
            }

            final boolean isSync = sequenceTask.value() == Type.SYNC_UI;
            switch (sequenceTask.exec_order()) {
                case PRE_COMPILE:
                    addOnPreCompileTask(() -> {
                        try {method.invoke(type);} catch (Exception e) {Log.e(TAG, e.getMessage());}
                    }, isSync);
                    break;
                case POST_COMPILE:
                    addOnPostCompileTask(() -> {
                        try {method.invoke(type);} catch (Exception e) {Log.e(TAG, e.getMessage());}
                    }, isSync);
                    break;
                case RUNTIME:
                    addTask(() -> {
                        try {method.invoke(type);} catch (Exception e) {Log.e(TAG, e.getMessage());}
                    }, isSync);
                    break;
            }
        }

        return this;
    }

    public ISequencer build(Mode mode) {
        mMode = mode;
        return new RunnableSequencerImpl(this);
    }

    private void addTask(@NotNull Runnable task, boolean shouldRunInUiThread) {
        FloatingRunnable floatTask = FloatingRunnable.newInstance();
        if (shouldRunInUiThread) floatTask.markUiRunning();
        floatTask.define(task);
        mTasks.add(floatTask);
    }

    private void addOnPreCompileTask(@NotNull Runnable task, boolean shouldRunInUiThread) {
        FloatingRunnable floatTask = FloatingRunnable.newInstance();
        if (shouldRunInUiThread) floatTask.markUiRunning();
        floatTask.define(task);
        mPreCompileTask = floatTask;
    }

    private void addOnPostCompileTask(@NotNull Runnable task, boolean shouldRunInUiThread) {
        FloatingRunnable floatTask = FloatingRunnable.newInstance();
        if (shouldRunInUiThread) floatTask.markUiRunning();
        floatTask.define(task);
        mPostCompileTask = floatTask;
    }
}