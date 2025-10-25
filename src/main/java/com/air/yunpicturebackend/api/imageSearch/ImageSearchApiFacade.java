package com.air.yunpicturebackend.api.imageSearch;

import com.air.yunpicturebackend.api.imageSearch.model.ImageSearchResult;
import com.air.yunpicturebackend.api.imageSearch.sub.GetImageFirstUrlApi;
import com.air.yunpicturebackend.api.imageSearch.sub.GetImageListApi;
import com.air.yunpicturebackend.api.imageSearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * @author WyH524
 * @since 2025/9/14 下午1:23
 */
@Slf4j
public class ImageSearchApiFacade {

    /**
     * 以图搜图
     *
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://air-wyh-1360725635.cos.ap-guangzhou.myqcloud.com/public/1960965073795543041/2025-09-05_3Um2GZUqjnoCkpLM_thumbnail.fDDjkYkZrK-nRckT6J6s4gHaLH";
        List<ImageSearchResult> resultList = searchImage(imageUrl);
        System.out.println("结果列表" + resultList);
    }
}

