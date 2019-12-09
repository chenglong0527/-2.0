package com.stylefeng.guns.service.promo.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class PromoOrderVo implements Serializable {

    private Integer userId;

    private Integer promoId;

    private Integer amount;

    private String promoToken = "0bb9baa701d646cab2f7210a23f2a0bd";
}
