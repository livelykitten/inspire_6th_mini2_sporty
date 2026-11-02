package com.example.sporty.features.facilities.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.exception.matches.ServiceNotFoundException;
import com.example.sporty.features.commons.filter.JwtAuthenticationFilter;
import com.example.sporty.features.facilities.domain.dto.ServiceDetailResponseDto;
import com.example.sporty.features.facilities.domain.dto.ServiceRequestDto;
import com.example.sporty.features.facilities.domain.dto.ServiceResponseDto;
import com.example.sporty.features.facilities.service.FacilityQueryService;

@WebMvcTest(FacilityServiceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(
        properties = "jwt.secret=test-only-signing-secret-with-at-least-32-bytes"
)
class FacilityServiceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FacilityQueryService facilityQueryService;

    @Test
    @DisplayName("FC-01: 인증 없이 지역과 시설 종류로 목록을 조회한다")
    void getServicesWithSearchConditions() throws Exception {
        ServiceResponseDto response = ServiceResponseDto.builder()
                .serviceId(1L)
                .serviceName("응봉공원 테니스장")
                .serviceType("테니스장")
                .region("성동구")
                .locationName("응봉공원")
                .contact("02-1234-5678")
                .status("접수중")
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(19, 0))
                .isFree(false)
                .url("https://example.com/reservation")
                .reservationDeadlineAt(
                        LocalDateTime.of(2026, 12, 31, 17, 0)
                )
                .build();

        when(facilityQueryService.getFacilities(any(ServiceRequestDto.class)))
                .thenReturn(List.of(response));

        mvc.perform(get("/api/services")
                        .param("region", "성동구")
                        .param("serviceType", "테니스장"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceId")
                        .value(1))
                .andExpect(jsonPath("$[0].locationName")
                        .value("응봉공원"))
                .andExpect(jsonPath("$[0].serviceType")
                        .value("테니스장"))
                .andExpect(jsonPath("$[0].region")
                        .value("성동구"))
                .andExpect(jsonPath("$[0].isFree")
                        .value(false));

        verify(facilityQueryService).getFacilities(
                argThat(request ->
                        "성동구".equals(request.getRegion())
                                && "테니스장".equals(
                                        request.getServiceType()
                                )
                )
        );
    }
    @Test
    @DisplayName("FC-01: 시간 형식이 잘못되면 400과 안내 메시지를 반환한다")
    void getServicesWithInvalidTime() throws Exception {
        mvc.perform(get("/api/services")
                        .param("startTime", "25:99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_CONDITION"))
                .andExpect(jsonPath("$.message")
                        .value("검색 조건 형식이 올바르지 않습니다. 시간은 HH:mm 형식으로 입력해주세요."));

        verifyNoInteractions(facilityQueryService);
    }

    @Test
    @DisplayName("FC-02: DB 서비스 ID로 상세 정보를 조회한다")
    void getServiceDetail() throws Exception {
        ServiceDetailResponseDto response = ServiceDetailResponseDto.builder()
                .id(1L)
                .serviceId("S251121100349891778")
                .status("접수중")
                .serviceName("응봉공원 테니스장")
                .paymentMethod("유료")
                .locationName("응봉공원")
                .serviceType("테니스장")
                .region("성동구")
                .build();

        when(facilityQueryService.getFacility(1L)).thenReturn(response);

        mvc.perform(get("/api/services/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.serviceId")
                        .value("S251121100349891778"))
                .andExpect(jsonPath("$.status").value("접수중"))
                .andExpect(jsonPath("$.serviceName")
                        .value("응봉공원 테니스장"))
                .andExpect(jsonPath("$.paymentMethod").value("유료"))
                .andExpect(jsonPath("$.locationName").value("응봉공원"));

        verify(facilityQueryService).getFacility(1L);
    }

    @Test
    @DisplayName("FC-02: 요청 ID가 숫자가 아니면 400을 반환한다")
    void getServiceDetailWithInvalidId() throws Exception {
        mvc.perform(get("/api/services/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SERVICE_ID"));

        verifyNoInteractions(facilityQueryService);
    }

    @Test
    @DisplayName("FC-02: 존재하지 않는 서비스이면 404를 반환한다")
    void getMissingServiceDetail() throws Exception {
        when(facilityQueryService.getFacility(999999L))
                .thenThrow(new ServiceNotFoundException());

        mvc.perform(get("/api/services/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("체육서비스를 찾을 수 없습니다."));
    }
}
