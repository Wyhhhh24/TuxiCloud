package com.air.yunpicturebackend.model.dto.file;

import lombok.Data;

/**
 * 上传图片的结果，当我们调用玩上传图片的接口接口之后，就会得到这个类了
 * 我们图片，文件的解析之后，来接收解析之后的宽高，各种各样的参数，我们肯定想要拿一个类
 */
@Data
public class UploadPictureResult {  
  
    /**  
     * 图片地址  
     */  
    private String url;  
  
    /**  
     * 图片名称  
     */  
    private String picName;  
  
    /**  
     * 文件大小
     */  
    private Long picSize;
  
    /**  
     * 图片宽度  
     */  
    private int picWidth;  
  
    /**  
     * 图片高度  
     */  
    private int picHeight;  
  
    /**  
     * 图片宽高比  
     */  
    private Double picScale;  
  
    /**  
     * 图片格式  
     */  
    private String picFormat;
}
