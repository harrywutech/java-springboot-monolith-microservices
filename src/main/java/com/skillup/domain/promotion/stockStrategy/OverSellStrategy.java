package com.skillup.domain.promotion.stockStrategy;

import com.skillup.domain.promotion.PromotionDomain;
import com.skillup.domain.promotion.PromotionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service(value = "oversell")
@Slf4j
public class OverSellStrategy implements StockOperation{

    @Autowired
    PromotionRepository promotionRepository;

    @Override
    public boolean lockStock(String id) {
        log.info("------Over Sell Strategy-----");
        // 1 get available stock, check > 0
        PromotionDomain currentPromotion = promotionRepository.getById(id);
        if (currentPromotion.getAvailableStock() <= 0) {
            return false;
        }
        log.info("----- Current Available Stock is: {} -----", currentPromotion.getAvailableStock());
        // 2 set available stock - 1, set lock stock + 1
        currentPromotion.setAvailableStock(currentPromotion.getAvailableStock() - 1);
        currentPromotion.setLockStock(currentPromotion.getLockStock() + 1);
        log.info("Update available stock to: {}, lock stock to: {} ", currentPromotion.getAvailableStock(), currentPromotion.getLockStock());
        // 3 save domain
        promotionRepository.updatePromotion(currentPromotion);
        return true;
    }

    @Override
    public boolean revertStock(String id) {
        return false;
    }

    @Override
    public boolean deductStock(String id) {
        return false;
    }
}
