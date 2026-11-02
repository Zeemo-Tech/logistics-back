package org.example.logisticsback.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.example.logisticsback.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;
    public SessionService(@Value("${app.auth.session-ttl:PT8H}") Duration ttl) { this.ttl = ttl; }
    public String create(Long userId) {
        var token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(userId, Instant.now().plus(ttl)));
        return token;
    }
    public Long requireUserId(String token) {
        if (token == null || token.isBlank()) throw new BusinessException(401, "请先登录");
        var session = sessions.get(token);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            throw new BusinessException(401, "登录已过期，请重新登录");
        }
        return session.userId();
    }
    public void remove(String token) { if (token != null) sessions.remove(token); }
    public record Session(Long userId, Instant expiresAt) {}
}
