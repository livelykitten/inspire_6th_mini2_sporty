package com.example.sporty.features.openapi.domain.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;

/**
 * 서울시 API JSON 구조를 그대로 받는 외부 연동용 DTO다.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeoulFacilityResponseDto {

    @JsonProperty("ListPublicReservationSport")
    private ReservationSport listPublicReservationSport;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReservationSport {
        @JsonProperty("list_total_count")
        private int listTotalCount;

        @JsonProperty("RESULT")
        private Result result;

        @JsonProperty("row")
        private List<Row> row;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("CODE")
        private String code;

        @JsonProperty("MESSAGE")
        private String message;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {
        @JsonProperty("SVCID") private String svcId;
        @JsonProperty("MINCLASSNM") private String minClassNm;
        @JsonProperty("SVCNM") private String svcNm;
        @JsonProperty("PLACENM") private String placeNm;
        @JsonProperty("AREANM") private String areaNm;
        @JsonProperty("SVCSTATNM") private String svcStatNm;
        @JsonProperty("PAYATNM") private String payAtNm;
        @JsonProperty("SVCURL") private String svcUrl;
        @JsonProperty("RCPTENDDT") private String rcptEndDt;
        @JsonProperty("X") private String x;
        @JsonProperty("Y") private String y;
        @JsonProperty("TELNO") private String telNo;
        @JsonProperty("V_MIN") private String vMin;
        @JsonProperty("V_MAX") private String vMax;
    }
}
