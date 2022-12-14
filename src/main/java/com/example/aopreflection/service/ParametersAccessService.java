package com.example.aopreflection.service;

import com.example.aopreflection.aspect.MemberLevel;
import com.example.aopreflection.aspect.RequiredPermission;
import org.springframework.stereotype.Component;


@Component
public class ParametersAccessService {

    private static String READ_MESSAGE = "%d번 멤버가 %d번 data에 READ 권한이 필요한 접근을 했어요.";
    private static String MAINTAIN_MESSAGE = "%d번 멤버가 %d번 data를 MAINTAIN 권한이 필요한 접근을 했어요.";
    private static String HOST_MESSAGE = "%d번 멤버가 %d번 data를 HOST 권한이 필요한 접근을 했어요.";

    @RequiredPermission(requiredLevel = MemberLevel.READ)
    public String readInfos(Long loginMemberId, Long accessNumber) {
        return String.format(READ_MESSAGE, loginMemberId, accessNumber);
    }

    @RequiredPermission(requiredLevel = MemberLevel.MAINTAIN)
    public String maintainInfos(Long loginMemberId, Long accessNumber) {
        return String.format(MAINTAIN_MESSAGE, loginMemberId, accessNumber);
    }

    @RequiredPermission(requiredLevel = MemberLevel.HOST)
    public String hostInfos(Long loginMemberId, Long accessNumber) {
        return String.format(HOST_MESSAGE, loginMemberId, accessNumber);
    }
}
