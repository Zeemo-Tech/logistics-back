package org.example.logisticsback.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.logisticsback.auth.AuthInterceptor;
import org.example.logisticsback.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users = users; }

    @PostMapping("/login")
    public ApiResponse<UserDtos.LoginResponse> login(@Valid @RequestBody UserDtos.LoginRequest request) {
        return ApiResponse.ok(users.login(request), "登录成功");
    }

    @GetMapping("/info")
    public ApiResponse<UserDtos.UserInfo> info(HttpServletRequest request) {
        return ApiResponse.ok(users.getInfo(userId(request)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        var token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
        if (token == null || token.isBlank()) token = request.getHeader("token");
        users.logout(token);
        return ApiResponse.ok(null, "已退出登录");
    }

    @PutMapping("/profile")
    public ApiResponse<UserDtos.UserInfo> updateProfile(HttpServletRequest request,
                                                         @Valid @RequestBody UserDtos.UpdateProfileRequest body) {
        return ApiResponse.ok(users.updateProfile(userId(request), body), "个人资料已保存");
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(HttpServletRequest request,
                                            @Valid @RequestBody UserDtos.ChangePasswordRequest body) {
        users.changePassword(userId(request), body);
        return ApiResponse.ok(null, "密码已更新");
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
    }
}
