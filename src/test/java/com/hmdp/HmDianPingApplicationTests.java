package com.hmdp;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import io.lettuce.core.api.async.RedisGeoAsyncCommands;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_TYPE_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    
    @Resource
    private IShopService shopService;
    
    @Resource
    private IShopTypeService shopTypeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //开始时间戳
    private static final long BEGIN_TIMESTAMP=1640995200L;
    /**
     * 序列号位数
     */
    private static final int COUNT_BITS=32;
    
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

    @Test
    void test3(){
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //    生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:"+"shop" + ":" + date);
        System.out.println(count);
        System.out.println(timestamp );
        System.out.println(timestamp << COUNT_BITS);
        System.out.println(timestamp << COUNT_BITS | count);
        
    }

    @Test
    void loadShopData(){
        List<Shop> list = shopService.list();
        
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            
            String key =SHOP_GEO_KEY+typeId;
            List<Shop> value = entry.getValue();
            
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            for (Shop shop : value) {
                //stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }

}
