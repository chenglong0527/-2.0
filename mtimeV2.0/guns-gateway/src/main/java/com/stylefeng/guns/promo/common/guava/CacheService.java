package com.stylefeng.guns.promo.common.guava;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Component
public class CacheService {
    private Cache cache = null;

    @PostConstruct
    public void init(){
        cache = CacheBuilder.newBuilder()
                .initialCapacity(10) //初始容量
                .maximumSize(100) //最大容量
                .expireAfterWrite(120, TimeUnit.SECONDS) //过期时间
                .build();
    }

    public void put(String key,Object object){
        cache.put(key,object);
    }

    public Object get(String key){
        return cache.getIfPresent(key);
    }
}
