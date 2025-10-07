package com.air.yunpicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新空间请求，给管理员使用，可以修改空间级别和限额
 * 其实这里修改空间的级别的话，用户可以申请通知管理员，然后管理员进行修改空间的级别
 * 这个空间的大小，以及图片上传的数量，用户可以买扩容包啥的进行扩容，这里管理员直接修改即可，空间的级别和大小不是绑定的，其实可以进行微调的
 * 所以我们数据库没有直接定死是比较好的，动态的
 */
@Data
public class SpaceUpdateRequest implements Serializable {
    /**
     * id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间图片的最大总大小
     */
    private Long maxSize;

    /**
     * 空间图片的最大数量
     */
    private Long maxCount;

    private static final long serialVersionUID = 1L;
}
