package com.air.yunpicturebackend.model.vo;

import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.model.entity.Picture;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class PictureVO implements Serializable {
  
    /**  
     * id  
     */  
    private Long id;  
  
    /**  
     * 图片 url  
     */  
    private String url;  
  
    /**  
     * 图片名称（原始名称）
     */  
    private String name;  
  
    /**  
     * 简介  
     */  
    private String introduction;  
  
    /**  
     * 标签  这里我们返回给前端的时候，为了给前端方便，我们可以帮前端转化为列表
     * 所以在实体类和VO对象相互转换的时候，这里属性拷贝是不成功的，这里自行转换
     */  
    private List<String> tags;
  
    /**  
     * 分类  
     */  
    private String category;  
  
    /**  
     * 文件体积  
     */  
    private Long picSize;  
  
    /**  
     * 图片宽度  
     */  
    private Integer picWidth;  
  
    /**  
     * 图片高度  
     */  
    private Integer picHeight;  
  
    /**  
     * 图片比例  
     */  
    private Double picScale;  
  
    /**  
     * 图片格式  
     */  
    private String picFormat;  
  
    /**  
     * 用户 id  
     */  
    private Long userId;
  
    /**  
     * 创建时间  
     */  
    private Date createTime;
  
    /**  
     * 编辑时间  
     */  
    private Date editTime;  
  
    /**  
     * 更新时间  
     */  
    private Date updateTime;
  
    /**  
     * 创建用户信息  
     */  
    private UserVO user;  
  
    private static final long serialVersionUID = 1L;  

    //为了实体类和 VO 对象之间更灵活的转换，这里添加两个方法
    /**  
     * 封装类转对象  
     */  
    public static Picture voToObj(PictureVO pictureVO) {
        if (pictureVO == null) {  
            return null;  
        }  
        Picture picture = new Picture();  
        BeanUtils.copyProperties(pictureVO, picture);
        // 类型不同，需要转换  
        picture.setTags(JSONUtil.toJsonStr(pictureVO.getTags()));
        return picture;  
    }
  
    /**  
     * 对象转封装类  
     */  
    public static PictureVO objToVo(Picture picture) {  
        if (picture == null) {  
            return null;  
        }  
        PictureVO pictureVO = new PictureVO();  
        BeanUtils.copyProperties(picture, pictureVO);  
        // 类型不同，需要转换  
        pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));  
        return pictureVO;  
    }
}
