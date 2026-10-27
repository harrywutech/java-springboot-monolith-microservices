package com.skillup.apiPresentation;

import com.skillup.application.performance.ReservationApplication;
import com.skillup.domain.order.OrderDomain;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReservationController {
  private final ReservationApplication application;

  @PostMapping("/internal/reservations")
  public OrderDomain reserve(@RequestBody OrderDomain order) {
    return application.reserve(order);
  }
}
