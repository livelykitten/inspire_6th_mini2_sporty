package com.example.sporty.features.openapi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.sporty.features.openapi.domain.dto.SeoulFacilityResponseDto;
import com.example.sporty.features.openapi.domain.dto.SeoulFacilityResponseDto.ReservationSport;
import com.example.sporty.features.openapi.domain.dto.SeoulFacilityResponseDto.Row;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 서울시 공공서비스예약 체육시설 API 호출을 담당한다.
 * API 페이지를 순회해 동기화에 사용할 전체 원본 데이터를 반환한다.
 */
@Component
public class SeoulFacilityApiClient {

    private static final int PAGE_SIZE = 100;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String SUCCESS_CODE = "INFO-000";
    private static final String NO_DATA_CODE = "INFO-200";

    private final WebClient webClient;
    private final String serviceKey;
    private final String dataType;
    private final String serviceName;
    private final ObjectMapper objectMapper;

    public SeoulFacilityApiClient(
            WebClient.Builder webClientBuilder,
            @Value("${facility.openapi.base-url}") String baseUrl,
            @Value("${facility.openapi.service-key}") String serviceKey,
            @Value("${facility.openapi.data-type}") String dataType,
            @Value("${facility.openapi.service-name}") String serviceName, ObjectMapper objectMapper
    ) {
        this.webClient = webClientBuilder.baseUrl(baseUrl)
                    .codecs(configurer ->
                            configurer.defaultCodecs()
                    .maxInMemorySize(10 * 1024 * 1024)
                    )
                    .build();
        this.serviceKey = serviceKey;
        this.dataType = dataType;
        this.serviceName = serviceName;
        this.objectMapper = objectMapper;
    }

    /**
     * 첫 응답의 전체 건수를 기준으로 나머지 페이지까지 조회한다.
     */
    public List<Row> fetchAll() {
        SeoulFacilityResponseDto firstResponse = fetchPage(1, PAGE_SIZE);
        ReservationSport firstPage = validateAndExtract(firstResponse);

        if (firstPage == null) {
            return Collections.emptyList();
        }

        List<Row> rows = new ArrayList<>();
        addRows(rows, firstPage);

        int totalCount = firstPage.getListTotalCount();
        for (int start = PAGE_SIZE + 1; start <= totalCount; start += PAGE_SIZE) {
            int end = Math.min(start + PAGE_SIZE - 1, totalCount);
            ReservationSport page = validateAndExtract(fetchPage(start, end));
            addRows(rows, page);
        }

        return rows;
    }

    private SeoulFacilityResponseDto fetchPage(int start, int end) {
        String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(
                                serviceKey,
                                dataType,
                                serviceName,
                                String.valueOf(start),
                                String.valueOf(end)
                        )
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block(REQUEST_TIMEOUT);
        
        // SeoulFacilityResponseDto response;

        if (response == null) {
            throw new IllegalStateException("서울시 공공데이터 API 응답이 비어 있습니다.");
        }

        try {
            return objectMapper.readValue(
                    response,
                    SeoulFacilityResponseDto.class
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "서울시 공공데이터 API 응답을 JSON으로 변환하지 못했습니다. 응답: "
                            + response,
                    e
            );
        }
    }

    /**
     * 서울시 API의 RESULT 코드를 확인하고 실제 데이터 영역을 꺼낸다.
     */
    private ReservationSport validateAndExtract(
            SeoulFacilityResponseDto response
    ) {
        ReservationSport reservationSport =
                response.getListPublicReservationSport();

        if (reservationSport == null || reservationSport.getResult() == null) {
            throw new IllegalStateException(
                    "서울시 공공데이터 API 응답 형식이 올바르지 않습니다."
            );
        }

        String resultCode = reservationSport.getResult().getCode();
        if (NO_DATA_CODE.equals(resultCode)) {
            return null;
        }

        if (!SUCCESS_CODE.equals(resultCode)) {
            throw new IllegalStateException(
                    "서울시 공공데이터 API 호출 실패: "
                            + reservationSport.getResult().getMessage()
            );
        }

        return reservationSport;
    }

    private void addRows(List<Row> target, ReservationSport page) {
        if (page != null && page.getRow() != null) {
            target.addAll(page.getRow());
        }
    }
}
