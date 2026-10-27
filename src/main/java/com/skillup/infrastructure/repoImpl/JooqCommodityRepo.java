package com.skillup.infrastructure.repoImpl;

import com.skillup.domain.commodity.CommodityDomain;
import com.skillup.domain.commodity.CommodityRepository;
import com.skillup.infrastructure.jooq.tables.Commodity;
import com.skillup.infrastructure.jooq.tables.records.CommodityRecord;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class JooqCommodityRepo implements CommodityRepository {
    @Autowired
    DSLContext dslContext;
    public static final Commodity C_T = new Commodity();
    @Override
    public void createCommodity(CommodityDomain commodityDomain) {
        dslContext.executeInsert(toRecord(commodityDomain));
    }

    @Override
    public CommodityDomain getCommodityById(String id) {
        return dslContext.selectFrom(C_T)
                .where(C_T.COMMODITY_ID.eq(id)).fetchOptional(this::toDomain).orElse(null);
    }

    private CommodityRecord toRecord(CommodityDomain commodityDomain) {
        CommodityRecord commodityRecord = new CommodityRecord();
        commodityRecord.setCommodityId(commodityDomain.getCommodityId());
        commodityRecord.setCommodityName(commodityDomain.getCommodityName());
        commodityRecord.setDescription(commodityDomain.getDescription());
        commodityRecord.setPrice(commodityDomain.getPrice());
        commodityRecord.setImageUrl(commodityDomain.getImageUrl());
        return commodityRecord;
    }
    private CommodityDomain toDomain(CommodityRecord CommodityRecord) {
        return CommodityDomain.builder()
                .commodityId(CommodityRecord.getCommodityId())
                .commodityName(CommodityRecord.getCommodityName())
                .description(CommodityRecord.getDescription())
                .price(CommodityRecord.getPrice())
                .imageUrl(CommodityRecord.getImageUrl())
                .build();
    }
}
