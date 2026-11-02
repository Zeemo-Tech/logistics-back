package org.example.logisticsback.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class UserDtos {
    private UserDtos() {}
    public record LoginRequest(@NotBlank(message = "账号不能为空") String username,
                               @NotBlank(message = "密码不能为空") String password) {}
    public record LoginResponse(String token, UserInfo userInfo) {}
    public record UserInfo(Long id, String username, String nickname, String realName,
                           String phone, List<String> roles, List<String> permissions) {}
    public record UpdateProfileRequest(@NotBlank(message = "账号不能为空") @Size(max = 32, message = "账号长度不能超过32位") String username,
                                       @NotBlank(message = "显示姓名不能为空") @Size(max = 32, message = "姓名长度不能超过32位") String realName,
                                       @NotBlank(message = "昵称不能为空") @Size(max = 32, message = "昵称长度不能超过32位") String nickname,
                                       @Size(max = 20, message = "联系电话长度不能超过20位") String phone) {}
    public record ChangePasswordRequest(@NotBlank(message = "当前密码不能为空") String currentPassword,
                                        @NotBlank(message = "新密码不能为空") @Size(min = 6, max = 64, message = "新密码长度需为6至64位") String nextPassword) {}
}
