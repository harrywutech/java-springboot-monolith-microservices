package com.skillup.domain.promotionStockLog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionStockLogService {

    @Autowired
    PromotionStockLogRepo promotionLogRepo;

    @Transactional(propagation = Propagation.REQUIRED)
    public PromotionStockLogDomain createPromotionLog(PromotionStockLogDomain domain) {
        promotionLogRepo.createPromotionLog(domain);
        return domain;
    }
    public PromotionStockLogDomain getPromotionDomain(Long orderId, String operationName) {
        return promotionLogRepo.getByOrderIdAndOperation(orderId, operationName);
    }
}
