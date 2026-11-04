package com.example.sporty.features.facilities.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.facilities.service.FacilitySyncService;
import com.example.sporty.features.facilities.service.FacilitySyncService.SyncResult;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/facilities")
@RequiredArgsConstructor
public class FacilitySyncController {

    private final FacilitySyncService facilitySyncService;

    @PostMapping("/sync")
    public ResponseEntity<SyncResult> sync() {
        return ResponseEntity.ok(facilitySyncService.syncAll(true));
    }
}