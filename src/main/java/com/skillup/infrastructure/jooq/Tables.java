package com.skillup.infrastructure.jooq;

import com.skillup.infrastructure.jooq.tables.Commodity;
import com.skillup.infrastructure.jooq.tables.Order;
import com.skillup.infrastructure.jooq.tables.Promotion;
import com.skillup.infrastructure.jooq.tables.PromotionLog;
import com.skillup.infrastructure.jooq.tables.User;

@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Tables {
    public static final User         USER          = User.USER;
    public static final Commodity    COMMODITY     = Commodity.COMMODITY;
    public static final Promotion    PROMOTION     = Promotion.PROMOTION;
    public static final PromotionLog PROMOTION_LOG = PromotionLog.PROMOTION_LOG;
    public static final Order        ORDER         = Order.ORDER;
}
