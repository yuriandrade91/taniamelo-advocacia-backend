package com.lawfirm.law.firm.security;

import com.lawfirm.law.firm.model.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Adapts our {@link User} entity to Spring Security's {@link UserDetails} contract. */
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;
    private final UUID actingSupportUserId;

    private UserPrincipal(
            UUID id,
            String email,
            String passwordHash,
            boolean active,
            Collection<? extends GrantedAuthority> authorities,
            UUID actingSupportUserId) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.active = active;
        this.authorities = authorities;
        this.actingSupportUserId = actingSupportUserId;
    }

    public UserPrincipal(User user) {
        this(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getActive()),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                null);
    }

    /**
     * Principal SINTÉTICO de uma sessão de suporte (impersonation). Não corresponde a nenhuma linha
     * de {@code users} do tenant, então {@code id} é NULL de propósito: as colunas {@code
     * created_by}/{@code updated_by}/{@code performed_by} têm FK para {@code users(id)} e um id de
     * suporte as violaria. A atribuição real vai por {@link #getActingSupportUserId()}, que a
     * auditoria grava em {@code audit_log.acting_support_user_id}.
     */
    public static UserPrincipal impersonation(UUID actingSupportUserId, String email, String role) {
        return new UserPrincipal(
                null,
                email,
                null,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)),
                actingSupportUserId);
    }

    public UUID getId() {
        return id;
    }

    /** Id do agente de suporte quando este principal é uma impersonation; senão {@code null}. */
    public UUID getActingSupportUserId() {
        return actingSupportUserId;
    }

    public boolean isImpersonation() {
        return actingSupportUserId != null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
