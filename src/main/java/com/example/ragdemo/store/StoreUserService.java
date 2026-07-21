package com.example.ragdemo.store;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class StoreUserService implements UserDetailsService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,30}$");

    private final StoreUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentStoreUser currentUser;

    public StoreUserService(StoreUserRepository repository, PasswordEncoder passwordEncoder,
            CurrentStoreUser currentUser) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return repository.findByUsernameIgnoreCase(normalizeUsername(username))
                .map(StoreUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
    }

    @Transactional
    public StoreUser register(StoreApiModels.RegisterRequest request) {
        if (request == null) {
            throw badRequest("请填写注册信息");
        }
        String username = normalizeUsername(request.username());
        String displayName = trim(request.displayName());
        validateUsername(username);
        validatePassword(request.password());
        if (displayName.isEmpty() || displayName.length() > 40) {
            throw badRequest("昵称长度必须为1到40个字符");
        }
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        try {
            return repository.saveAndFlush(
                    new StoreUser(username, passwordEncoder.encode(request.password()), displayName));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在", exception);
        }
    }

    public StoreApiModels.UserResponse current() {
        return StoreApiModels.UserResponse.from(ownedUser());
    }

    @Transactional
    public StoreApiModels.UserResponse update(StoreApiModels.UpdateProfileRequest request) {
        if (request == null) {
            throw badRequest("请填写个人资料");
        }
        String displayName = trim(request.displayName());
        String phone = trim(request.phone());
        if (displayName.isEmpty() || displayName.length() > 40) {
            throw badRequest("昵称长度必须为1到40个字符");
        }
        if (phone.length() > 30) {
            throw badRequest("手机号长度不能超过30个字符");
        }
        StoreUser user = ownedUser();
        user.updateProfile(displayName, phone.isEmpty() ? null : phone);
        return StoreApiModels.UserResponse.from(user);
    }

    private StoreUser ownedUser() {
        return repository.findById(currentUser.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在"));
    }

    private void validateUsername(String username) {
        if (!USERNAME.matcher(username).matches()) {
            throw badRequest("用户名只能包含3到30位字母、数字或下划线");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw badRequest("密码长度必须为8到72个字符");
        }
    }

    private String normalizeUsername(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
