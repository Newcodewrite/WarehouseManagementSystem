package com.lanxi.wms.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanxi.wms.entity.SysOperLog;
import com.lanxi.wms.security.LoginUser;
import com.lanxi.wms.security.SecurityUtil;
import com.lanxi.wms.service.OperLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作日志切面，异步落库
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final OperLogService operLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint point, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        SysOperLog log = new SysOperLog();
        log.setModule(operLog.module());
        log.setDescription(operLog.value());
        try {
            LoginUser user = SecurityUtil.getLoginUser();
            if (user != null) {
                log.setUsername(user.getUsername());
            }
        } catch (Exception ignored) {
        }
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                log.setIp(req.getRemoteAddr());
                log.setMethod(req.getMethod() + " " + req.getRequestURI());
            }
        } catch (Exception ignored) {
        }
        try {
            Object result = point.proceed();
            log.setStatus(1);
            log.setCostMs(System.currentTimeMillis() - start);
            saveLog(log, point);
            return result;
        } catch (Throwable e) {
            log.setStatus(0);
            log.setCostMs(System.currentTimeMillis() - start);
            log.setErrorMsg(e.getMessage());
            saveLog(log, point);
            throw e;
        }
    }

    private void saveLog(SysOperLog log, ProceedingJoinPoint point) {
        try {
            Object[] args = point.getArgs();
            if (args != null && args.length > 0 && !(args[0] instanceof jakarta.servlet.ServletRequest)) {
                String params = objectMapper.writeValueAsString(args[0]);
                log.setParams(params.length() > 2000 ? params.substring(0, 2000) : params);
            }
        } catch (Exception ignored) {
        }
        operLogService.add(log);
    }
}
