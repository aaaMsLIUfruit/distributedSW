package com.example.user_service.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * 服务侧 Sentinel 限流规则（配合 {@code @SentinelResource} 资源名）。
 */
@Configuration
public class UserServiceSentinelConfiguration {

    public static final String STRESS_HOT_RESOURCE = "userStressHot";

    @Bean
    public ApplicationRunner userServiceSentinelFlowRules(
            @Value("${traffic.sentinel.user-service.hot-resource-qps:8}") double hotQps) {
        return args -> {
            FlowRule rule = new FlowRule(STRESS_HOT_RESOURCE);
            rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
            rule.setCount(hotQps);
            List<FlowRule> rules = Collections.singletonList(rule);
            FlowRuleManager.loadRules(rules);
        };
    }
}
