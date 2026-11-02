package org.example.logisticsback.user;

import java.util.Optional;

public interface UserRepository {
    Optional<UserAccount> findById(Long id);
    Optional<UserAccount> findByUsername(String username);
    UserAccount save(UserAccount user);
    boolean existsByUsername(String username);
}
