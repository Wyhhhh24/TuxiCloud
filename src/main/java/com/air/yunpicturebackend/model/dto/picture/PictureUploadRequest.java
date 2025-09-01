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
  
    private static final long serialVersionUID = 1L;
}
