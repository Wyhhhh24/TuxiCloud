package com.air.yunpicturebackend.manager.websocket;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.air.yunpicturebackend.constant.UserConstant;
import com.air.yunpicturebackend.manager.auth.SpaceUserAuthContext;
import com.air.yunpicturebackend.manager.auth.SpaceUserAuthManager;
import com.air.yunpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.air.yunpicturebackend.model.entity.Picture;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.enums.SpaceTypeEnum;
import com.air.yunpicturebackend.service.PictureService;
import com.air.yunpicturebackend.service.SpaceService;
import com.air.yunpicturebackend.service.SpaceUserService;
import com.air.yunpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * @author WyH524
 * @since 2025/9/25 16:49
 *
 * 根据我们的需求，建立 WebSocket 连接前，需要先进行用户权限校验，只有它是该团队空间的成员才能让它建立连接
 * 并且还要把当前登录的用户信息保存到即将建立成功的 WebSocket 会话属性中，以后在消息传输时需要去获取这些信息
 * 那么我们在建立 WebSocket 连接前就要进行校验，那怎么在发送请求前做一些事情呢？如果是 Http 请求是不是会写一个 aop 切面、一个拦截器
 * 同样的我们的 WebSocket 也有一个拦截器，如下:
 *
 * WebSocket 拦截器，建立连接前需要校验
 */
@Component
@Slf4j
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 建立连接前先校验
     *
     * 我们先去检查有没有权限，有权限再给你建立连接，再完成握手
     * attributes 给 WebSocketSession 会话设置属性
     * 因为 WebSocket 它基于 Http 协议实现的嘛，本身也是具有会话的概念的，每个会话可以存放一些值，便于我们等会去使用
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 1.获取当前登录用户
        if(request instanceof ServletServerHttpRequest){
            // 1.1.获取 HttpServletRequest 对象，这个就是我们平时开发接口时经常拿到的对象了
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            // 1.2.从请求中获取参数，我们获取的参数可不只有用户信息吧，还要知道当前用户编辑的是哪张图片，每一个接口请求地址是不是可以传递一些参数
            // 比如说 Get 请求，我们就可以取出 ? 后面你的 url 参数
            String pictureId = servletRequest.getParameter("pictureId");
            if(StrUtil.isBlank(pictureId)){
                log.error("缺少图片参数，拒绝握手");
                // 返回 false 拒绝连接， 返回 true 建立连接
                return false;
            }
            // 1.3.校验用户是否登录
            User loginUser = userService.getLoginUser(servletRequest);
            if(ObjectUtil.isEmpty(loginUser)){
                // 如果用户未登录就不用报异常了，直接返回false ，不用建立连接就行了
                log.error("用户未登录，拒绝握手");
                return false;
            }
            // 1.4.校验是否有编辑图片的权限
            Picture picture = pictureService.getById(pictureId);
            if(ObjectUtil.isEmpty(picture)){
                log.error("图片不存在，拒绝握手");
                return false;
            }
            Long spaceId = picture.getSpaceId();
            Space space = null;
            // 1.4.1.spaceId 不为空才是团队空间，否则公共图库
            if(spaceId != null){
                space = spaceService.getById(spaceId);
                if(ObjectUtil.isEmpty(space)){
                    // 空间都不存在直接报错
                    log.error("空间不存在，拒绝握手");
                    return false;
                }
                // 判断是不是团队空间
                if(space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()){
                    log.error("不是团队空间，拒绝握手");
                    return false;
                }
            }
            // 1.4.2.spaceId 为空的话就是公共图库，按理来说的话公共图库中的图片是不支持协同编辑的
            // 但是这里我们考虑到假如以后系统有多个管理员，又想让多个管理员能同时编辑系统中的公共图片，所以这里做一个小的扩展，允许 spaceId 为空，这里也可以进行权限校验
            // 拿到用户的权限列表，判断权限列表是否包含编辑权限
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            if(!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)){
                // 如果不包含的话，也报错
                log.error("用户没有编辑权限，拒绝握手");
                return false;
            }

            // 1.5.现在用户有权限建立连接了
            // 1.5.1.设置用户登录信息等属性到 WebSocket 会话中，我们这个握手前的方法有一个参数 Map<String, Object> attributes
            // 它是一个 Map ，我们给这个 Map 中插入键值对，就相当于给接下来给建立连接的 WebSocket 会话创建属性
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId()); // 此外为了方便这里也设置一个 userId 不知道会不会用到，先存了
            attributes.put("pictureId", Long.valueOf(pictureId)); // 这个是一定要存的，没有 pictureId 怎么去维护 pictureId 对应的会话集合
            // 这里的 pictureId 从 String 类型转换为 Long 类型
            return true;
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
