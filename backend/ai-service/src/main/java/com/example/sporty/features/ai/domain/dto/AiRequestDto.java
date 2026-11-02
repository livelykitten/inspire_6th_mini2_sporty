package com.example.sporty.features.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter 
@Setter 
@ToString 
@NoArgsConstructor 
@AllArgsConstructor 
public class AiRequestDto {
    
    @NotBlank (message = "요청 내용을 입력해주세요.")
    @Size (max = 500, message = "요청은 500자 이하로 입력해주세요.")
    private String prompt;
}
