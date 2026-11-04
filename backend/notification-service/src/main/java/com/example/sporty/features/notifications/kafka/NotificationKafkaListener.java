package com.example.sporty.features.notifications.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.sporty.features.notifications.service.NotificationService;

import lombok.RequiredArgsConstructor;

@Component 
@RequiredArgsConstructor 
public class NotificationKafkaListener {
    private final NotificationService notificationService;

@KafkaListener(topics = "match.withdrawal.failed")
public void onWithdrawalFailed(ConsumerRecord<String, String> record) {
    notificationService.sendNotification(record.topic(), record.value());
}

@KafkaListener(topicPattern = ".*\\.DLT")
public void onDeadLetter(ConsumerRecord<String, String> record) {
    notificationService.sendNotification(record.topic(), record.value());
}
    
}
