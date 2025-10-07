package com.air.yunpicturebackend.manager.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

/**
 * @author WyH524
 * @since 2025/9/25 21:04
 * WebSocket 的配置，定义连接
 */
@Configuration
@EnableWebSocket  //还需要打上这个注解
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * 我们自己定义的消息处理器
     */
    @Resource
    private PictureEditHandler pictureEditHandler;

    /**
     * 拦截器，每次在执行 WebSocket 连接前进行权限校验
     */
    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    /**
     * 类似于编写؜ Spring MVC 的 Controller 接口，可以为指定的路径‍配置处理器和拦截器
     * 配置之后，前端就可以通过 WebSocket 连接项目启动端口的 /ws/picture/edit 路径
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pictureEditHandler, "/ws/picture/edit") // 注册一个处理器，然后是请求地址 TODO 每一个接口都可以写一个单独的处理器
                .addInterceptors(wsHandshakeInterceptor) // 添加拦截器，可以添加多个，这个拦截器进行权限校验的
                .setAllowedOriginPatterns("*"); // 允许跨域
    }
}
