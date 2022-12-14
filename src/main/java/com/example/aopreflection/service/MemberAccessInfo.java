package com.example.aopreflection.service;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public class MemberAccessInfo {

    private final Long loginMemberId;
    private final Long accessNumber;

}
