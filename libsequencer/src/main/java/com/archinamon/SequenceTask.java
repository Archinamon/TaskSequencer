package com.archinamon;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SequenceTask {

    enum Type {

        SYNC_UI,
        ASYNC
    }

    enum Order {

        PRE_COMPILE,
        POST_COMPILE,
        RUNTIME
    }

    Type value() default Type.SYNC_UI;

    Order exec_order() default Order.RUNTIME;
}