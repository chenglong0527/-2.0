package com.stylefeng.guns.promo.modular.promo.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.google.common.util.concurrent.RateLimiter;
import com.stylefeng.guns.core.exception.GunsException;
import com.stylefeng.guns.core.exception.GunsExceptionEnum;
import com.stylefeng.guns.core.exception.RespException;
import com.stylefeng.guns.core.exception.RespExceptionEnum;
import com.stylefeng.guns.service.promo.vo.RedisStockKeys;
import com.stylefeng.guns.service.cinema.vo.CinemasReqVo;
import com.stylefeng.guns.service.film.vo.BaseRespVo;
import com.stylefeng.guns.service.promo.PromoService;
import com.stylefeng.guns.service.user.beans.UserInfo;
import com.sun.org.apache.xpath.internal.operations.Bool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RestController
@RequestMapping("promo")
@Slf4j
public class PromoController {
    @Reference(interfaceClass = PromoService.class, check = false,retries = 0)
    PromoService promoService;

    @Autowired
    RedisTemplate redisTemplate;

    private ExecutorService executorService;

    private RateLimiter rateLimiter;

    @PostConstruct
    public void init() {
        //创建一个线程池
        executorService = Executors.newFixedThreadPool(100);//拥塞窗口
        rateLimiter = RateLimiter.create(100);
    }

    /**
     * 获得秒杀活动信息
     *
     * @param cinemasReqVo
     * @return
     */
    @RequestMapping("getPromo")
    public BaseRespVo getPromo(CinemasReqVo cinemasReqVo) {
        return promoService.getPromo(cinemasReqVo);
    }

    /**
     * 创建秒杀订单
     *
     * @param
     * @return
     */
    @RequestMapping(value = "createOrder", method = RequestMethod.POST)
    public BaseRespVo createOrder(Integer promoId, Integer amount, String promoToken, HttpServletRequest request) {

        //通过RateLimiter去限流
        //返回的是线程等待时间，
        double acquire = rateLimiter.acquire();
        if (acquire < 0) {
            return BaseRespVo.fail(1, "秒杀失败");
        }

        Integer userId = getUserInfo().getUuid();
        //判断登录是否过期
        if (userId == null) {
            return BaseRespVo.fail(1, "请重新登录");
        }

        //判断订单数是否异常
        if (amount > 10 || amount < 0) {
            return BaseRespVo.fail(999, "订单数非法");
        }

        //判断令牌
        String key = String.format(RedisStockKeys.PROMO_TOKEN + "promoId:%s_userId:%s", promoId, userId);
        Boolean hasKey = redisTemplate.hasKey(key);
        if (!hasKey) {
            return BaseRespVo.fail(1, "秒杀令牌不合法");
        }
        String token = (String) redisTemplate.opsForValue().get(key);
        if (!token.equals(promoToken)) {
            return BaseRespVo.fail(1, "秒杀令牌不合法");
        }
        //下单之前初始化一条库存流水，并把状态设置为初始值  返回这条记录的主键id
        String stockLogId = promoService.createPromoStockLog(promoId, amount);
        if (StringUtils.isBlank(stockLogId)) {
            log.info("下单失败");
            return BaseRespVo.fail(999, "下单失败！");
        }

        Future<Boolean> future = executorService.submit(() -> {
            //下单接口
            //1. 创建订单  2. 扣减库存
            Boolean result = false;
            try {
                result = promoService.createPromoOrderInTransaction(promoId, amount, userId, stockLogId, promoToken);
            } catch (Exception e) {
                e.printStackTrace();
                //return BaseRespVo.fail(1, "下单失败");
                throw new RespException(RespExceptionEnum.PROMO_ORDER_ERROR);
            }
            if (!result) {
                // return BaseRespVo.fail(1, "下单失败");
                throw new RespException(RespExceptionEnum.PROMO_ORDER_ERROR);
            }
            return result;
        });

        Boolean result = false;
        try {
            result = future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (RespException e) {
            e.printStackTrace();
        }

        return BaseRespVo.ok(0, "下单成功");
    }

    /**
     * 刷新缓存库存
     *
     * @param cinemaId
     * @return
     */
    @RequestMapping("publishPromoStock")
    public BaseRespVo publishPromoStock(Integer cinemaId) {
        return promoService.publishPromoStock(cinemaId);
    }


    @RequestMapping(value = "generateToken", method = RequestMethod.GET)
    public BaseRespVo generateToken(@RequestParam(required = true, name = "promoId") Integer promoId) {
        //先判断活动是否存在
        Boolean has = promoService.hasPromo(promoId);
        if (!has) {
            return BaseRespVo.fail(1, "秒杀活动失效");
        }
        //先判断是否已经告罄
        String runOut = (String) redisTemplate.opsForHash().get(RedisStockKeys.STOCK_RUN_OUT, promoId + "");
        if (!StringUtils.isBlank(runOut)) {
            return BaseRespVo.fail(1, "");
        }
        //
        Integer userId = getUserInfo().getUuid();
        String token = promoService.generateToken(promoId, userId);
        if (token == null) {
            return BaseRespVo.fail(1, "令牌获取失败,库存不足！");
        }
        return BaseRespVo.ok(0, token);
    }

    /**
     * 获取请求头中的authorization的user信息
     *
     * @return
     */
    public UserInfo getUserInfo() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String authorization = request.getHeader("Authorization");
        String authToken = authorization.substring(7);
        return (UserInfo) redisTemplate.opsForValue().get(authToken);
    }


}
