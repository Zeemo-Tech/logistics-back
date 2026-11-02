package org.example.logisticsback.user;

import org.example.logisticsback.auth.PasswordHasher;
import org.example.logisticsback.auth.SessionService;
import org.example.logisticsback.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final SessionService sessions;
    public UserService(UserRepository users, PasswordHasher hasher, SessionService sessions) {
        this.users = users; this.hasher = hasher; this.sessions = sessions;
    }
    public UserDtos.LoginResponse login(UserDtos.LoginRequest request) {
        var user = users.findByUsername(request.username().trim()).orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!hasher.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        return new UserDtos.LoginResponse(sessions.create(user.getId()), toInfo(user));
    }
    public UserDtos.UserInfo getInfo(Long userId) { return toInfo(find(userId)); }
    public void logout(String token) { sessions.remove(token); }
    @Transactional
    public UserDtos.UserInfo updateProfile(Long userId, UserDtos.UpdateProfileRequest request) {
        var user = find(userId);
        var username = request.username().trim();
        if (!username.equalsIgnoreCase(user.getUsername()) && users.existsByUsername(username)) {
            throw new BusinessException(409, "登录账号已存在");
        }
        user.setUsername(username); user.setRealName(request.realName().trim());
        user.setNickname(request.nickname().trim()); user.setPhone(request.phone() == null ? "" : request.phone().trim());
        return toInfo(users.save(user));
    }
    @Transactional
    public void changePassword(Long userId, UserDtos.ChangePasswordRequest request) {
        var user = find(userId);
        if (!hasher.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(400, "当前密码不正确");
        }
        user.changePassword(hasher.hash(request.nextPassword()));
        users.save(user);
    }
    private UserAccount find(Long id) { return users.findById(id).orElseThrow(() -> new BusinessException(404, "用户不存在")); }
    private UserDtos.UserInfo toInfo(UserAccount user) {
        return new UserDtos.UserInfo(user.getId(), user.getUsername(), user.getNickname(), user.getRealName(), user.getPhone(), user.getRoles(), java.util.List.of("*"));
    }
}
