package com.example.aopreflection.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AccessServiceTest {

    @Autowired
    private ParametersAccessService accessService;

    private final Long loginMemberId = 1L;
    private final Long accessNumber = 1L;

    @Test
    void test() {
        assertThat(accessService.readInfos(loginMemberId, accessNumber)).isEqualTo("1가 1번 data에 READ 권한이 필요한 접근을 했어요.");
        assertThat(accessService.maintainInfos(loginMemberId, accessNumber)).isEqualTo("1가 1번 data에 MAINTAIN 권한이 필요한 접근을 했어요.");
        assertThat(accessService.hostInfos(loginMemberId, accessNumber)).isEqualTo("1가 1번 data에 HOST 권한이 필요한 접근을 했어요.");
    }
}
