package com.example.sporty.features.facilities.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.sporty.features.facilities.service.FacilitySyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class FacilityStartupSyncRunner implements ApplicationRunner {

    private final FacilitySyncService facilitySyncService;

    @Value("${facility.sync.on-startup:true}")
    private boolean syncOnStartup;

    @Override
    public void run(ApplicationArguments args) {
        if (!syncOnStartup) {
            log.info("facility.sync.on-startup=false, 기동 시 동기화를 건너뜁니다.");
            return;
        }
        try {
            log.info("애플리케이션 시작 시 체육시설 동기화를 시작합니다.");
            facilitySyncService.syncAll();
            log.info("체육시설 동기화가 완료되었습니다.");
        } catch (Exception e) {
            log.error("기동 시 시설 동기화 중 오류가 발생했습니다.", e);
        }
    }
}