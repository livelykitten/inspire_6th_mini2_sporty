package com.example.sporty.features.ai.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

// AI 프롬프트에 넣을 날짜표.
// AI가 날짜를 직접 계산하면 자주 틀리므로 코드에서 만들어 준다.
public final class PromptDateTable {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int CALENDAR_DAYS = 14;

    private PromptDateTable() {
    }

    public static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    // "이번 주말: 2026-09-26 ~ 2026-09-27" + 오늘부터 14일간의 날짜 목록
    public static String of(LocalDate today) {
        return "이번 주말: " + thisWeekend(today) + "\n" + calendar(today);
    }

    // 오늘부터 14일간의 날짜와 요일 목록
    private static String calendar(LocalDate today) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CALENDAR_DAYS; i++) {
            LocalDate date = today.plusDays(i);
            sb.append("- ").append(date).append(" (").append(dayName(date)).append(")");
            if (i == 0) sb.append(" 오늘");
            if (i == 1) sb.append(" 내일");
            sb.append("\n");
        }
        return sb.toString();
    }

    // 토~일. 오늘이 일요일이면 오늘 하루만
    private static String thisWeekend(LocalDate today) {
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return today + " ~ " + today;
        }
        LocalDate saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        return saturday + " ~ " + saturday.plusDays(1);
    }

    private static String dayName(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
    }
}
