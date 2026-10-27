package com.skillup.apiPresentation.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommodityOutDto {
  String commodityId;
  String commodityName;
  String description;
  Integer price;
  String imageUrl;
}
