package com.sky.controller;

import com.sky.properties.AliOssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 图片下载接口，兼容小程序端 /common/download?name=xxx 的访问方式
 */
@RestController
@RequestMapping("/common")
@Slf4j
public class CommonDownloadController {

    @Autowired
    private AliOssProperties aliOssProperties;

    /**
     * 根据图片名下载图片
     * name 是完整地址时直接读取，否则按 OSS 桶拼接地址读取
     *
     * @param name     图片地址或文件名
     * @param response HTTP响应
     */
    @GetMapping("/download")
    public void download(@RequestParam String name, HttpServletResponse response) throws IOException {
        String url = name;
        if (!name.startsWith("http://") && !name.startsWith("https://")) {
            url = "https://" + aliOssProperties.getBucketName() + "." + aliOssProperties.getEndpoint() + "/" + name;
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try (InputStream in = connection.getInputStream();
             OutputStream out = response.getOutputStream()) {
            String contentType = connection.getContentType();
            if (contentType != null) {
                response.setContentType(contentType);
            }
            response.setHeader("Content-Disposition", "inline");
            response.setHeader("Cache-Control", "public, max-age=3600");
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
        } finally {
            connection.disconnect();
        }
    }
}
