package com.example.sporty.features.ai.util;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.example.sporty.features.commons.util.SportType;

// AI가 enum에 없는 값(예: SQUASH)을 넘겼을 때 보여줄 안내 문구. 지원 종목은 SportType에서 만든다.
public final class SupportedSports {

    private SupportedSports() {
    }

    public static String unsupportedMessage() {
        String sports = Arrays.stream(SportType.values())
                .map(SportType::getDescription)
                .collect(Collectors.joining(", "));
        return "지원하지 않는 조건이 포함되어 있습니다. 지원 종목: " + sports;
    }
}
