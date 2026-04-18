package com.example.user_service.controller;

import com.example.user_service.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示从 Nacos 读取可刷新配置：控制台修改 demo.greeting 并发布后，本接口返回值随之变化，无需重启进程。
 */
@RestController
@RequestMapping("/api/users")
@RefreshScope
public class UserDynamicConfigController {

    @Value("${demo.greeting:Hello}")
    private String greeting;

    @GetMapping("/demo/config")
    public Result<Map<String, String>> demoConfig() {
        return Result.success(Map.of("demo.greeting", greeting));
    }
}
