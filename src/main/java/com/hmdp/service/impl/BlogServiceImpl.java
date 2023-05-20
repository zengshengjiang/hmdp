package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 *笔记功能实现
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private IFollowService followService;
    
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        //records.forEach(this::queryBlogUser);
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if (blog==null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        
        //查询blog是否被点赞
        isBlogLiked(blog);
        
        return Result.ok(blog);
    }
    
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user==null) {
            //用户未登录
            return;
        }
        Long userId = user.getId();
        String key=BLOG_LIKED_KEY+blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
        
    }
    
    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key=BLOG_LIKED_KEY+id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score==null) {
        //未点赞
            boolean isSuccess = this.update().setSql("liked=liked+1").eq("id", id).update();
            //保存点赞用户到sortedSet集合
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else{
        //    已点赞
            boolean isSuccess = this.update().setSql("liked=liked-1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }
    
    @Override
    public Result queryBlogLikes(Long id) {
        String key=BLOG_LIKED_KEY+id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5==null||top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id",ids)
                .last("order by field(id,"+idStr+")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        
        return Result.ok(userDTOS);
    }
    
    @Override
    public Result saveBlog(Blog blog) {
        //获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = this.save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        //查询笔记id给所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //推送
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            
            String key=FEED_KEY+userId;
            
            stringRedisTemplate.opsForZSet().add(key,follow.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }
    
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        
        String key=FEED_KEY+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                        .opsForZSet()
                        .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples==null||typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime =0;
        int os=1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //获取id
            String idStr = tuple.getValue();
            ids.add(Long.valueOf(idStr));
            //时间戳
            long time = tuple.getScore().longValue( );
            if (time==minTime) {
                os++;
            }else{
                minTime=time;
                os=1;
            }
          
        }
        
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query()
                .in("id", ids)
                .last("order by field(id," + idStr + ")").list();
        
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        
        return Result.ok(scrollResult);
    }
    
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
