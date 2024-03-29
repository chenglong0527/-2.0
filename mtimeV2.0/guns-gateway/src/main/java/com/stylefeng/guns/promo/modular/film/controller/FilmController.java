package com.stylefeng.guns.promo.modular.film.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.stylefeng.guns.promo.common.guava.CacheService;
import com.stylefeng.guns.service.film.FilmService;
import com.stylefeng.guns.service.film.vo.BaseRespVo;
import com.stylefeng.guns.service.film.vo.FilmDetailVo;
import com.stylefeng.guns.service.film.vo.FilmReqVo3;
import com.stylefeng.guns.service.film.vo.FilmsVo;
import com.stylefeng.guns.service.film.vo.response.BaseVoDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Da
 * @version 1.0
 * @date 2019/11/29
 * @time 9:20
 */

@RestController
public class FilmController {

    @Reference(interfaceClass = FilmService.class, check = false)
    FilmService filmService;

    @Value("${meeting.film.preImg}")
    private String preImg;

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    private CacheService cacheService;


    private static final String INDEX_PREFIX = "index_prefix_";

    @RequestMapping("/film/getFilms")
    public BaseVoDetail queryFilms(FilmReqVo3 filmReqVo) {
        BaseVoDetail baseVoDetail = new BaseVoDetail();
        FilmsVo films = filmService.getFilms(filmReqVo);
        if (films == null) {
            baseVoDetail.setStatus(1);
            baseVoDetail.setMsg("查询失败，无影片可加载");
            return baseVoDetail;
        }
        baseVoDetail.setImgPre(preImg);
        baseVoDetail.setNowPage(films.getNowPage());
        baseVoDetail.setTotalPage(films.getTotalPage());
        baseVoDetail.setData(films.getData());
        baseVoDetail.setStatus(0);
        return baseVoDetail;
    }


    @RequestMapping("/film/films/{searchParam}")
    public BaseVoDetail queryFilmInfo(Integer searchType,
                                      @PathVariable("searchParam") String searchParam) {
        FilmDetailVo filmDetail = filmService.getFilmDetail(searchType, searchParam);
        BaseVoDetail baseVoDetail = new BaseVoDetail();
        if (filmDetail == null) {
            baseVoDetail.setStatus(1);
            baseVoDetail.setMsg("查询失败，无影片可加载");
            return baseVoDetail;
        }
        baseVoDetail.setImgPre(preImg);
        baseVoDetail.setData(filmDetail);
        baseVoDetail.setStatus(0);
        return baseVoDetail;
    }


    @RequestMapping("film/getIndex")
    public BaseRespVo getIndex() {
        Object cache = cacheService.get(INDEX_PREFIX);
        if (cache != null) {
            return (BaseRespVo) cache;
        }

        Object reids = redisTemplate.opsForValue().get(INDEX_PREFIX);
        if (reids != null) {
            cacheService.put(INDEX_PREFIX, reids);
            return (BaseRespVo) reids;
        }
        Map<String, Object> map = filmService.getIndex();
        BaseRespVo ok = BaseRespVo.ok(map);
        ok.setImgPre("http://img.meetingshop.cn/");
        redisTemplate.opsForValue().set(INDEX_PREFIX, ok);
        redisTemplate.expire(INDEX_PREFIX, 5, TimeUnit.MINUTES);
        cacheService.put(INDEX_PREFIX, ok);
        return ok;
    }

    @RequestMapping("film/getConditionList")
    public BaseRespVo getConditionList(Integer catId, Integer sourceId, Integer yearId) {
        Map<String, Object> condition = filmService.getCondition(catId, sourceId, yearId);

        BaseRespVo ok = BaseRespVo.ok(condition);
        ok.setImgPre("http://img.meetingshop.cn/");
        return ok;
    }
}
