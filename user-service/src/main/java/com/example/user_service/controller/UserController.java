package com.example.user_service.controller;

import com.example.user_service.common.Result;
import com.example.user_service.dto.LoginRequest;
import com.example.user_service.dto.RegisterRequest;
import com.example.user_service.service.UserService;
import com.example.user_service.vo.UserLoginVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Value("${server.port:8081}")
    private String serverPort;

    @Value("${instance.id:${server.port}}")
    private String instanceId;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 负载均衡压测用：轻量接口，快速返回实例标识，便于验证请求分配。
     */
    @GetMapping("/ping")
    public Result<Map<String, String>> ping() {
        return Result.success(Map.of("instance", instanceId, "port", serverPort, "status", "ok"));
    }

    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = userService.register(request);
        return Result.success(userId);
    }

    @PostMapping("/login")
    public Result<UserLoginVO> login(@Valid @RequestBody LoginRequest request) {
        UserLoginVO vo = userService.login(request);
        return Result.success(vo);
    }
}
