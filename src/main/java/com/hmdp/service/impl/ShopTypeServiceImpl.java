package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_KEY;

/**
 * 商铺类型
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Override
    public Result queryShopList() {
        String key = SHOP_TYPE_KEY;
        List<String> shopList = stringRedisTemplate.opsForList().range(key, 0, -1);
        //判断商铺类型是否在redis中存在
        if (shopList != null && !shopList.isEmpty()) {
            //存在
            List<ShopType> list = JSONUtil.toList(shopList.toString(), ShopType.class);

            return Result.ok(list);
        }
        //不存在就查询数据库
        List<ShopType> typeList = this.query().orderByAsc("sort").list();
        //判断数据库中是否有数据
        if (typeList != null && !typeList.isEmpty()) {
            //有数据
            //将ShopType类型转化你为String类型
            List<String> list = JSONUtil.toList(JSONUtil.toJsonStr(typeList), String.class);
            //将数据存入redis
            stringRedisTemplate.opsForList().rightPushAll(key, list);
            return Result.ok(typeList);
        }
        return Result.fail("没有商铺类型数据");
    }
}
