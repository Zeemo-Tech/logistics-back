package org.example.logisticsback.user;

import java.util.ArrayList;
import java.util.List;

public class UserAccount {
    private final Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String realName;
    private String phone;
    private final List<String> roles;

    public UserAccount(Long id, String username, String passwordHash,
                       String nickname, String realName, String phone, List<String> roles) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.realName = realName;
        this.phone = phone;
        this.roles = new ArrayList<>(roles);
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void changePassword(String hash) { this.passwordHash = hash; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public List<String> getRoles() { return List.copyOf(roles); }
}
