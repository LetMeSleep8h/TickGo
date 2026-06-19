package com.eighthours.tickgo.ticket.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    String prefix();

    long processingTtlSeconds() default 60L;

    long successTtlSeconds() default 1800L;
}
