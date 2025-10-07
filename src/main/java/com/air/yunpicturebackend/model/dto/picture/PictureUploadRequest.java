package com.air.yunpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadRequest implements Serializable {
  
    /**
     * 由于图片需要支持重复上传（基础信息不变，只改变图片文件），所以要添加图片 id 参数
     * 图片 id（用于修改该条 Picture 记录）
     */  
    private Long id;

    /**
     * 上传图片的 url ，这个参数在上传图片的时候是为 null 的，只有在通过 url 上传图片才不为 null
     */
    private String fileUrl;

    /**
     * 图片名称，这里是用户指定的图片名称
     */
    private String picName;

    /**
     * 空间id
     * 用户可以上传到公共图库（空间 id 为 null）
     * 可以上传到私有空间以及团队空间（空间 id 不为 null）
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}
