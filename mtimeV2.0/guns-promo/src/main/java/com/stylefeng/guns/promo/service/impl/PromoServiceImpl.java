package com.stylefeng.guns.promo.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.stylefeng.guns.core.exception.GunsException;
import com.stylefeng.guns.core.exception.GunsExceptionEnum;
import com.stylefeng.guns.promo.common.mq.Producer;
import com.stylefeng.guns.promo.common.persistence.dao.MtimePromoMapper;
import com.stylefeng.guns.promo.common.persistence.dao.MtimePromoOrderMapper;
import com.stylefeng.guns.promo.common.persistence.dao.MtimePromoStockMapper;
import com.stylefeng.guns.promo.common.persistence.dao.MtimeStockLogMapper;
import com.stylefeng.guns.promo.common.persistence.model.MtimePromo;
import com.stylefeng.guns.promo.common.persistence.model.MtimePromoOrder;
import com.stylefeng.guns.promo.common.persistence.model.MtimePromoStock;
import com.stylefeng.guns.promo.common.persistence.model.MtimeStockLog;
import com.stylefeng.guns.promo.common.persistence.vo.StockLogStatus;
import com.stylefeng.guns.service.cinema.CinemaService;
import com.stylefeng.guns.service.cinema.vo.CinemaInfoVo;
import com.stylefeng.guns.service.cinema.vo.CinemasReqVo;
import com.stylefeng.guns.service.film.vo.BaseRespVo;
import com.stylefeng.guns.service.promo.PromoService;
import com.stylefeng.guns.service.promo.vo.PromoRespVo;
import com.stylefeng.guns.service.promo.vo.RedisStockKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Service(interfaceClass = PromoService.class)
@Transactional
@Slf4j
public class PromoServiceImpl implements PromoService {

    @Reference(interfaceClass = CinemaService.class, check = false)
    CinemaService cinemaService;

    @Autowired
    MtimePromoMapper mtimePromoMapper;

    @Autowired
    MtimePromoStockMapper mtimePromoStockMapper;

    @Autowired
    MtimePromoOrderMapper mtimePromoOrderMapper;

    @Autowired
    MtimeStockLogMapper stockLogMapper;

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    Producer producer;

    private ExecutorService executorService;

    private static Integer TOKEN_TIMES = 5;

    @PostConstruct
    public void init() {
        //创建一个线程池
        executorService = Executors.newFixedThreadPool(10);
    }

    @Override
    public BaseRespVo getPromo(CinemasReqVo cinemasReqVo) {
        List<MtimePromo> mtimePromos = mtimePromoMapper.selectList(null);
        List<PromoRespVo> promoRespVoList = new ArrayList<>();
        for (MtimePromo mtimePromo : mtimePromos) {
//            EntityWrapper<MtimePromoStock> mtimePromoStockEntityWrapper = new EntityWrapper<>();
//            mtimePromoStockEntityWrapper.eq("promo_id",mtimePromo.getUuid());
            MtimePromoStock mtimePromoStock = mtimePromoStockMapper.selectById(mtimePromo.getUuid());
            Integer cinemaId = mtimePromo.getCinemaId();
            CinemaInfoVo cinema = cinemaService.getCinemaById(cinemaId);
            PromoRespVo promoRespVo = new PromoRespVo();
            BeanUtils.copyProperties(mtimePromo, promoRespVo);
            promoRespVo.setCinemaAddress(cinema.getCinemaAddress());
            promoRespVo.setImgAddress(cinema.getImgAddress());
            promoRespVo.setCinemaName(cinema.getCinemaName());
            promoRespVo.setCinemaId(cinemaId);
            promoRespVo.setStock(mtimePromoStock.getStock());
            promoRespVoList.add(promoRespVo);
        }
        BaseRespVo respVo = new BaseRespVo();
        respVo.setStatus(0);
        respVo.setData(promoRespVoList);
        respVo.setNowPage(String.valueOf(cinemasReqVo.getNowPage()));
        respVo.setTotalPage(String.valueOf((int)Math.ceil((double) promoRespVoList.size()/cinemasReqVo.getPageSize())));
        return respVo;
    }

    /**
     * 创建一个秒杀订单
     *
     * @param promoId
     * @param amount
     * @param userId
     * @param stockLogId
     * @param promoToken
     * @return
     */
    @Override
    public BaseRespVo createPromoOrder(Integer promoId, Integer amount, Integer userId, String stockLogId, String promoToken) throws Exception {
        Integer i = savePromoOrder(promoId, amount, userId, stockLogId, promoToken);

        if (i != 1) {
            //添加订单失败 则将流水的状态改为3
            executorService.submit(() -> {//异步去完成，防止事务回滚
                stockLogMapper.updateStatusById(stockLogId, StockLogStatus.FAIL.getStatus());
            });
            throw new GunsException(GunsExceptionEnum.SERVER_ERROR);
//            return BaseRespVo.fail(999,"系统繁忙，请联系管理员");
        }
        //在redis里添加一个记录，判断是否操作过
        Integer logStatus = (Integer) redisTemplate.opsForValue().get(stockLogId);
        Boolean res = false;
        if (logStatus == null || logStatus != 2) {
            res = decreaseStock(promoId, amount);
        }
        if (!res) {//更新流水状态为失败
            executorService.submit(() -> {//异步去完成，防止事务回滚
                stockLogMapper.updateStatusById(stockLogId, StockLogStatus.FAIL.getStatus());
            });
            throw new GunsException(GunsExceptionEnum.SERVER_ERROR);
//            return BaseRespVo.fail(1,"库存不足");
        }

        //执行本地事务成功  更新库存流水为成功 1
        redisTemplate.opsForValue().set(stockLogId, StockLogStatus.SUCCESS.getStatus());
        redisTemplate.expire(stockLogId,5,TimeUnit.MINUTES);
        stockLogMapper.updateStatusById(stockLogId, StockLogStatus.SUCCESS.getStatus());
        return BaseRespVo.ok(0, "下单成功");
    }

    //减少库存
    private Boolean decreaseStock(Integer promoId, Integer amount) {
        //将缓存中的数据减一
        Long promoStocks = redisTemplate.opsForHash().increment(RedisStockKeys.STOCKS, promoId.toString(), -amount);
        if (promoStocks < 0) {
            log.info("库存不足，promoId:{}", promoId);
            redisTemplate.opsForHash().increment(RedisStockKeys.STOCKS, promoId.toString(), amount);//库存不足则将刚才减少的加回来
            return false;
        }
        if (promoStocks == 0) {
            redisTemplate.opsForHash().put(RedisStockKeys.STOCK_RUN_OUT, promoId + "", "out");
            redisTemplate.expire(RedisStockKeys.STOCK_RUN_OUT, 1, TimeUnit.HOURS);
        }
        return true;
    }

    private Integer savePromoOrder(Integer promoId, Integer amount, Integer userId, String stockLogId, String promoToken) {
        //组装一个order
        MtimePromoOrder promoOrder = buildPromoOrder(promoId, amount, userId, promoToken);
        //往数据库中添加一个订单
        Integer insert = mtimePromoOrderMapper.insert(promoOrder);
        return insert;
    }

    private MtimePromoOrder buildPromoOrder(Integer promoId, Integer amount, Integer userId, String promoToken) {
        MtimePromo mtimePromo = mtimePromoMapper.selectById(promoId);
        MtimePromoOrder mtimePromoOrder = new MtimePromoOrder();
        BeanUtils.copyProperties(mtimePromo, mtimePromoOrder);
        mtimePromoOrder.setUserId(userId);
        mtimePromoOrder.setExchangeCode(promoToken);
        mtimePromoOrder.setCreateTime(new Date());
        mtimePromoOrder.setAmount(amount);
        mtimePromoOrder.setUuid(String.valueOf(UUID.randomUUID()).replaceAll("-", ""));
        return mtimePromoOrder;
    }

    /**
     * 如果数据过期，则新缓存中的数据
     *
     * @param cinemaId
     * @return
     */
    @Override
    public BaseRespVo publishPromoStock(Integer cinemaId) {
        Map stocks = redisTemplate.opsForHash().entries(RedisStockKeys.STOCKS);
        //如果redis中已经过期则更新数据
        if (stocks == null || stocks.isEmpty()) {
            List<MtimePromoStock> mtimePromoStocks = mtimePromoStockMapper.selectList(null);
            for (MtimePromoStock mtimePromoStock : mtimePromoStocks) {
                Integer promoId = mtimePromoStock.getPromoId();
                if (mtimePromoStock.getStock() == 0) {
                    redisTemplate.opsForHash().put(RedisStockKeys.STOCK_RUN_OUT, promoId + "", "out");
                    redisTemplate.expire(RedisStockKeys.STOCK_RUN_OUT, 1, TimeUnit.HOURS);
                }
                redisTemplate.opsForHash().put(RedisStockKeys.STOCKS, promoId.toString(), mtimePromoStock.getStock());
                redisTemplate.expire(RedisStockKeys.STOCKS, 1, TimeUnit.HOURS);

                String key = RedisStockKeys.TOKEN_LIMIT + promoId;
                Integer amount = mtimePromoStock.getStock() * TOKEN_TIMES;
                redisTemplate.opsForValue().set(key, amount);
                redisTemplate.expire(key, 1, TimeUnit.HOURS);
            }

        }
        return BaseRespVo.ok(0, "发布成功！");
//        return BaseRespVo.fail(999,"发布失败！");
    }

    //初始化流水
    @Override
    public String createPromoStockLog(Integer promoId, Integer amount) {
        MtimeStockLog stockLog = new MtimeStockLog();
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        stockLog.setUuid(uuid);
        stockLog.setAmount(amount);
        stockLog.setPromoId(promoId);
        stockLog.setStatus(StockLogStatus.INIT.getStatus());

        Integer insert = stockLogMapper.insert(stockLog);
        return insert == 1 ? uuid : null;
    }

    /**
     * 发送事务型消息
     *
     * @param promoId
     * @param amount
     * @param userId
     * @param stockLogId
     * @return
     */
    @Override
    public Boolean createPromoOrderInTransaction(Integer promoId, Integer amount, Integer userId, String stockLogId, String promoToken) {
        return producer.sendStockMessageIntransaction(promoId, amount, userId, stockLogId, promoToken);
    }

    @Override
    public String generateToken(Integer promoId, Integer userId) {
        //先判断令牌数量是否还有剩余
        String limitKey = RedisStockKeys.TOKEN_LIMIT + promoId;
        Integer remain = (Integer) redisTemplate.opsForValue().get(limitKey);
        if (remain <= 0) {
            return null;
        }
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        String key = String.format(RedisStockKeys.PROMO_TOKEN + "promoId:%s_userId:%s", promoId, userId);
        redisTemplate.opsForValue().set(key, token);
        redisTemplate.expire(key, 5, TimeUnit.MINUTES);
        return token;
    }

    @Override
    public Boolean hasPromo(Integer promoId) {
        MtimePromo mtimePromo = mtimePromoMapper.selectById(promoId);
        if (mtimePromo.getStatus() == 1) {
            return true;
        }
        return false;
    }
}
