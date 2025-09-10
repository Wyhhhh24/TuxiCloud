package com.air.yunpicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间创建请求
 */
@Data
public class SpaceAddRequest implements Serializable {

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     * 其实在我们的系统中，应该是只有管理员才可以创建专业版和旗舰版的空间的
     * 其实也可以开通一些付费的业务，让用户可以自己开通专业版，旗舰版空间
     */
    private Integer spaceLevel;

    private static final long serialVersionUID = 1L;
}
