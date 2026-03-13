package com.example.user_service.controller;

import com.example.user_service.common.Result;
import com.example.user_service.dto.LoginRequest;
import com.example.user_service.dto.RegisterRequest;
import com.example.user_service.service.UserService;
import com.example.user_service.vo.UserLoginVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
