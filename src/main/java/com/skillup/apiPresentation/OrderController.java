package com.skillup.apiPresentation;

import com.skillup.apiPresentation.dto.in.OrderInDto;
import com.skillup.apiPresentation.dto.in.OrderStatusInDto;
import com.skillup.apiPresentation.dto.out.OrderOutDto;
import com.skillup.apiPresentation.util.SkillUpCommon;
import com.skillup.apiPresentation.util.SnowFlake;
import com.skillup.application.order.OrderApplication;
import com.skillup.domain.order.OrderDomain;
import com.skillup.domain.order.OrderService;
import com.skillup.domain.order.util.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Objects;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    SnowFlake snowFlake;

    @Autowired
    OrderApplication orderApplication;

    @Autowired
    OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderOutDto> createOrder(@Valid @RequestBody OrderInDto orderInDto) {
        OrderDomain domain = orderApplication.createOrder(toDomain(orderInDto));
        if (OrderStatus.OUT_OF_STOCK.equals(domain.getOrderStatus()) || OrderStatus.ITEM_ERROR.equals(domain.getOrderStatus())) {
            return ResponseEntity.status(SkillUpCommon.BAD_REQUEST).body(toOutDto(domain));
        }
        return ResponseEntity.status(SkillUpCommon.SUCCESS).body(toOutDto(domain));
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<OrderOutDto> getOrderById(@PathVariable("id") Long id) {
        OrderDomain domain = orderService.getOrderById(id);
        if (Objects.isNull(domain)) {
            return ResponseEntity.status(SkillUpCommon.BAD_REQUEST).body(null);
        }
        return ResponseEntity.status(SkillUpCommon.SUCCESS).body(toOutDto(domain));
    }

    @PatchMapping("/pay/id/{id}")
    public ResponseEntity<OrderOutDto> payOrder(@PathVariable("id") Long id) {
        OrderDomain domain = orderApplication.payOrder(id);
        if (Objects.isNull(domain)) {
            return ResponseEntity.status(SkillUpCommon.BAD_REQUEST).body(null);
        }
        return ResponseEntity.status(SkillUpCommon.SUCCESS).body(toOutDto(domain));
    }

    @PatchMapping("/pay")
    public ResponseEntity<OrderOutDto> payFromPage(@RequestBody OrderStatusInDto in) {
        if (in.getOrderNumber() == null || !Integer.valueOf(1).equals(in.getExistStatus())
                || !Integer.valueOf(2).equals(in.getExpectStatus())) {
            return ResponseEntity.badRequest().build();
        }
        return payOrder(in.getOrderNumber());
    }

    private OrderDomain toDomain(OrderInDto in) {
        return OrderDomain.builder()
                .orderNumber(snowFlake.nextId())
                .userId(in.getUserId())
                .promotionId(in.getPromotionId())
                .promotionName(in.getPromotionName())
                .orderAmount(in.getOrderAmount())
                .orderStatus(OrderStatus.READY)
                .build();
    }

    private OrderOutDto toOutDto(OrderDomain d) {
        return OrderOutDto.builder()
                .orderNumber(String.valueOf(d.getOrderNumber()))
                .userId(d.getUserId())
                .promotionId(d.getPromotionId())
                .promotionName(d.getPromotionName())
                .orderAmount(d.getOrderAmount())
                .orderStatus(d.getOrderStatus().code)
                .createTime(d.getCreateTime())
                .payTime(d.getPayTime())
                .build();
    }
}
