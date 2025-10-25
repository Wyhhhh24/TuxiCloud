package com.air.yunpicturebackend.manager.websocket.disruptor;

import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.manager.websocket.PictureEditHandler;
import com.air.yunpicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.air.yunpicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.air.yunpicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lmax.disruptor.WorkHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

/**
 * @author WyH524
 * @since 2025/9/26 14:20
 * 图片编辑事件处理器（消费者）
 */
@Slf4j
@Component                // 需要实现 Disruptor 的消费者接口，泛型就是事件类型，这是自定义的，也就要处理消息得要要传的必要参数可以封装到这个类里面，等同于事件
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    // 我们得要使用 PictureEditHandler 中定义的处理消息的方法
    @Resource
    PictureEditHandler pictureEditHandler;

    @Resource
    private UserService userService;

    /**
     * 和消息队列一样了，我们提供一个方法，当队列接收了事件之后，就通过这个方法进行处理，参数就是我们定义的事件对象，放到队列中的事件
     * 处理事件的函数
     */
    @Override
    public void onEvent(PictureEditEvent pictureEditEvent) throws Exception {
        // 1.从 PictureEditEvent 中获取到消息
        PictureEditRequestMessage pictureEditRequestMessage = pictureEditEvent.getPictureEditRequestMessage();
        Long pictureId = pictureEditEvent.getPictureId();
        WebSocketSession session = pictureEditEvent.getSession();
        User user = pictureEditEvent.getUser();

        // 2.从消息信息中获取到对应的消息类别，获取到这个消息类型的枚举
        String type = pictureEditRequestMessage.getType();
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.getEnumByValue(type);

        switch (pictureEditMessageTypeEnum) { // INFO 和 ERROR 是后端发给前端的，前端不会发这两种消息类别
            case ENTER_EDIT:
                pictureEditHandler.handleEnterEditMessage(pictureEditRequestMessage,session,user,pictureId);
                break;
            case EXIT_EDIT:
                pictureEditHandler.handleExitEditMessage(pictureEditRequestMessage,session,user,pictureId);
                break;
            case EDIT_ACTION:
                pictureEditHandler.handleEditActionMessage(pictureEditRequestMessage,session,user,pictureId);
                break;
            default:
                // 如果收到其它类型的消息，就给当前客户端发送错误类型的消息
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                pictureEditResponseMessage.setMessage("消息类型错误");
                pictureEditResponseMessage.setUser(userService.getUserVO(user));
                // 解决精度丢失问题，因为需要返回用户 Id Long 类型的
                ObjectMapper objectMapper = new ObjectMapper(); // 创建 jackson 库的 ObjectMapper
                SimpleModule module = new SimpleModule();
                module.addSerializer(Long.class, ToStringSerializer.instance);
                module.addSerializer(Long.TYPE, ToStringSerializer.instance);
                objectMapper.registerModule(module);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pictureEditResponseMessage)));
                break;
        }
    }
}
