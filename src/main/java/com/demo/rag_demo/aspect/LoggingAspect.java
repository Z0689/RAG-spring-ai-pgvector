package com.demo.rag_demo.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * 记录 Controller 层方法执行耗时
     */
    @Around("execution(* com.demo.rag_demo.controller..*.*(..))")
    public Object logControllerMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();

        log.info("【请求开始】{}.{}", className, methodName);

        try {
            Object result = joinPoint.proceed();
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.info("【请求结束】{}.{} 执行耗时: {}ms", className, methodName, elapsedMs);
            return result;
        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.error("【请求异常】{}.{} 执行耗时: {}ms, 异常: {}",
                    className, methodName, elapsedMs, e.getMessage(), e);
            throw e;
        }
    }
}
