package com.skillup.domain.commodity;

public interface CommodityRepository {
    void createCommodity(CommodityDomain commodityDomain);
    CommodityDomain getCommodityById(String id);
}
