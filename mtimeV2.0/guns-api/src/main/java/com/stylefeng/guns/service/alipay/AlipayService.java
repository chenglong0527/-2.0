package com.stylefeng.guns.service.alipay;

import com.stylefeng.guns.service.film.vo.BaseRespVo;

/**
 * @author Da
 * @version 1.0
 * @date 2019/12/02
 * @time 20:50
 */

public interface AlipayService {
    String pay(String orderId);

    BaseRespVo getPayResult(String orderId);
}
