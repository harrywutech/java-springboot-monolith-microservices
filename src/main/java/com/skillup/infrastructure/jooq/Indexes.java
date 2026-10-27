package com.skillup.infrastructure.jooq;

import com.skillup.infrastructure.jooq.tables.Commodity;
import com.skillup.infrastructure.jooq.tables.Order;
import com.skillup.infrastructure.jooq.tables.Promotion;
import com.skillup.infrastructure.jooq.tables.PromotionLog;

import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;

@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Indexes {
    public static final Index COMMODITY_UNI_COMMODITY_NAME = Internal.createIndex(DSL.name("uni_commodity_name"), Commodity.COMMODITY, new OrderField[] { Commodity.COMMODITY.COMMODITY_NAME }, false);
    public static final Index PROMOTION_IDX_PROMOTION_COMMODITY_ID = Internal.createIndex(DSL.name("idx_promotion_commodity_id"), Promotion.PROMOTION, new OrderField[] { Promotion.PROMOTION.COMMODITY_ID }, false);
    public static final Index PROMOTION_IDX_PROMOTION_START_TIME = Internal.createIndex(DSL.name("idx_promotion_start_time"), Promotion.PROMOTION, new OrderField[] { Promotion.PROMOTION.START_TIME }, false);
    public static final Index PROMOTION_IDX_PROMOTION_END_TIME = Internal.createIndex(DSL.name("idx_promotion_end_time"), Promotion.PROMOTION, new OrderField[] { Promotion.PROMOTION.END_TIME }, false);
    public static final Index PROMOTION_LOG_IDX_ORDER_NUMBER = Internal.createIndex(DSL.name("idx_order_number"), PromotionLog.PROMOTION_LOG, new OrderField[] { PromotionLog.PROMOTION_LOG.ORDER_NUMBER }, false);
    public static final Index PROMOTION_LOG_IDX_USER_ID = Internal.createIndex(DSL.name("idx_user_id"), PromotionLog.PROMOTION_LOG, new OrderField[] { PromotionLog.PROMOTION_LOG.USER_ID }, false);
    public static final Index ORDER_IDX_USER_ID = Internal.createIndex(DSL.name("idx_user_id"), Order.ORDER, new OrderField[] { Order.ORDER.USER_ID }, false);
}
