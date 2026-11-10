package com.example.employment.backend.midterm.national.service;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
public class QueueConfig {
    @Bean
    public Queue nationalChunks() { return new Queue(QueueSender.QUEUE,true); }
}
