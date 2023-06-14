package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    
    //锁的名称
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private StringRedisTemplate stringRedisTemplate;
    
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    
    public SimpleRedisLock() {
    }
    
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    
    
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        //避免success拆箱时出现空指针
        return Boolean.TRUE.equals(success);
    }
    
    //释放锁
    @Override
    public void unLock() {
        //    调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
        
    }
    /*
        @Override
        public void unLock() {
            //获取线程标识
            String threadId = ID_PREFIX + Thread.currentThread().getId();
            //    获取锁中的标识
            String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
            if (threadId.equals(id)) {
                //释放锁
                stringRedisTemplate.delete(KEY_PREFIX + name);
            }
            
        }*/
        
}
