package com.example.ragdemo.store;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record StoreUserPrincipal(Long id, String username, String password, String displayName,
        boolean enabled) implements UserDetails {

    static StoreUserPrincipal from(StoreUser user) {
        return new StoreUserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getDisplayName(), Boolean.TRUE.equals(user.getEnabled()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
