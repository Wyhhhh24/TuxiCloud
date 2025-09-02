package com.air.yunpicturebackend.aop;

import com.air.yunpicturebackend.annotation.AuthCheck;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.enums.UserRoleEnum;
import com.air.yunpicturebackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author WyH524
 * @since 2025/8/28 下午6:10
 */
@Aspect //表示这是一个切面
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 执行拦截
     *
     * @param joinPoint 切入点
     * @param authCheck 权限校验注解
     */
    //定义对哪些方法进行拦截
    //这是一个切点，就是你想要在哪些地方去执行这里面的代码
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        //可以通过这个 joinPoint 来知道我们到底对哪个方法进行了拦截  authCheck 这是我们刚刚自己定义的注解
        //我们可以通过这样一个写法，得到接口上我们设置的允许什么角色访问这个接口
        String mustRole = authCheck.mustRole();

        //可以通过这样一个全局的上下文，可以得到当前这个请求所有的属性
        //获取当前HTTP请求的HttpServletRequest对象
        //从而让你能够在应用的任何地方（如Service层、工具类等）访问当前请求的详细信息（如参数、头信息、Session等）
        //而不仅限于Controller层，可以自行构建
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        //获取当前登录用户
        //得到登录用户的信息，这个方法里面有校验你是否登录的逻辑，所以我们现在自定义的注解也就校验了你是否登录
        //也就是，如果你为接口添加了这个注解，就证明这个接口是需要登录才可以访问的
        User loginUser = userService.getLoginUser(request);

        //根据 value 值获取对应的枚举，也就是获取当前这个接口允许哪位角色访问
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);

        // 如果返回的枚举为空，也就是所加注解的接口上，不是我们需要我们进行权限控制，而只是登录校验
        if (mustRoleEnum == null) {
            //直接放行
            return joinPoint.proceed();
        }

        // 所加注解的方法必须有对应权限才通过
        // 获取当前用户具有的权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());

        // 该接口需要对应权限的用户访问，但是现在访问的用户没有权限
        if (userRoleEnum == null) {
            //抛异常，拒绝访问接口
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 如果该接口要求必须得要有管理员权限才能访问，但当前用户没有管理员权限
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            //拒绝
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 该用户通过了权限校验，放行
        return joinPoint.proceed();
    }
}
