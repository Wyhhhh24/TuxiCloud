package com.air.yunpicturebackend.manager.websocket.model;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 和我们的 Http 请求一样，我们的 Http 请求是不是我们每一个接口都要定义一个请求封装类，定义一个响应类，vo 视图类
 * WebSocket 通讯，一样的也要定义一个请求类，一个响应类
 *
 * 客户端向服务器发送的图片编辑请求消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditRequestMessage {

    /**
     * 消息类型，例如 "ENTER_EDIT", "EXIT_EDIT", "EDIT_ACTION" ,"INFO","ERROR"
     *                进入编辑       退出编辑     执行编辑的操作   INFO 和 ERROR 是后端发给前端的，前端不会发这两种消息类别
     */
    private String type;

    /**
     * 执行的编辑动作
     * 如果前端执行左旋右旋，光有一个消息类型是没有办法区分，所以无论左旋右旋放大缩小都对应于这个字段
     */
    private String editAction;
}
