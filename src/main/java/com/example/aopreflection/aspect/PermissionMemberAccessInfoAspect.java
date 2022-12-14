package com.example.aopreflection.aspect;

import com.example.aopreflection.service.MemberAccessInfo;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Aspect
@Slf4j
public class PermissionMemberAccessInfoAspect {

    @Before("@annotation(com.example.aopreflection.aspect.RequiredPermission)")
    public void validateMemberLevel(JoinPoint joinPoint) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        RequiredPermission requiredPermission = signature.getMethod().getAnnotation(RequiredPermission.class);
        log.info("required level = {}", requiredPermission.requiredLevel());
        MemberAccessInfo memberAccessInfo = getMemberAccessInfo(joinPoint.getArgs());
        log.info(memberAccessInfo.toString());
    }

    private MemberAccessInfo getMemberAccessInfo(Object[] args) {
        return Arrays.stream(args)
                .filter(a -> a instanceof MemberAccessInfo)
                .map(MemberAccessInfo.class::cast)
                .findFirst()
                .orElseThrow(AccessException::new);
    }

}
