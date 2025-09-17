package com.air.yunpicturebackend.constant;

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
}
