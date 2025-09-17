package com.air.yunpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 以图搜图请求类
 */
@Data
public class SearchPictureByPictureRequest implements Serializable {

    /**
     * 图片 id ，前端只需要传一个pictureId就好了，我们的图片是存到后端系统中的，只要通过pictureId就可以拿到图片的 url 地址
     */
    private Long pictureId;

    private static final long serialVersionUID = 1L;
}
