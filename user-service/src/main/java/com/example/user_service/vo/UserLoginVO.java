package com.example.user_service.vo;

import lombok.Data;

@Data
public class UserLoginVO {
    private Long userId;
    private String username;
    private String token;
}
