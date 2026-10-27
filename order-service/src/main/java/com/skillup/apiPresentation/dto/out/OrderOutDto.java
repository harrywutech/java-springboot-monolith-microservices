package com.skillup.apiPresentation.dto.out;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderOutDto {
  private String orderNumber;

  // -2 promotion invalid, -1:insufficient stock; order status: 0 pending creation, 1 awaiting payment, 2 paid, 3 expired or invalid
  private Integer orderStatus;

  private String promotionId;

  private String promotionName;

  private String userId;

  private Integer orderAmount;

  private LocalDateTime createTime;

  private LocalDateTime payTime;
}
