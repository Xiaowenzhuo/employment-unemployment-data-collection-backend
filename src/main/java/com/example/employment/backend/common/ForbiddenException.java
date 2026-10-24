package com.example.employment.backend.common;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException() {
        super("无权访问");
    }
}
