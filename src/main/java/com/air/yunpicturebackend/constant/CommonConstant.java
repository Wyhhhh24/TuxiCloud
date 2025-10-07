package com.air.yunpicturebackend.constant;

import java.util.Arrays;
import java.util.List;

/**
 * @author WyH524
 * @since 2025/9/5 上午11:03
 */
public interface CommonConstant {

    /**
     * 随机用户昵称
     */
    List<String> USER_NICK_NAME_PREFIX = List.of("咖啡不加糖","熬夜冠军","西瓜味的夏天","走路带风","发呆专业户"
            ,"爱吃薯片的猫","周末不上班","懒人小助手","快乐小肥宅","今天也要加油鸭");

    /**
     * 默认用户头像 URL
     */
    String USER_AVATAR_URL = "https://air-wyh-1360725635.cos.ap-guangzhou.myqcloud.com/%E9%BB%98%E8%AE%A4%E7%94%A8%E6%88%B7%E5%A4%B4%E5%83%8F.png";

    /**
     * 允许上传的文件类型
     */
    List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");

    /**
     * 允许的图片类型
     * HTTP 协议和浏览器的约定 当文件通过 HTTP 上传时
     * Content-Type 请求头会携带完整的 MIME 类型（如 image/jpeg）
     * 如果校验时只写 "jpeg" 或 "jpg"，会导致匹配失败，多写其它匹配项。
     */
    List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg",
            "image/png", "image/webp");
}
