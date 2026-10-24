package com.example.employment.backend.common;

public class NotFoundException extends RuntimeException {
    public NotFoundException() {
        super("资源不存在");
    }
}
