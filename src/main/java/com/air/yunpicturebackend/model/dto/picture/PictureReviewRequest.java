package com.air.yunpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 请求包装类
 * 不需要增加 reviewerId 审核人id 和‌ reviewTime 审核时间，这两个是由系统自动‍填充的，而不是由前端传递
 * reviewId是当前登录用户
 */
@Data
public class PictureReviewRequest implements Serializable {
  
    /**  
     * id  图片id
     */  
    private Long id;
  
    /**  
     * 状态：0-待审核, 1-通过, 2-拒绝  
     */  
    private Integer reviewStatus;  
  
    /**  
     * 审核信息  
     */  
    private String reviewMessage;
  
  
    private static final long serialVersionUID = 1L;  
}
