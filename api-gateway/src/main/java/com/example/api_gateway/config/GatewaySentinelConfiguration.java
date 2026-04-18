package com.example.api_gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 网关侧流量治理：路由级 QPS 限流、慢调用 RT 熔断，以及触发规则时的统一降级响应。
 * 阈值可通过配置调整，便于 JMeter 压测复现。
 */
@Configuration
public class GatewaySentinelConfiguration {

    public static final String USER_SERVICE_ROUTE_ID = "user-service";

    @Bean
    public BlockRequestHandler sentinelGatewayBlockRequestHandler() {
        return (exchange, ex) -> {
            String reason;
            if (ex instanceof FlowException) {
                reason = "FLOW";
            } else if (ex instanceof DegradeException) {
                reason = "DEGRADE";
            } else {
                reason = ex.getClass().getSimpleName();
            }
            Map<String, Object> body = new HashMap<>(4);
            body.put("code", 429);
            body.put("message", "网关 Sentinel：触发限流或熔断，已降级");
            body.put("reason", reason);
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body);
        };
    }

    @Bean
    public ApplicationRunner sentinelGatewayRules(
            @Value("${traffic.sentinel.gateway.user-route.flow-qps:120}") double userRouteFlowQps,
            @Value("${traffic.sentinel.gateway.user-route.degrade-slow-rt-ms:280}") int degradeSlowRtMs,
            @Value("${traffic.sentinel.gateway.user-route.degrade-min-requests:8}") int degradeMinRequests,
            @Value("${traffic.sentinel.gateway.user-route.degrade-time-window-sec:10}") int degradeTimeWindowSec) {
        return args -> {
            Set<GatewayFlowRule> flowRules = new HashSet<>();
            GatewayFlowRule flow = new GatewayFlowRule(USER_SERVICE_ROUTE_ID)
                    .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                    .setGrade(RuleConstant.FLOW_GRADE_QPS)
                    .setCount(userRouteFlowQps)
                    .setIntervalSec(1);
            flowRules.add(flow);
            GatewayRuleManager.loadRules(flowRules);

            DegradeRule degrade = new DegradeRule(USER_SERVICE_ROUTE_ID)
                    .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                    .setCount(degradeSlowRtMs)
                    .setTimeWindow(degradeTimeWindowSec)
                    .setMinRequestAmount(degradeMinRequests);
            DegradeRuleManager.loadRules(List.of(degrade));
        };
    }
}
