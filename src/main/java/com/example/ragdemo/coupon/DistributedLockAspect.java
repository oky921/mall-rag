package com.example.ragdemo.coupon;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DistributedLockAspect {
    private final LockFactory lockFactory;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    public DistributedLockAspect(LockFactory lockFactory) { this.lockFactory = lockFactory; }

    @Around("@annotation(lock)")
    public Object around(ProceedingJoinPoint pjp, DistributedLock lock) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();
        if (names != null) for (int i = 0; i < names.length; i++) context.setVariable(names[i], pjp.getArgs()[i]);
        String key = parser.parseExpression(lock.key()).getValue(context, String.class);
        RLock rLock = lockFactory.create(lock.lockType(), "coupon:lock:" + key);
        if (!rLock.tryLock(lock.waitTime(), lock.leaseTime(), java.util.concurrent.TimeUnit.SECONDS))
            throw new IllegalStateException("获取分布式锁超时");
        try { return pjp.proceed(); } finally { if (rLock.isHeldByCurrentThread()) rLock.unlock(); }
    }
}
