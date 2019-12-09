package com.stylefeng.guns.service.promo.vo;

import lombok.Data;

@Data
public class RedisStockKeys {

    //库存前缀
    public static final String STOCKS = "promoStocks_";

    //库存告罄前缀
    public static final String STOCK_RUN_OUT = "promoStocksRunOut_";

    //promoToken前缀
    public static final String PROMO_TOKEN = "promoToken_";

    //秒杀令牌数量限制
    public static final String TOKEN_LIMIT = "tokenTimes_";
}
