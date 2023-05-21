package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import io.lettuce.core.RedisClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * 商铺方法
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private CacheClient cacheClient;
    
    @Override
    public Result queryById(Long id) {
        //普通方法解决缓存穿透
        //Shop shop = queryWithPassThrough(id);
        Shop shop = cacheClient.
                queryWithPassThrough(
                        CACHE_SHOP_KEY,
                        id,
                        Shop.class,
                        this::getById,
                        CACHE_SHOP_TTL,
                        TimeUnit.MINUTES);
        
        
        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        
        
        //使用逻辑过期解决缓存穿透
        //Shop shop = queryWithLogicalExpire(id);
        //Shop shop = cacheClient.queryWithLogicalExpire(
        //        CACHE_SHOP_KEY,
        //        id,
        //        Shop.class,
        //        this::getById,
        //        CACHE_SHOP_TTL,
        //        TimeUnit.MINUTES
        //);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        
        return Result.ok(shop);
    }
    //使用互斥锁解决缓存击穿
    /*
    public Shop queryWithMutex(Long id) {
        //查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否是空值
        if (shopJson != null) {
            //    返回错误信息
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否获取成功
            if (!isLock) {
                //失败，则休眠并重试
                //模拟重建延时
                Thread.sleep(200);
                return queryWithMutex(id);
            }
            
            
            //成功，更具id查询数据库
            //不存在，根据id查询数据库
            shop = this.getById(id);
            //不存在，返回错误404
            if (shop == null) {
                // 将空数据写入redis
                stringRedisTemplate.opsForValue()
                        .set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //存在，写入redis
            stringRedisTemplate.opsForValue()
                    .set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }
        
        
        return shop;
    }
    */
    //普通方法解决缓存击穿
    /*
    public Shop queryWithPassThrough(Long id) {
        //查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否是空值
        if (shopJson != null) {
            //    返回错误信息
            return null;
        }
        //不存在，根据id查询数据库
        Shop shop = this.getById(id);
        //不存在，返回错误404
        if (shop == null) {
            // 将空数据写入redis
            stringRedisTemplate.opsForValue()
                    .set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在，写入redis
        stringRedisTemplate.opsForValue()
                .set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        
        return shop;
    }
    */
    //线程池
    /*
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
  */
    //使用逻辑过期解决缓存击穿
    /*
    
    public Shop queryWithLogicalExpire(Long id) {
        //查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //不存在
            return null;
        }
        //查询到，需要将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回
            return shop;
        }
        //过期，需要缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //判断是否获取锁成功
        if (isLock) {
            //再次判断redis是否过期，做双重检测
            if (expireTime.isAfter(LocalDateTime.now())) {
                //未过期，直接返回
                return shop;
            }
            //成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 缓存重建
                    this.saveShop2Redis(id, CACHE_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //   释放锁
                    unLock(lockKey);
                }
                
            });
        }
        //失败，返回过期商铺信息
        return shop;
    }
    */
    //获取锁
    /*
   
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        
        return BooleanUtil.isTrue(flag);
    }
    
    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
    **/
    // 缓存重建
    /*
  
    private void saveShop2Redis(Long id, Long expireSeconds) {
        //查询店铺数据
        Shop shop = this.getById(id);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setData(shop);
        //写入redis
        stringRedisTemplate.opsForValue()
                .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
    */
    
    /**
     * 更新商铺信息
     *
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id为空");
            
            
        }
        //更新数据库
        this.update(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok("店铺信息更新成功");
    }
    
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x==null || y==null) {
            // 根据类型分页查询
            Page<Shop> page = this.query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        int from = (current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current*SystemConstants.DEFAULT_PAGE_SIZE;
        String key=SHOP_GEO_KEY+typeId;
        
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        if (results==null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size()<=from) {
            return Result.ok(Collections.emptyList());
        }
        
        //截取from到end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result->
                {
                    //店铺id
                    String shopIdStr = result.getContent().getName();
                    ids.add(Long.valueOf(shopIdStr));
                    //距离
                    Distance distance = result.getDistance();
                    distanceMap.put(shopIdStr,distance);
                });
        String idStr=StrUtil.join(",",ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
