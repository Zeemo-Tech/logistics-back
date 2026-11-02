package org.example.logisticsback.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.example.logisticsback.auth.PasswordHasher;
import org.example.logisticsback.auth.SessionService;
import org.example.logisticsback.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserServiceTests {
    private UserService service;

    @BeforeEach
    void setUp() {
        var hasher = new PasswordHasher();
        service = new UserService(new InMemoryUserRepository(hasher), hasher, new SessionService(Duration.ofHours(8)));
    }

    @Test
    void loginAndProfileWorkflow() {
        var login = service.login(new UserDtos.LoginRequest("admin", "123456"));
        assertEquals("admin", login.userInfo().username());

        var updated = service.updateProfile(login.userInfo().id(),
                new UserDtos.UpdateProfileRequest("operator", "运营负责人", "负责人", "13800138000"));
        assertEquals("operator", updated.username());
        assertEquals("运营负责人", updated.realName());
    }

    @Test
    void changingPasswordInvalidatesOldCredential() {
        var userId = service.login(new UserDtos.LoginRequest("admin", "123456")).userInfo().id();
        service.changePassword(userId, new UserDtos.ChangePasswordRequest("123456", "NewPassword8"));

        assertThrows(BusinessException.class,
                () -> service.login(new UserDtos.LoginRequest("admin", "123456")));
        assertEquals("admin", service.login(new UserDtos.LoginRequest("admin", "NewPassword8")).userInfo().username());
    }
}
