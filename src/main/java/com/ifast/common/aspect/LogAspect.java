package com.ifast.common.aspect;

import com.ifast.common.annotation.Log;
import com.ifast.common.config.IFastProperties;
import com.ifast.common.dao.LogDao;
import com.ifast.common.domain.LogDO;
import com.ifast.common.utils.*;
import com.ifast.sys.domain.UserDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Map;

/**
 * <pre>
 * 日志切面
 * </pre>
 * <small> 2018年3月22日 | Aron</small>
 */
@Aspect
@Component
@Slf4j
@Data
@AllArgsConstructor
public class LogAspect {

    private final LogDao logMapper;
    private final IFastProperties iFastProperties;

    @Pointcut("execution(public * com.ifast.*.controller.*.*(..))")
    public void logController(){}
    
    /**
     * 记录controller日志，包括请求、ip、参数、响应结果
     */
    @Around("logController()")
    public Object controller(ProceedingJoinPoint point) {

        long beginTime = System.currentTimeMillis();
        Object result = null;
        try {
            result = point.proceed();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        long time = System.currentTimeMillis() - beginTime;

        print(point, result, time, iFastProperties.isLogPretty());
        saveLog(point, time);

        return result;
    }

    /**
     * 日志打印
     * @param point
     * @param result
     * @param time
     * @param isLogPretty
     */
    private void print(ProceedingJoinPoint point, Object result, long time, boolean isLogPretty) {
        HttpServletRequest request = HttpContextUtils.getHttpServletRequest();
        if(isLogPretty){
            log.info("User request info  ---- {} ---- S", DateUtils.format(new Date(),DateUtils.DATE_TIME_PATTERN_19));
            log.info("请求接口: {} {}@{} {} {}.{}", request.getMethod(), request.getRequestURI(), ShiroUtils.getUserId(), IPUtils.getIpAddr(request), point.getTarget().getClass().getSimpleName(), point.getSignature().getName());
            log.info("请求参数:{}", JSONUtils.beanToJson(point.getArgs()));
            log.info("请求耗时:{} ms", time);
            log.info("请求用户:{} ", ShiroUtils.getUserId());
            log.info("请求结果:{}", JSONUtils.beanToJson(result));
            log.info("------------------------------------------------ E", DateUtils.format(new Date(),DateUtils.DATE_TIME_PATTERN_19));
        } else {
            log.info("【请求】：{} {}@{} {} {}.{}{} (耗时 {} ms) 【返回】：{}", request.getMethod(), request.getRequestURI(), ShiroUtils.getUserId(), IPUtils.getIpAddr(request), point.getTarget().getClass().getSimpleName(), point.getSignature().getName(), JSONUtils.beanToJson(point.getArgs()), time, JSONUtils.beanToJson(result));
        }
    }

    /**
     * <pre>
     * 保存日志
     * </pre>
     * <small> 2018年3月22日 | Aron</small>
     * @param joinPoint
     * @param time
     */
    private void saveLog(ProceedingJoinPoint joinPoint, long time) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LogDO sysLog = new LogDO();
        Log syslog = method.getAnnotation(Log.class);
        if (syslog != null) {
            // 注解上的描述
            sysLog.setOperation(syslog.value());
        }
        // 请求的方法名
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = signature.getName();
        String params;
        HttpServletRequest request = HttpContextUtils.getHttpServletRequest();
        if(request != null) {
        	sysLog.setMethod(request.getMethod()+" "+request.getRequestURI());
        	Map<String, String[]> parameterMap = request.getParameterMap();
        	params = JSONUtils.beanToJson(parameterMap);
        	// 设置IP地址
        	sysLog.setIp(IPUtils.getIpAddr(request));
        }else {
        	sysLog.setMethod(className + "." + methodName + "()");
        	Object[] args = joinPoint.getArgs();
        	params = JSONUtils.beanToJson(args);
        }
        int maxLength = 4999;
        if(params.length() > maxLength){
        	params = params.substring(0, maxLength);
        }
        sysLog.setParams(params);
        // 用户名
    	UserDO currUser = ShiroUtils.getSysUser();
    	if (null == currUser) {
    		sysLog.setUserId(-1L);
    		sysLog.setUsername("");
    	} else {
    		sysLog.setUserId(currUser.getId());
    		sysLog.setUsername(currUser.getUsername());
    	}
        sysLog.setTime((int) time);
        // 系统当前时间
        Date date = new Date();
        sysLog.setGmtCreate(date);
        // 保存系统日志
        logMapper.insert(sysLog);
    }
}
