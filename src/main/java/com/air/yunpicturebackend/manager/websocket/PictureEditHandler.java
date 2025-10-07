package com.air.yunpicturebackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.air.yunpicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.air.yunpicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.air.yunpicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.air.yunpicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author WyH524
 * @since 2025/9/25 18:49
 *
 * 图片编辑 WebSocket 处理器
 * 定义一些 WebSocket 的处理器去处理对应的消息
 * 如果客户端给我们服务器发送了用户进入编辑、退出编辑、执行图片操作等信息的时候，我们应该怎么处理
 */
@Slf4j       // 这里继承的是这个类，我们等会都是用 json 完成前后端的交互，是以字符串的方式发‍送和接受消息，所以这里继承的使用文本的 WebSocket 处理器
@Component           // 需要重写几个方法
public class PictureEditHandler extends TextWebSocketHandler {

    /**
     * 每张图片对应正在编辑的用户
     * 保存当前正在编辑的用户 id，执行编辑操作、进入或退出编辑时都会校验。
     * 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的 userId
     * 我们需求中明确了一点，同时只能有一个用户进入编辑，我们怎么知道有用户进入编辑了呢？是不是需要把某张照片当前正在编辑的用户给存下来
     * 一个图片只能有一个用户正在编辑
     */
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    /**
     * 每张图片有哪些用户在操作这张图片
     * 保存参与编辑图片的用户 WebSocket 会话的集合。
     * 保存所有连接的会话，key: pictureId, value: WebSocketSession 的集合（用户会话集合），WebSocketSession 就是 SpringWebSocket 为我们提供的 WebSocket 会话类
     * 我们一定要使用 并发的 HashMap ，因为接下来我们无论有多少个连接，都是要调用相同这个类的这些方法，都要同时去操作这个集合，为了保证线程安全，为了防止有些数据不要丢失
     * 所以使用线程安全的这个 HashMap
     */
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;

    /**
     * 用户与服务端建立会话连接成功之后，会执行这个方法
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        // 1.保存会话到集合中
        // 1.1.从 session 属性中拿到当前会话的用户信息，pictureId
        Long pictureId = (Long)session.getAttributes().get("pictureId");
        User user = (User)session.getAttributes().get("user");
        // 如果会话集合中 pictureId 还没有任何用户加入到编辑中，Set 是为空的，首次加入的时候，先初始化这个集合，然后再把 pictureId 放到这个 Map 中
        pictureSessions.putIfAbsent(pictureId,ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);

        // 2.构造响应，告诉其它用户现在谁加入编辑了，发送加入编辑的消息通知
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue()); // 消息类型是 INFO
        String message = String.format("用户 %s 加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user)); // 设置脱敏的用户信息

        // 3.将这个响应广播给这张图片的所有用户，包括自己
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * 收到前端发送的消息，，根据消息类别处理消息
     * 前端给我们的服务器发送了一个请求，我们收到请求之后，就根据用户发的消息来通知其它的客户端，把这个消息进行一个处理，广播啥的
     * 就和我们自己写 controller 一样的道理，根据不同的请求找到不同的方法
     *
     * 使用 Disruptor 处理消息
     * 要定义一个事件，因为我们队列是要接收任务、接收事件的，我们要把这个消息处理当作一个事件，我们是要分为生产者、消费者和事件
     * handleTextMessage 中就是接收 WebSocket 传过来的消息进行处理，这个就是生产者，接收传过来的消息发送到消息队列中
     * handleEnterEditMessage、handleExitEditMessage、handleEditActionMessage 这些处理消息的方法就放到消费者里面去
     * 原本在 WebSocket 处理函数 handleTextMessage 中要顺序执行，现在只要把处理消息的代码当作一个事件去发送，那就相当于直接往消息队列里面发一个事件
     * 实现
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        // 1.获取消息内容，将 JSON 转换为 PictureEditRequestMessage ，这里不需要配置 Json 的自定义序列化了，因为前端发来的消息本来就是字符串了
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);

        // 2.从 session 属性中拿到当前会话的用户信息，pictureId
        Long pictureId = (Long)session.getAttributes().get("pictureId");
        User user = (User)session.getAttributes().get("user");

//        // 3.根据消息的类型进行对应的处理
//        String type = pictureEditRequestMessage.getType();
//        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.getEnumByValue(type);
//        switch (pictureEditMessageTypeEnum) { // INFO 和 ERROR 是后端发给前端的，前端不会发这两种消息类别
//            case ENTER_EDIT:
//                handleEnterEditMessage(pictureEditRequestMessage,session,user,pictureId);
//                break;
//            case EXIT_EDIT:
//                handleExitEditMessage(pictureEditRequestMessage,session,user,pictureId);
//                break;
//            case EDIT_ACTION:
//                handleEditActionMessage(pictureEditRequestMessage,session,user,pictureId);
//                break;
//            default:
//                // 如果收到其它类型的消息，就给当前客户端发送错误类型的消息
//                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
//                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
//                pictureEditResponseMessage.setMessage("消息类型错误");
//                pictureEditResponseMessage.setUser(userService.getUserVO(user));
//                // 解决精度丢失问题，长整型转换成 json 的时候会精度丢失
//                ObjectMapper objectMapper = new ObjectMapper(); // 创建 jackson 库的 ObjectMapper
//                SimpleModule module = new SimpleModule();
//                module.addSerializer(Long.class, ToStringSerializer.instance);
//                module.addSerializer(Long.TYPE, ToStringSerializer.instance);
//                objectMapper.registerModule(module);
//                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pictureEditResponseMessage)));
//                break;
//        }

        // 3.根据消息类型处理消息（生产消息到 disruptor 环形队列中）
        // 以后如果说同时来了 10w 个请求，我们现在的线程操作已经是异步的了，首先现在 10w 个请求来了，我们只会提交完事件
        // 这个请求就返回了，这个 WebSocket 就可以继续去接收更多的请求了
        // 而我们的后端，会有 WorkHandler 消费者，默默地去使用额外地线程去处理这些操作
        // 那它是怎么处理地呢？它会按照生产任务地顺序，依次从任务队列中取出来任务进行执行，这样我们就实现了处理消息和接收消息的解耦，避免阻塞
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage,session,user,pictureId);
    }

    /**
     * 进入编辑状态
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 1.没有用户正在编辑该图片的时候，当前用户才可以进入编辑状态，看一下 Map 中有没有 pictureId 这个 Key ，有的话说明有用户正在编辑，不能加入
        if(!pictureEditingUsers.containsKey(pictureId)){
            // 1.1.设置当前用户正在编辑图片
            pictureEditingUsers.put(pictureId,user.getId());

            // 1.2.构造响应，告诉其它用户现在谁进入编辑了
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue()); // 消息类型是 INFO
            String message = String.format("用户 %s 开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user)); // 设置脱敏的用户信息

            // 1.3.广播给所有用户
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }


    /**
     * 处理编辑操作，放大缩小
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 1.判断当前登录用户是不是正在编辑的用户
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum pictureEditActionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if(pictureEditActionEnum == null){
            log.error("无效的编辑操作");
            return;
        }
        // 1.1.确认当前用户是编辑者，才能够编辑
        // 编辑动作到底要做什么呢？一个用户如果点击了放大，我是不是只要把这个放大消息原封不动的告诉其它的前端（会话）就好了
        if(editingUserId != null && editingUserId.equals(user.getId())){
            // 1.2.构造响应发送具体的操作通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s 执行 %s 操作", user.getUserName(),pictureEditActionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 1.3.广播给除了当前客户端之外的其它用户，否则会造成重复编辑，也就是会造成当前用户多编辑一次
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    /**
     * 退出正在编辑状态
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 1.判断当前登录用户是不是编辑者，如果不是正在编辑的用户怎么退出编辑呢？
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if(editingUserId != null && editingUserId.equals(user.getId())){
            // 1.1.移除用户正在编辑该图片，从 Map 中移除掉当前这个 pictureId 这个 Key
            pictureEditingUsers.remove(pictureId);

            // 1.2.构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("用户 %s 退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 1.3.广播所有的用户
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }



    /**
     * 客户端退出连接，关闭了这个连接之后，我们需要释放一些资源
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        // 1.也就是当前用户可能从正在编辑状态直接退出了连接，所以也需要调用上面这个 handleExitEditMessage 方法，退出正在编辑状态
        // 1.1.从 session 属性中拿到当前会话的用户信息，pictureId
        Long pictureId = (Long)session.getAttributes().get("pictureId");
        User user = (User)session.getAttributes().get("user");
        // 1.2.移除当前用户正在编辑图片的状态
        handleExitEditMessage(null, session, user, pictureId);

        // 2.删除会话
        Set<WebSocketSession> webSocketSessions = pictureSessions.get(pictureId);
        if(webSocketSessions != null){
            // 2.1.如果 webSocketSessions 不为 null
            webSocketSessions.remove(session);
            // 2.2.如果集合为空了之后，直接将这个 Key 从 Map 里面删除掉
            if(webSocketSessions.isEmpty()){
                pictureSessions.remove(pictureId);
            }
        }

        // 3.用户退出编辑了，也需要广播，通知其它用户该用户以及离开编辑
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }


    /**
     * 我们有一个很重要的需求是，无论哪个用户做了一些操作，是不是都有可能发送给其它用户
     * 比如用户 A 操作了，用户 B 、用户 C 都要收到用户 A 的操作
     * 第一种情况是：把消息发送给除自己之外的所有用户
     * 第二种情况是：包括自己，只要有消息，我要给所有的用户都发送一遍消息
     *
     * 广播给该图片的所有用户（支持排除掉某一个 session）
     */
    public void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage , WebSocketSession excludeSession) throws IOException {
        // 1.根据 pictureId 获取当前这个图片的的所有会话对象集合
        Set<WebSocketSession> sessionsSet = pictureSessions.get(pictureId);
        // 2.如果会话对象集合不为空才广播给所有的会话
        // 有一个 Bug ：PictureEditResponseMessage 对象中的 UserVO 对象中的 id 属性是 Long 类型的，在转换成 json 字符串的时候，会有精度丢失问题
        // 这样的话有可能我们就没办法拿到正在编辑的用户 ID ，不好判断当前用户能不能操作了，因为前端有一个逻辑只有发现正在编辑的用户是自己才能编辑，所以需要配置一下
        // 解决 Long 类型精度丢失问题
        ObjectMapper objectMapper = new ObjectMapper(); // 创建 jackson 库的 ObjectMapper
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
        // 序列化成 JSON 字符串
        String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
        TextMessage textMessage = new TextMessage(message);
        if(CollUtil.isNotEmpty(sessionsSet)){
            // 3.遍历会话对象集合，发送消息给所有的会话
            for (WebSocketSession session : sessionsSet) {
                // 排除掉的 session 不发送
                if(excludeSession != null && excludeSession.equals( session)){
                    continue;
                }
                if(session.isOpen()){
                    session.sendMessage(textMessage); //这里发送的消息，必须得是 TextMessage 类型的，所以进行转化
                }
            }
        }
    }

    /**
     * 广播给该图片的所有用户
     */
    public void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws IOException {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }
}
