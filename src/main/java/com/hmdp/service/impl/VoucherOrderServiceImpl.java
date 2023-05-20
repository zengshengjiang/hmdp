package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    //阻塞队列
    private BlockingQueue<VoucherOrder> orderTask=new ArrayBlockingQueue<>(1024*1024);
    
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
        while (true){
            //获取队列中的订单信息
            try {
                VoucherOrder voucherOrder = orderTask.take();
            //    创建订单
                handleVoucherOrder(voucherOrder);
            } catch (Exception e) {
               log.error("处理订单异常",e);
            }
            
        }
        }
     
    }
    
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock) {
            //获取锁失败，返回错误
            log.error("请勿重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
        //}
    }
    
    private IVoucherOrderService proxy;
    @Override
    public Result seckillVocher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        
        int r = result.intValue();
        if (r!=0) {
        //不为0，没有购买资格
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }
        //有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        //    有购买资格
        //订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        
         //放入阻塞队列
        orderTask.add(voucherOrder);
        
        //获取代理对象（事务），不然事务不生效，用this事务不生效
         proxy = (IVoucherOrderService) AopContext.currentProxy();
        
        return Result.ok(orderId);
    }
    
    /*  @Override
    
    public Result seckillVocher(Long voucherId) {
        //获取优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //加悲观锁
        //synchronized (userId.toString().intern()) {
        
        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        //boolean isLock = lock.tryLock(1200);
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock) {
        //获取锁失败，返回错误
            return Result.fail("请勿重复下单");
        }
        try {
            //获取代理对象（事务），不然事务不生效，用this事务不生效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }
        //}
        
    }*/
    
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        
        
        int count = this.query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        if (count > 0) {
            log.error("用户已购买过");
            return;
        }
        
        //判断是否更新成功
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
            return ;
        }
        
       /* VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order:");
        voucherOrder.setId(orderId);


        voucherOrder.setUserId(userId);

        voucherOrder.setVoucherId(voucherOrder);
*/
        save(voucherOrder);
        
        //return Result.ok(orderId);
        
        
    }
}
