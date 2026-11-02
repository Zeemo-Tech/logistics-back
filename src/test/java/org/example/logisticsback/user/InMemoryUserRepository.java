package org.example.logisticsback.user;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.example.logisticsback.auth.PasswordHasher;

class InMemoryUserRepository implements UserRepository {
    private final Map<Long, UserAccount> users = new ConcurrentHashMap<>();

    InMemoryUserRepository(PasswordHasher hasher) {
        users.put(1L, new UserAccount(
                1L, "admin", hasher.hash("123456"), "系统管理员", "百川管理员", "",
                java.util.List.of("admin")));
    }

    @Override
    public Optional<UserAccount> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return users.values().stream()
                .filter(user -> user.getUsername().equalsIgnoreCase(username))
                .findFirst();
    }

    @Override
    public UserAccount save(UserAccount user) {
        users.put(user.getId(), user);
        return user;
    }

    @Override
    public boolean existsByUsername(String username) {
        return users.values().stream()
                .anyMatch(user -> user.getUsername().equalsIgnoreCase(username));
    }
}
