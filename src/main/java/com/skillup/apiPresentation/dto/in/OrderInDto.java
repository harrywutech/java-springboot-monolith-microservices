package com.skillup.apiPresentation.dto.in;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderInDto {
    @NotBlank(message = "promotionId is can not be null!")
    private String promotionId;

    private String promotionName;

    private String userId;

    private Integer orderAmount;
}
