package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * 拦截器配置
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //添加拦截器 order(int order)为拦截器执行顺序，小的先执行
        registry.addInterceptor(new LoginInterceptor())
                //设置不拦截那些路径
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "shop-type/**",
                        "/upload/**",
                        "/voucher/**"
                ).order(1);
        //拦截所有请求：刷新token有效期
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
    }
}
