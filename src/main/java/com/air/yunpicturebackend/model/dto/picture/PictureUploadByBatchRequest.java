package com.air.yunpicturebackend.model.dto.picture;

import lombok.Data;

@Data
public class PictureUploadByBatchRequest {  
  
    /**  
     * 搜索词  
     */  
    private String searchText;

    /**
     * 管理员在抓取图片的时候，指定一个图片名称前缀，如果管理员不传，默认就等于搜索词
     * 名称前缀
     */
    private String namePrefix;


    /**  
     * 抓取数量  
     */  
    private Integer count = 10;
}
