package org.example.logisticsback.pledge;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.example.logisticsback.auth.AuthInterceptor;
import org.example.logisticsback.auth.PasswordHasher;
import org.example.logisticsback.common.*;
import org.example.logisticsback.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pledge-accounts")
public class PledgeAccountController {
    private final PledgeRepository db;
    private final UserRepository users;
    private final PasswordHasher passwords;
    public PledgeAccountController(PledgeRepository db,UserRepository users,PasswordHasher passwords) {this.db=db;this.users=users;this.passwords=passwords;}
    public record Account(@NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,32}") String username,
        @NotBlank @Size(min=8,max=64) String password,@NotBlank @Size(max=32) String realName,
        @NotBlank String role) {}
    @GetMapping public ApiResponse<List<Map<String,Object>>> list(HttpServletRequest r) {
        admin(r);return ApiResponse.ok(db.query("select id,username,real_name,roles from users order by id"));
    }
    @PostMapping @Transactional public ApiResponse<Void> create(@Valid @RequestBody Account input,HttpServletRequest r) {
        admin(r);db.lock();
        if(!Set.of("pledge_operator","pledge_reviewer").contains(input.role()))throw new BusinessException(400,"请选择经办或复核角色");
        if(users.existsByUsername(input.username()))throw new BusinessException(409,"登录账号已存在");
        long id=db.query("select coalesce(max(id),0)+1 as next_id from users").stream().mapToLong(row->Long.parseLong(row.get("nextId").toString())).findFirst().orElseThrow();
        db.insert("users",PledgeService.row("id",id,"username",input.username(),"password_hash",passwords.hash(input.password()),"nickname",input.realName(),"real_name",input.realName(),"phone","","roles",input.role()));
        return ApiResponse.ok(null,"监管账号已建立");
    }
    private void admin(HttpServletRequest r) {
        Long id=(Long)r.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
        if(users.findById(id).filter(user->user.getRoles().contains("admin")).isEmpty())throw new BusinessException(403,"仅管理员可配置监管账号");
    }
}
