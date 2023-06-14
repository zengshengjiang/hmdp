package com.hmdp;

import cn.hutool.db.Session;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
@SpringBootTest
public class TestCacheClient
{
    @Resource
    private CacheClient cacheClient;
    
    @Resource
    private IShopService shopService;
    
    /**
     * 执行hmdp前要先将数据存储到redis
     * @throws Exception
     */
    @Test
    void test() throws Exception{
            Shop shop = shopService.getById(1L);
            cacheClient.setWithLogicExpire(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.MINUTES);
 
    }
}
