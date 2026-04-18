package com.example.user_service.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.example.user_service.common.Result;
import com.example.user_service.config.UserServiceSentinelConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 流量治理压测用接口：服务侧限流（blockHandler）、异常降级（fallback）、慢调用（配合网关熔断）。
 */
@RestController
@RequestMapping("/api/users")
public class UserTrafficDemoController {

    @GetMapping("/stress/hot")
    @SentinelResource(
            value = UserServiceSentinelConfiguration.STRESS_HOT_RESOURCE,
            blockHandler = "stressHotBlock",
            fallback = "stressHotFallback")
    public Result<Map<String, Object>> stressHot(
            @RequestParam(name = "fail", defaultValue = "false") boolean fail) {
        if (fail) {
            throw new IllegalStateException("模拟业务异常");
        }
        return Result.success(Map.of("resource", UserServiceSentinelConfiguration.STRESS_HOT_RESOURCE, "ok", true));
    }

    @SuppressWarnings("unused")
    public Result<Map<String, Object>> stressHotBlock(boolean fail, BlockException ex) {
        return Result.fail("服务侧 Sentinel 限流（blockHandler）");
    }

    @SuppressWarnings("unused")
    public Result<Map<String, Object>> stressHotFallback(boolean fail, Throwable t) {
        return Result.fail("服务侧异常/熔断降级（fallback）：" + t.getMessage());
    }

    /**
     * 故意拉高 RT，便于经网关访问时触发网关对 {@code user-service} 路由的慢调用熔断规则。
     */
    @GetMapping("/demo/slow")
    public Result<Map<String, Object>> slow(@RequestParam(name = "ms", defaultValue = "200") long ms)
            throws InterruptedException {
        long sleep = Math.min(Math.max(ms, 0), 3000);
        Thread.sleep(sleep);
        return Result.success(Map.of("sleptMs", sleep));
    }
}
