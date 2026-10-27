package com.skillup.apiPresentation.dto.in;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderInDto {
  @NotBlank(message = "promotionId is can not be null!")
  private String promotionId;

  private String promotionName;

  @NotBlank(message = "userId is required")
  private String userId;

  private Integer orderAmount;
}
