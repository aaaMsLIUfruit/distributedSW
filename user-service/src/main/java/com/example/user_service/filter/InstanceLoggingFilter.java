package com.example.user_service.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 负载均衡验证用：为每个请求添加实例标识头，并记录日志，便于验证请求是否均衡分配到各实例。
 */
@Component
@Order(1)
public class InstanceLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(InstanceLoggingFilter.class);

    @Value("${server.port:8081}")
    private String serverPort;

    @Value("${instance.id:${server.port}}")
    private String instanceId;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        resp.setHeader("X-Instance-Port", serverPort);
        resp.setHeader("X-Instance-Id", instanceId);

        log.info("[Instance-{}] {} {}", instanceId, req.getMethod(), req.getRequestURI());

        chain.doFilter(request, response);
    }
}
