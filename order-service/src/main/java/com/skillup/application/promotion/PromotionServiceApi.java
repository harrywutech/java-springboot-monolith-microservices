package com.skillup.application.promotion;

import com.skillup.domain.order.OrderDomain;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/** Resolve a healthy promotion instance through Consul + Spring Cloud LoadBalancer. */
@FeignClient(name = "step3-promotion")
public interface PromotionServiceApi {
  @PostMapping("/internal/reservations")
  OrderDomain reserve(@RequestBody OrderDomain order);
}
