package com.air.yunpicturebackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.air.yunpicturebackend.model.entity.Picture;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.entity.SpaceUser;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.enums.SpaceRoleEnum;
import com.air.yunpicturebackend.model.enums.SpaceTypeEnum;
import com.air.yunpicturebackend.service.PictureService;
import com.air.yunpicturebackend.service.SpaceService;
import com.air.yunpicturebackend.service.SpaceUserService;
import com.air.yunpicturebackend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.air.yunpicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * sa-token 中做权限校验的话，类需要实现 StpInterface 接口，重写方法
 * 自定义权限加载接口实现类
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    /**
     * 我们需要通过请求的 url 来判断应该给上下文对象传递什么参数
     * 我们要获取当前请求的上下文路径，这样才能从 url 中获取到指定的前缀  /api
     */
    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    /**
     * 返回一个账号所拥有的权限码集合
     * 我们要在这个方法中编写根据用户id，以及我们获取到的一系列请求参数进行鉴权逻辑
     * 只要打上了注解，这个接口就变成了用户登录之后才能访问
     */
    @Override                    // 这里的 loginType 是在登录的时候 StpKit.SPACE.login(user.getId()) 中的 .SPACE 指定的，所以第一个判断肯定是对的
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 1.判断 loginType，仅对类型为 "space" 进行权限校验，我们项目现在运用的是多套权限校验体系，现在这个方法是对空间账号体系进行校验
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }

        // 2.先定义一个管理员所拥有的权限列表
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());

        // 3.从请求中获取上下文对象，也就是当前拦截请求所具有的参数
        SpaceUserAuthContext authContext = getAuthContextByRequest();

        // 4.如果所有字段都为空，表示的就是不需要进行权限校验的接口，因为如果需要权限校验的话，是肯定会传参数的，例如你操作图片，接口中肯定有 pictureId
        // 如果这个接口访问的时候，都没有参数，怎么鉴权，如果越权的话，需要越哪些权呢？
        // 需要检查上下文字段是否为空，如果上下文所有字段都为空，就表示该接口不是编辑操作，这个接口就不需要权限的，可能就是浏览公共图库
        // 这个时候就直接返回管理员的所有权限列表
        if (isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSIONS;
        }

        // 如果所有字段不为空，表示要进行权限校验
        // 5.获取 userId ，从 sa-token 的 session 中获取用户信息，这是我们登录的时候存的用户信息
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        // 5.1.获取到当前登录用户的 ID
        Long userId = loginUser.getId();

        // 6.优先从上下文中获取 SpaceUser 对象，根据 SpaceRole 获取权限列表
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }

        // 7.如果上下文中有 spaceUserId，必然是团队空间，通过数据库查询 SpaceUser 对象
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null) {
            spaceUser = spaceUserService.getById(spaceUserId);
            if (spaceUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户所关联的信息");
            }
            // 7.1.取出当前登录用户对应的 spaceUser ，因为有可能上面查询到的 SpaceUser 对象不是当前登录人的，所以下面还需再判断一次
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (loginSpaceUser == null) {
                // 啥权限都没有
                return new ArrayList<>();
            }
            // TODO 这里不是很懂
            // 这里会导致管理员在私有空间没有权限，可以再查一次库，根据角色拿到权限列表
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }

        // 8. 如果没有 spaceUserId ，尝试通过 spaceId 或 pictureId 获取 Space 对象继续进行判断
        Long spaceId = authContext.getSpaceId();
        // 8.1.如果没有 spaceId ，那可能就是公共图库了，或者说传信息的时候没有传 spaceId ，只能通过 pictureId 获取 Picture 对象再获取到 Space 对象
        if (spaceId == null) {
            Long pictureId = authContext.getPictureId();
            // 8.1.1.pictureId 也没有，那就表示这个接口也是不需要进行校验了，就默认返回所有的管理员权限
            if (pictureId == null) {
                return ADMIN_PERMISSIONS;
            }
            // 8.1.2.pictureId 有，则通过 pictureId 获取 Picture
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId) // 只需要查询这两列就行
                    .one();
            // 8.1.3.如果 Picture 对象为空，则表示没有该图片，就返回错误
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            // 8.1.4.如果 Picture 存在，则通过 Picture 对象获取 SpaceId
            spaceId = picture.getSpaceId();
            // 8.1.5.spaceId 为空，但是传了 pictureId 代表这个图片属于公共图库，那么就仅本人或管理员可操作
            if (spaceId == null) {
                // 如果是 管理员 或者是 本人图片 ，那么就返回所有权限，crud
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    // 不是自己的图片，仅可查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }

        // 8.2.存在 SpaceId，通过 SpaceId 获取对应的 Space 对象
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        // 根据 Space 类型判断权限
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，仅本人或管理员有权限
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                // 否则就啥权限没有，公共图库还有查看的权限，私有空间就啥权限没有
                return new ArrayList<>();
            }
        } else {
            // 团队空间，根据 spaceId 和 userId 查询 SpaceUser 并获取角色和权限
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (spaceUser == null) {
                // 啥权限都没有
                return new ArrayList<>();
            }
            // 根据 spaceUser 的角色获取权限
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }


    /**
     * 从请求中获取上下文对象
     * 通过请求对象获取到上下文
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        // 1.首先获取到请求对象，转换成我们所熟知的 HttpServletRequest 对象
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = servletRequestAttributes.getRequest();

        // 2.从请求头中获取 Content-Type，用于区分不同请求类型（GET/POST等）的参数解析方式
        //   例如：GET请求通常无Content-Type，POST请求可能是 application/json 或 x-www-form-urlencoded
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;

        // 3.1.获取请求参数
        // 3.2.如果我们的请求参数是 JSON 类型
        if(ContentType.JSON.getValue().equals(contentType)){
            // 3.2.1.如果是 Json ，通过这个工具类直接根据请求对象拿到所有的请求体
            // todo 请求体（body）的数据流（InputStream）默认只能读取一次，所以在配置类中需要配置来解决这个问题
            String body = ServletUtil.getBody(request); // 如果是 JSON 的话，这个就是一个 JSON
            // 我们就可以直接把 JSON 字符串转换成我们需要的上下文对象 SpaceUserAuthContext
            // 工具会尝试根据 JSON 的键（key）与目标对象的属性名自动匹配并赋值
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        }else{
            // 3.3.如果是 get 请求，get 请求是没有 json 对象的，获取所有请求参数到一个 Map 集合
            Map<String, String> paramMap = ServletUtil.getParamMap(request);  // Key 和 Value 都是 String 的 Map
            // 将得到的 Map 转换成我们需要的上下文对象
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }

        // 4.根据请求路径区分 id 字段的含义
        // 4.1.传的参数有 id 才进行区分
        Long id = authRequest.getId();
        if(ObjUtil.isNotNull(id)){
            // 4.2.获取到请求路径的业务前缀，先获取到完整的请求路径  /api/picture/aaa?a=1
            String requestURI = request.getRequestURI();
            // 4.2.1.将 /api/ 去掉，剩下的就是前缀了 picture/aaa?a=1
            String partURI = requestURI.replace(contextPath + "/", "");
            // 4.2.2.第一个斜杠前面的就是我们要的业务前缀
            String moduleName = StrUtil.subBefore(partURI, "/", false);
            switch (moduleName) {
                case "picture":
                    // 如果是 picture ，那么 id 就是 pictureId
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    // 如果是 spaceUser ，那么 id 就是 spaceUserId
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    // 如果是 space ，那么 id 就是 spaceId
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        // 5.获得的上下文参数传递出去
        return authRequest;
    }


    /**
     * 判断该对象中的所有字段是否都为空，‌通过反射动态的获取对象的所有字段，进行判空‍
     * 需要检查上下文字段是否为空，如果上下文所有字段都为空，就表示权限中不需要编辑操作，可能就是浏览公共图库
     * 这个时候就直接返回管理员的所有权限列表
     */
    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass())) //首先用反射，获取到这个对象所有的字段信息
                // 获取字段值，遍历字段的值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty); //全为空的话，就返回 true
    }


    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     * 这个方法是，如果我们项目中有的接口是只允许某些特定角色访问，才需要实现这个方法
     * 本项目的权限认证是直接设置权限的，有特定权限直接访问，没有通过角色来进行限制
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>();
    }
}

