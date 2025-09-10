package com.air.yunpicturebackend.model.dto.picture;

import com.air.yunpicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class PictureQueryRequest extends PageRequest implements Serializable {
  
    /**  
     * id  
     */  
    private Long id;  
  
    /**  
     * 图片名称  
     */  
    private String name;  
  
    /**  
     * 简介  
     */  
    private String introduction;  
  
    /**  
     * 分类  
     */  
    private String category;  
  
    /**  
     * 标签  
     */  
    private List<String> tags;
  
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
     * 搜索词（同时搜名称、简介等，也就是这个搜索词，是可以对名称和简介进行搜索的）
     */  
    private String searchText;
  
    /**  
     * 用户 id  
     */  
    private Long userId;

    /**
     * 空间id，允许用户查询某一个空间的图片
     */
    private Long spaceId;

    /**
     * 假如说用户想要查询公共图库的图片
     * 主页只展示公共图库的图片，那应该怎么查询呢？
     * 那就不是查询整个图片表了吧，而是查询 spaceId 为 null 的数据
     * 如果我们想查询 spaceId 为 null 的数据，spaceId 不传的话，spaceId 就不会作为查询条件了，相当于把整个表都查了
     * 所以我们增加这个参数
     *
     *
     * 是否只查询 spaceId 为 null 的数据 ，true 的话就是查询公共图库中的图片，如果查询某一个空间，就指定上面 spaceId
     */
    private boolean nullSpaceId;


    /**
     * 状态：0-待审核; 1-通过; 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 审核人 id
     */
    private Long reviewerId;


    private static final long serialVersionUID = 1L;
}
