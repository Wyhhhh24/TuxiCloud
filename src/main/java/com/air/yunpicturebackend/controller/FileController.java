package com.air.yunpicturebackend.controller;

import com.air.yunpicturebackend.annotation.AuthCheck;
import com.air.yunpicturebackend.common.BaseResponse;
import com.air.yunpicturebackend.common.ResultUtils;
import com.air.yunpicturebackend.constant.UserConstant;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.manager.CosManager;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * @author WyH524
 * @since 2025/8/31 下午8:45
 */
@RestController
@RequestMapping
@Slf4j
public class FileController {

    @Resource
    private CosManager cosManager;

    /**
     * 测试文件上传
     *
     * @param multipartFile
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)  //只有管理员可以访问
    @PostMapping("/test/upload")            //接收前端表单传过来的文件，表单参数名称“file”
    public BaseResponse<String> testUploadFile(@RequestPart("file") MultipartFile multipartFile) {
        // 得到最终要存储的文件地址，像： /test/example.jpg
        String filename = multipartFile.getOriginalFilename();
        String filepath = String.format("/test/%s", filename);
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(filepath, null); //创建一个临时文件
            //将 MultipartFile 类型的文件转成 File 类型文件
            multipartFile.transferTo(file);
            cosManager.putObject(filepath, file);
            // 返回 “/test/qqcion.png” 像这样的地址，可以与我们的域名进行拼接就可以有可访问的地址了
            return ResultUtils.success(filepath);

        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (file != null) {
                // 我们在本地创建了一个临时文件
                // 如果不删除，那不就是每上传一次，服务器就被占用了一个坑，文件上传成功了之后，得要把本地临时文件删除掉
                // 删除临时文件
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }
    }


    /**
     * 测试文件下载
     *
     * @param filepath 文件路径
     * @param response 响应对象
     * 核心流程是根据路؜径获取到 COS 文件对象，然后将文件对象转换为文件流
     * 并写‌入到 Servlet 的 Response 对象中。注意要设‍置文件下载专属的响应头。
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download/")  //这里的返回值为void，控制返回结果是通过响应体控制的
    public void testDownloadFile(String filepath, HttpServletResponse response) throws IOException {
        COSObjectInputStream cosObjectInput = null;
        try {
            //得到一个文件的输入流，就可以下载到前端了
            COSObject cosObject = cosManager.getObject(filepath);
            cosObjectInput = cosObject.getObjectContent();
            // 处理下载到的流，流转为字节数组
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);

            // 设置响应头，我们怎么控制前端是下载文件，还是看文件呢，就看这个响应头，浏览器看到这个头就知道自己要下载文件了
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filepath);
            // 写入响应（从输入流读，往输出流写）
            response.getOutputStream().write(bytes);
            // 缓冲区不是直接返回的，而是得要刷新才能返回，把内容刷新到响应体中
            response.getOutputStream().flush();

        } catch (Exception e) {
            log.error("file download error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            if (cosObjectInput != null) {
                //最后都是要释放流
                cosObjectInput.close();
            }
        }
    }

}
