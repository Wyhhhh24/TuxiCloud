package com.air.yunpicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 编辑空间请求，给用户使用，目前仅允许编辑空间名称
 * 这里用户是不允许修改空间级别的，只有管理员才可以修改空间级别，等后续扩展
 */
@Data
public class SpaceEditRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    private static final long serialVersionUID = 1L;
}
