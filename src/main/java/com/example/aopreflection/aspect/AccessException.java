package com.example.aopreflection.aspect;

public class AccessException extends RuntimeException {

    private static final String message = "접근 권한이 없습니다.";

    public AccessException() {
        super(message);
    }
}
