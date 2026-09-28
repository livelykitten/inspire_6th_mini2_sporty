package com.example.sporty.features.facilities.scheduler;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.sporty.config.DemoMatchDataInitializer;
import com.example.sporty.features.facilities.service.FacilitySyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Component
@RequiredArgsConstructor
@Slf4j
public class FacilityStartupSyncRunner implements ApplicationRunner {

    private final FacilitySyncService facilitySyncService;
    private final DemoMatchDataInitializer demoMatchDataInitializer;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("애플리케이션 시작 시 체육시설 동기화를 시작합니다.");

            // 1. 서울시 실제 시설 데이터 생성
            facilitySyncService.syncAll();

            // 2. 실제 시설을 참조하는 데모 매치 생성
            demoMatchDataInitializer.initialize();

            log.info("초기 데이터 구성이 완료되었습니다.");

        } catch (Exception e) {
            log.error("초기 데이터 구성 중 오류가 발생했습니다.", e);
        }
    }
}