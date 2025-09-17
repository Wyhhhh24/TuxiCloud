package com.air.yunpicturebackend;

import com.air.yunpicturebackend.manager.CosManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class YunPictureBackendApplicationTests {

    @Resource
    private CosManager cosManager;
    @Test
    void contextLoads() {
    }


    @Test
    void test(){
        cosManager.deleteObject("/public/1960965073795543041/2025-09-15_BYOM3TX71uVaHLTh.webp");
    }
}
