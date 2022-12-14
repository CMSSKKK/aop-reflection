package com.example.aopreflection.aspect;

import com.example.aopreflection.service.MemberAccessInfo;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

//@Aspect
//@Component
@Slf4j
public class PermissionParametersAspect {

    private static final String LOGIN_ID_PARAM = "loginMemberId";
    private static final String ACCESS_NUMBER_PARAM = "accessNumber";

    @Before("@annotation(com.example.aopreflection.aspect.RequiredPermission)")
    public void validateMemberLevel(JoinPoint joinPoint) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        RequiredPermission requiredPermission = signature.getMethod().getAnnotation(RequiredPermission.class);

        Object[] args = joinPoint.getArgs();
        String[] parameterNames = signature.getParameterNames();
        Class<?>[] parameterTypes = signature.getParameterTypes();

        for (int i = 0; i < args.length; i++) {
            log.info("args[{}] = {}", i, args[i].toString());
        }
        log.info("====================================");
        for (int i = 0; i < parameterNames.length; i++) {
            log.info("parametersNames[{}] = {}", i, parameterNames[i]);
        }
        log.info("====================================");
        for (int i = 0; i < parameterTypes.length; i++) {
            log.info("parameterTypes[{}] = {}", i, parameterTypes[i].toString());
        }

        MemberAccessInfo memberAccessInfo = getMemberAccessInfo(args, parameterNames, parameterTypes);
        log.info(memberAccessInfo.toString());
        MemberLevel memberLevel = requiredPermission.requiredLevel();
        log.info("required level = {}", requiredPermission.requiredLevel());
        // 권한 체크

    }

    private MemberAccessInfo getMemberAccessInfo(Object[] args, String[] parameterNames, Class[] parameterTypes) {
        Object loginId = null;
        Object accessNumber = null;

        for (int i = 0; i < parameterNames.length; i++) {
           if (parameterNames[i].equals(LOGIN_ID_PARAM) && parameterTypes[i].equals(Long.class) && loginId == null) {
                loginId = args[i];
           }
           if (parameterNames[i].equals(ACCESS_NUMBER_PARAM) && parameterTypes[i].equals(Long.class) && accessNumber == null) {
               accessNumber = args[i];
           }
        }
        checkNull(loginId, accessNumber);

        return new MemberAccessInfo((Long) loginId, (Long) accessNumber);
    }

    private void checkNull(Object arg1, Object arg2) {
        if(arg1 == null || arg2 == null) {
            throw new NullPointerException("파라미터가 null 입니다");
        }
    }

}
