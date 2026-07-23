package com.example.ragdemo.coupon;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    String key();
    long waitTime() default 3;
    long leaseTime() default 10;
    LockType lockType() default LockType.REENTRANT;
    enum LockType { REENTRANT, FAIR, READ, WRITE }
}
