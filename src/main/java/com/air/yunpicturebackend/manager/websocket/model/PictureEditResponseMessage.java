package com.air.yunpicturebackend.manager.websocket.model;

import com.air.yunpicturebackend.model.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author WyH524
 * @since 2025/9/25 16:35
 * 服务器后端要发送给前端客户端的一个消息
 * 就比如说用户现在它执行了一个操作，现在是不是要告诉其它用户现在执行的是什么操作
 *
 * 图片编辑响应消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditResponseMessage {

    /**
     * 消息类型，例如 "INFO", "ERROR", "ENTER_EDIT", "EXIT_EDIT", "EDIT_ACTION"
     */
    private String type;

    /**
     * 信息，比如说是 INFO 类型的消息，就是要传送给前端的信息，这个信息是通过后端传递给前端的，不是前端自己定制的
     * 这样更灵活一点
     */
    private String message;

    /**
     * 执行的编辑动作，用户执行了哪些编辑动作，也要返回给前端
     */
    private String editAction;

    /**
     * 用户信息，当前哪个用户正在编辑，这个也是需要返回给前端的
     * 我们的需求中有一个是某一个用户在编辑时，其它用户要看到谁正在编辑，如果不告诉当前正在编辑的用户是谁这个需求就实现不了
     */
    private UserVO user;
}

