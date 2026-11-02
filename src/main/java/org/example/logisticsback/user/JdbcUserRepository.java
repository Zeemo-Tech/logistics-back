package org.example.logisticsback.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.example.logisticsback.auth.PasswordHasher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserRepository implements UserRepository {
    private final JdbcTemplate jdbc;

    public JdbcUserRepository(JdbcTemplate jdbc, PasswordHasher hasher) {
        this.jdbc = jdbc;
        if (jdbc.queryForObject("select count(*) from users", Integer.class) == 0) {
            jdbc.update(
                    """
                    insert into users
                        (id, username, password_hash, nickname, real_name, phone, roles)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    1L, "admin", hasher.hash("123456"),
                    "系统管理员", "百川管理员", "", "admin");
        }
    }

    @Override
    public Optional<UserAccount> findById(Long id) {
        return jdbc.query("select * from users where id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return jdbc.query("select * from users where lower(username) = lower(?)", this::map, username)
                .stream().findFirst();
    }

    @Override
    public UserAccount save(UserAccount user) {
        jdbc.update(
                """
                update users set username = ?, nickname = ?, real_name = ?, phone = ?,
                    password_hash = ?, roles = ? where id = ?
                """,
                user.getUsername(), user.getNickname(), user.getRealName(), user.getPhone(),
                user.getPasswordHash(), String.join(",", user.getRoles()), user.getId());
        return user;
    }

    @Override
    public boolean existsByUsername(String username) {
        var count = jdbc.queryForObject(
                "select count(*) from users where lower(username) = lower(?)", Integer.class, username);
        return count != null && count > 0;
    }

    private UserAccount map(ResultSet result, int rowNumber) throws SQLException {
        return new UserAccount(
                result.getLong("id"), result.getString("username"), result.getString("password_hash"),
                result.getString("nickname"), result.getString("real_name"),
                result.getString("phone"), List.of(result.getString("roles").split(",")));
    }
}
