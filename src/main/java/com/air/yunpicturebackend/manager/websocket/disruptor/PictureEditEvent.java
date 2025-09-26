package com.air.yunpicturebackend.manager.websocket.disruptor;

import com.air.yunpicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.air.yunpicturebackend.model.entity.User;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

/**
 * 图片编辑事件，这四个参数，就对应了我们编写的图片处理函数接收的四个参数，我们把图片处理当作一个事件，等会要发送给我们的队列
 */
@Data
public class PictureEditEvent {

    /**
     * 消息
     */
    private PictureEditRequestMessage pictureEditRequestMessage;

    /**
     * 当前用户的 session
     */
    private WebSocketSession session;
    
    /**
     * 当前用户
     */
    private User user;

    /**
     * 图片 id
     */
    private Long pictureId;
}
