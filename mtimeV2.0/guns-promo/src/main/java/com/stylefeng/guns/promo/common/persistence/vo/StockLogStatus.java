package com.stylefeng.guns.promo.common.persistence.vo;

public enum StockLogStatus {
    INIT(1),
    SUCCESS(2),
    FAIL(3);
    private Integer status;

    StockLogStatus(Integer status) {
        this.status = status;
    }

    public Integer getStatus() {
        return status;
    }
}
