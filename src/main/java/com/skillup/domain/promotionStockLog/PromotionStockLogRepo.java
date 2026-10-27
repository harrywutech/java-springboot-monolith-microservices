package com.skillup.domain.promotionStockLog;

public interface PromotionStockLogRepo {
    void createPromotionLog(PromotionStockLogDomain promotionLogDomain);

    PromotionStockLogDomain getByOrderIdAndOperation(Long orderId, String operationName);
}
