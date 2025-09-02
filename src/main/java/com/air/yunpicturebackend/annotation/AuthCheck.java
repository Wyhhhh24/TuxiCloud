package com.air.yunpicturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//大多数自定义注解的时候，都是需要这两个注解
@Target(ElementType.METHOD)  //注解的生效范围，这里是针对方法打上的注解
@Retention(RetentionPolicy.RUNTIME)  //指定注解在什么时候生效，运行时
public @interface AuthCheck {

    /**
     * 我们这个注解要接收哪些参数呢？
     * 我们是不是想要知道这个接口哪些角色有角色访问
     * 我们就指定 mustRole 表示该接口必须具有某个角色
     * 我们可以约定一下，只要打上了这个注解，它就必须要登录，也就是说这个 mustRole 不填的话，默认就是用户必须要登录才能调用这个接口
     * 如果你想要用户不登陆也能调用这个接口，那就别用这个注解了，用了这个注解就是要校验登录校验权限的
     */
    String mustRole() default  "";
    //默认是空字符串，这里我们设置为空字符串，就是让它不需要校验任何角色
}
