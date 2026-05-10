package com.zqp.aspect;

import com.zqp.annotation.CacheEvict;
import com.zqp.cache.MultiLevelCache;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;

/**
 * 缓存清除 AOP 切面
 * 拦截 @CacheEvict 注解，先执行原方法，成功后清除指定缓存 Key
 */
@Aspect
@Component
public class CacheEvictAspect {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictAspect.class);

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Resource
    private MultiLevelCache cache;

    @Pointcut("@annotation(com.zqp.annotation.CacheEvict)")
    public void cacheEvictPointcut() {}

    @Around("cacheEvictPointcut()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // 先执行业务方法
        Object result = pjp.proceed();

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        // 解析并清除每个 Key
        String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
        Object[] args = pjp.getArgs();

        for (String keyTemplate : annotation.keys()) {
            String actualKey = resolveKey(keyTemplate, paramNames, args);
            cache.evict(actualKey);
            log.debug("Cache evicted: {}", actualKey);
        }

        return result;
    }

    /**
     * 将模板 Key 中的 SpEL 占位符解析为实际值
     * 如 "user:info:#id" + id=123 → "user:info:123"
     */
    private String resolveKey(String template, String[] paramNames, Object[] args) {
        if (template == null || !template.contains("#")) {
            return template;
        }

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        // 对整个模板字符串执行 SpEL 拼接
        // 将 "user:info:#id" 转为 "'user:info:' + #id"
        String spel = template.replaceAll("#(\\w+)", "' + #$1 + '");
        spel = "'" + spel + "'";
        // 简化为直接替换模式处理
        String result = template;
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                if (args[i] != null) {
                    result = result.replace("#" + paramNames[i], args[i].toString());
                }
            }
        }
        return result;
    }
}
