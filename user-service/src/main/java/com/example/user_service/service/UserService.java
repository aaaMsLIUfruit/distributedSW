package com.example.user_service.service;

import com.example.user_service.dto.LoginRequest;
import com.example.user_service.dto.RegisterRequest;
import com.example.user_service.vo.UserLoginVO;

public interface UserService {
    Long register(RegisterRequest request);

    UserLoginVO login(LoginRequest request);
}
