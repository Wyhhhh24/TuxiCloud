package com.air.yunpicturebackend.manager.auth;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

/**
 * StpLogic 门面类，管理项目中所有的 StpLogic 账号体系
 * 添加 @Component 注解的目的是确保静态属性 DEFAULT 和 SPACE 被初始化
 */
@Component
public class StpKit {
    public static final String SPACE_TYPE = "space";

    /**
     * sa-token 可以使用两套账号体系，默认的我们没有用
     * 因为我们是基于 自定义注解+aop 实现的用户登录权限校验
     * 这里我们定义一套 SPACE 账号体系
     * 具体 Kit 模式的用法，看文档：https://sa-token.cc/doc.html#/up/many-account 中的第 5 点
     */

    /**
     * 默认原生会话对象，项目中目前没使用到，StpUtil.login(10001) 也就是这种原生的，直接调用这个方法的话，就是这套默认的体系
     */
    public static final StpLogic DEFAULT = StpUtil.stpLogic;

    /**
     * Space 会话对象，管理 Space 表所有账号的登录、权限认证
     */
    public static final StpLogic SPACE = new StpLogic(SPACE_TYPE);
}
