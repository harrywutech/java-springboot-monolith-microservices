package com.skillup.apiPresentation.dto.in;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PromotionInDto {
  private String promotionName;

  private String commodityId;

  private Integer originalPrice;

  private Integer promotionalPrice;

  private LocalDateTime startTime;

  private LocalDateTime endTime;

  /*
  0 not start, 1 active, 2 end
   */
  private Integer status;

  private Long totalStock;

  private Long availableStock;

  private Long lockStock;

  private String imageUrl;
}
