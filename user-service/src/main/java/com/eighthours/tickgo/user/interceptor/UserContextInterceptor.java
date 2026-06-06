package com.eighthours.tickgo.user.interceptor;

import com.eighthours.tickgo.user.context.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class UserContextInterceptor implements HandlerInterceptor {

    private static final String X_USER_ID = "X-User-Id";
    private static final Long DEFAULT_USER_ID = 1L;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdStr = request.getHeader(X_USER_ID);
        Long userId = DEFAULT_USER_ID;

        if (userIdStr != null && !userIdStr.trim().isEmpty()) {
            try {
                userId = Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                // 忽略，使用默认值
            }
        }

        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
