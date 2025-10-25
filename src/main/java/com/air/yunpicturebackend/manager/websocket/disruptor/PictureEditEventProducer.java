package com.air.yunpicturebackend.manager.websocket.disruptor;
import com.air.yunpicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.air.yunpicturebackend.model.entity.User;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * 图片编辑事件生产者
 */
@Component
@Slf4j
public class PictureEditEventProducer {

    /**
     * 注入 Disruptor 对象
     */
    @Resource
    Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    /**
     * 生产者
     * 将事件的发布到缓冲区
     * 我们需要自己把传过来的必要参数，封装成一个对象，也就是封装成一个事件然后发到队列里，队列绑定消费者之后，就会自行进行消费了
     * 我们怎么样去往队列里面放内容呢？是不是要先取到这个环形缓冲区下一个可以放置的位置
     * 不是随便来放置的，需要根据序号来放置这个任务的
     */
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        // 1.获取到可以放置事件的位置
        // 1.1.拿到缓冲区对象
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 1.2.拿到下一个可放置的位置
        long next = ringBuffer.next();
        // 2.根据下一个位置，获取到我们要存放的事件对象，给这个事件对象赋值
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        // 3.发布事件
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机
     * 这个操作可以写到生产者里面也可以写到 disruptor 的配置里面
     * 现在我们有了一个队列，如果队列中有任务没有处理完，我们可以做一个优雅停机
     */
    @PreDestroy
    public void close() {
        // disruptor 为我们提供了一个现成的方法，这个方法就会让我们的 disruptor 默认先处理完所有的任务之后，再去关闭
        pictureEditEventDisruptor.shutdown();
    }
}
