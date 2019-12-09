package com.stylefeng.guns.service.promo;

import com.stylefeng.guns.service.cinema.vo.CinemasReqVo;
import com.stylefeng.guns.service.film.vo.BaseRespVo;

public interface PromoService {
    BaseRespVo getPromo(CinemasReqVo cinemasReqVo);

    BaseRespVo createPromoOrder(Integer promoId, Integer amount, Integer userId, String stockLogId,String promoToken) throws Exception;

    BaseRespVo publishPromoStock(Integer cinemaId);

    String createPromoStockLog(Integer promoId, Integer amount);

    Boolean createPromoOrderInTransaction(Integer promoId, Integer amount, Integer userId, String stockLogId,String promoToken);

    //获取一个token
    String generateToken(Integer promoId, Integer userId);

    //判断秒杀活动是否有效
    Boolean hasPromo(Integer promoId);
}
