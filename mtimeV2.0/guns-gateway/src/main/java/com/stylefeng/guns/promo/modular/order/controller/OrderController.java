package com.stylefeng.guns.promo.modular.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.stylefeng.guns.promo.modular.auth.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import com.stylefeng.guns.service.cinema.CinemaService;
import com.stylefeng.guns.service.film.vo.BaseRespVo;
import com.stylefeng.guns.service.order.OrderService;
import com.stylefeng.guns.service.user.beans.UserInfo;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("order")
public class OrderController {

    @Autowired
    JwtTokenUtil jwtTokenUtil;

    @Reference(interfaceClass = CinemaService.class, check = false)
    CinemaService cinemaService;

    @Reference(interfaceClass = OrderService.class, check = false)
    OrderService orderService;

    @Autowired
    RedisTemplate redisTemplate;

    /**
     * 买票
     * @param fieldId 场次id
     * @param soldSeats 销售的座位
     * @param seatsName 销售座位的名字
     * @return
     */
    @RequestMapping("buyTickets")
    public BaseRespVo buyTickets(Integer fieldId, String soldSeats, String seatsName) {
        Boolean trueSeats = cinemaService.isTrueSeats(fieldId, soldSeats);
        if (!trueSeats) {
            BaseRespVo<Object> respVo = new BaseRespVo<>();
            respVo.setStatus(1);
            respVo.setMsg("座位不存在");
            return respVo;
        }
        if(orderService.isSoldSeats(fieldId, soldSeats)){
            BaseRespVo<Object> respVo = new BaseRespVo<>();
            respVo.setStatus(1);
            respVo.setMsg("所选座位已售出");
            return respVo;
        }
        UserInfo userInfo = getUserInfo();
        return orderService.buyTickets(fieldId, soldSeats, seatsName, userInfo.getUuid());
    }

    /**
     * 获取订单信息
     * @param nowPage
     * @param pageSize
     * @return
     */
    @RequestMapping("getOrderInfo")
    public BaseRespVo getOrderInfo(Integer nowPage, Integer pageSize) {
        UserInfo userInfo = getUserInfo();
        return orderService.getOrderInfo(nowPage,pageSize,userInfo.getUuid());
    }

    /**
     * 获取请求头中的authorization的user信息
     * @return
     */
    public UserInfo getUserInfo() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String authorization = request.getHeader("Authorization");
        String authToken = authorization.substring(7);
        return (UserInfo) redisTemplate.opsForValue().get(authToken);
    }

}
