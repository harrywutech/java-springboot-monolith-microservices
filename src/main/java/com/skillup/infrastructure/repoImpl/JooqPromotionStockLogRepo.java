package com.skillup.infrastructure.repoImpl;

import com.skillup.domain.promotionStockLog.PromotionStockLogDomain;
import com.skillup.domain.promotionStockLog.PromotionStockLogRepo;
import com.skillup.domain.util.OperationName;
import com.skillup.infrastructure.jooq.tables.PromotionLog;
import com.skillup.infrastructure.jooq.tables.records.PromotionLogRecord;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqPromotionStockLogRepo implements PromotionStockLogRepo {
    @Autowired
    DSLContext dslContext;

    private static final PromotionLog PLT = new PromotionLog();

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void createPromotionLog(PromotionStockLogDomain promotionLogDomain) {
        dslContext.executeInsert(toRecord(promotionLogDomain));
    }

    @Override
    // @Transactional(propagation = Propagation.REQUIRED)
    public PromotionStockLogDomain getByOrderIdAndOperation(Long orderId, String operationName) {
        return dslContext.selectFrom(PLT).where(PLT.ORDER_NUMBER.eq(orderId).and(PLT.OPERATION_NAME.eq(operationName)))
                .fetchOptional(this::toDomain).orElse(null);
    }

    public PromotionStockLogDomain toDomain(PromotionLogRecord promotionLogRecord) {
        return PromotionStockLogDomain.builder()
                .orderNumber(promotionLogRecord.getOrderNumber())
                .promotionId(promotionLogRecord.getPromotionId())
                .userId(promotionLogRecord.getUserId())
                .operationName(OperationName.valueOf(promotionLogRecord.getOperationName()))
                .createTime(promotionLogRecord.getCreateTime())
                .build();
    }

    public PromotionLogRecord toRecord(PromotionStockLogDomain promotionStockLogDomain) {
        return new PromotionLogRecord(
                promotionStockLogDomain.getOrderNumber(),
                promotionStockLogDomain.getUserId(),
                promotionStockLogDomain.getPromotionId(),
                promotionStockLogDomain.getOperationName().toString(),
                promotionStockLogDomain.getCreateTime()
        );
    }
}
