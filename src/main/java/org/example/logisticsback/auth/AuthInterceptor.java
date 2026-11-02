package org.example.logisticsback.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String USER_ID_ATTRIBUTE = "authenticatedUserId";
    private final SessionService sessions;
    public AuthInterceptor(SessionService sessions) { this.sessions = sessions; }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/api/user/login".equals(path)
                || "/api/health".equals(path)) {
            return true;
        }
        var token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
        if (token == null || token.isBlank()) token = request.getHeader("token");
        request.setAttribute(USER_ID_ATTRIBUTE, sessions.requireUserId(token));
        return true;
    }
}
