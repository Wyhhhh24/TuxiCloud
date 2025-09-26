package com.air.yunpicturebackend.manager.websocket.disruptor;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * @author WyH524
 * @since 2025/9/26 14:34
 * 图片编辑事件 Disruptor 配置
 * 这是环形控制器的配置类
 * 我们要先定义一下环形队列的结构，这样生产者才好往队列中去写
 *
 * 这样我们就创建出来了一个 Disruptor 的配置，等会我们在消息生产中就可以使用这个对象，拿到它的缓冲区，往里面塞任务了
 */
@Configuration
public class PictureEditEventDisruptorConfig {

    /**
     * 需要注册消费者
     */
    @Resource
    private PictureEditEventWorkHandler pictureEditEventWorkHandler;

    /**
     * 初始化一个 Disruptor 对象
     * 定义成一个 Bean 便于在其它代码中去使用这个 Bean
     */
    @Bean("PictureEditEventDisruptor")
    public Disruptor<PictureEditEvent> messageModelRingBuffer(){
        // 定义环形缓冲区 ringBuffer 的大小，就和定义一个消息队列一样，首先要定义大小
        // 这里定义的大一点，一般情况下，如果要用这个环形缓冲区，并发起码都是万级以上，如果并发量还没有那么大，提升的性能也是比较有限的
        // 这里我们就假设并发量达到了百万，缓冲区同时要接收百万的任务，如果定义小了，任务是不是很容易被覆盖
        // 就很容易因为防止任务被覆盖而产生等待，等待消费者消费，生产者就无法添加任务
        // 所以这个队列个根据情况来定义，定义得越大越占用空间，能容纳得任务越多
        int bufferSize = 1024 * 256;
        // 创建 Disruptor 对象
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(
                PictureEditEvent::new, //创建一个 Disruptor 的事件对象，就是用来定义每次放到缓冲区的数据的类型，这里是图片编辑事件类型
                bufferSize, // 缓冲区的大小
                ThreadFactoryBuilder.create() // 创建一个线程是为了我们更好的去调试，这是一个最佳实践
                        .setNamePrefix("PictureEditEventDisruptor")  // 指定每个线程的前缀
                        .build()  // 这样以后事件打印出来的信息，在日志中就能看出来，异步执行的
        );

        // 给 Disruptor 绑定消费者，使用我们的 worker 工作线程来执行我们的消费者的事件
        disruptor.handleEventsWithWorkerPool(pictureEditEventWorkHandler);

        // 开启 Disruptor
        disruptor.start();

        // 返回对象
        return disruptor;
    }


}
