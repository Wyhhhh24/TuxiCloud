package com.air.yunpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadRequest implements Serializable {
  
    /**
     * 由于图片需要支持重复上传（基础信息不变，只改变图片文件），所以要添加图片 id 参数
     * 图片 id（用于修改）  
     */  
    private Long id;

    /**
     * 上传图片的 url
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 空间id
     * 我们之前上传图片是不区分上传到哪个空间的，现在用户有了自己的空间，上传图片的时候可以指定空间Id了
     */
    private Long spaceId;


    private static final long serialVersionUID = 1L;
}
