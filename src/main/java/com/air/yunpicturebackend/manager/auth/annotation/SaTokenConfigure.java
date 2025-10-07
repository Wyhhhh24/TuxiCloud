package com.air.yunpicturebackend.manager.auth.annotation;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.PostConstruct;

/**
 * Sa-token 开启注解和配置
 * 从官方文档中粘贴过来
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    // 使用注解式鉴权必须要注册这个拦截器
    // Sa-Token 使用全局拦截器完成注解鉴权功能，为了不为项目带来不必要的性能负担，拦截器默认处于关闭状态
    // 因此，为了使用注解鉴权，你必须手动将 Sa-Token 的全局拦截器注册到你项目中
    // 注册 Sa-Token 拦截器，打开注解式鉴权功能
    // 文档：https://sa-token.cc/doc.html#/use/at-check
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }

    // 多账号体系下，使用注解合并功能
    // 重写 Sa-Token 的注解处理器，增加注解合并功能
    // @SaCheckLogin(type = "user") 指定账号类型，但几十上百个注解都加上这个的话，还是有些繁琐，代码也不够优雅，有么有更简单的解决方案？
    // 我们期待一种[注解继承/合并]的能力
    // 即：自定义一个注解，标注上 @SaCheckLogin(type = "user") ， 然后在方法上标注这个自定义注解，效果等同于标注 @SaCheckLogin(type = "user")
    // 很遗憾，JDK默认的注解处理器并没有提供这种 [注解继承/合并] 的能力，不过好在我们可以利用 Spring 的注解处理器，达到同样的目的。
    // 文档：https://sa-token.cc/doc.html#/up/many-account 中的 第七点
    @PostConstruct
    public void rewriteSaStrategy() {
        SaAnnotationStrategy.instance.getAnnotation = (element, annotationClass) -> {
            return AnnotatedElementUtils.getMergedAnnotation(element, annotationClass);
        };
    }
}
