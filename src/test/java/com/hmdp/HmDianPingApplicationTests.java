package com.hmdp;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private IShopTypeService shopTypeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Test
    void test() {
        String key = SHOP_TYPE_KEY;
        //商铺类型在redis中不存在
        List<ShopType> typeList = shopTypeService.query().orderByAsc("sort").list();
        //判断数据库中是否有数据
        if (typeList != null && !typeList.isEmpty()) {
            //有数据,将数据写入redis并返回
            //将ShopType类型转化你为String类型
            List<String> list = JSONUtil.toList(JSONUtil.toJsonStr(typeList), String.class);
            System.out.println(list);
            //存入redis
            stringRedisTemplate.opsForList().rightPushAll(key, list);
        }
    }
    
    @Test
    void test2(){
        String key = SHOP_TYPE_KEY;
        List<String> shopList = stringRedisTemplate.opsForList().range(key, 0, -1);
        //商铺类型在redis中存在
        if (shopList != null && !shopList.isEmpty()) {
            List<ShopType> list = JSONUtil.toList(shopList.toString(), ShopType.class);
            for (ShopType shopType : list) {
                System.out.println(shopType);
            }
            return;
        }
        //商铺类型在redis中不存在
        List<ShopType> typeList = shopTypeService.query().orderByAsc("sort").list();
        //判断数据库中是否有数据
        if (typeList != null && !typeList.isEmpty()) {
            //有数据,将数据写入redis并返回
            //将ShopType类型转化你为String类型
            List<String> list = JSONUtil.toList(JSONUtil.toJsonStr(typeList), String.class);
            for (String s : list) {
                System.out.println(s);
            }
            //存入redis
            stringRedisTemplate.opsForList().rightPushAll(key, list);
        }
    }
}
