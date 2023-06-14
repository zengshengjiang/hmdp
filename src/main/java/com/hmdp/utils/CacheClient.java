package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.naming.ldap.Rdn;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    
    private final StringRedisTemplate stringRedisTemplate;
    
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    
    
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    
    //缓存重建
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    
    //普通方法解决缓存穿透
    public <R, ID> R queryWithPassThrough(
            String keyPrefix,
            ID id, Class<R> type,
            Function<ID, R> dbFallback,
            Long time, TimeUnit unit) {
        //查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //存在
            return JSONUtil.toBean(json, type);
            
        }
        //判断是否是空值
        if (json != null) {
            //    返回错误信息
            return null;
        }
        //不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        //不存在，返回错误404
        if (r == null) {
            // 将空数据写入redis
            stringRedisTemplate.opsForValue()
                    .set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在，写入redis
        this.set(key, r, time, unit);
        
        return r;
    }
    
    
    //使用逻辑过期解决缓存击穿 和 缓存穿透
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time, TimeUnit unit) {
        
        //查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(json)) {
            //不存在,直接返回
            return null;
        }
        //查询到，需要将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回
            return r;
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
                return r;
            }
            //成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 缓存重建:查询数据库，写入redis
                    R r1 = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicExpire(key, r1, time, unit);
                    //this.saveShop2Redis(id, CACHE_SHOP_TTL);
                    
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //   释放锁
                    unLock(lockKey);
                }
            });
        }
        //获取锁失败，返回过期商铺信息
        return r;
    }
    
    //获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        
        return BooleanUtil.isTrue(flag);
    }
    
    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
    
    
}
