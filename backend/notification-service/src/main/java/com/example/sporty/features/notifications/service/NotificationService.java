package com.example.sporty.features.notifications.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class NotificationService {

    private final JavaMailSender javaMailSender;

    @Value ("${spring.mail.username}")
    private String senderEmail;

    @Value("${notification.mail.to}")
    private String adminEmail;
    
    public void sendNotification(String topic, String payload) {
        String subject = "[Sporty] Kafka 장애 알림 - " + topic;

        String body = """
            Kafka 이벤트 알림

            Topic: %s

            Payload:
            %s
            """.formatted(topic, payload);

        SimpleMailMessage message = new SimpleMailMessage();


        message.setFrom(senderEmail);
        message.setTo(adminEmail);
        message.setSubject(subject);
        message.setText(body);

        javaMailSender.send(message);
        
    }
}
