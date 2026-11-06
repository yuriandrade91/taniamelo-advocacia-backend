package com.lawfirm.law.firm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.model.Role;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.core.GrantedAuthority;

@DisplayName("UserPrincipal: adaptação da entidade User para o UserDetails do Spring Security")
class UserPrincipalTest {

    @Test
    @DisplayName("expõe id, e-mail como username e o hash como password")
    void exposesIdentityFields() {
        User user = TestFixtures.user();
        UserPrincipal principal = new UserPrincipal(user);

        assertEquals(user.getId(), principal.getId());
        assertEquals(user.getEmail(), principal.getUsername());
        assertEquals(user.getPasswordHash(), principal.getPassword());
    }

    @ParameterizedTest(name = "papel {0} vira a authority ROLE_{0}")
    @EnumSource(Role.class)
    void mapsRoleToPrefixedAuthority(Role role) {
        User user = TestFixtures.user();
        user.setRole(role);

        var authorities = new UserPrincipal(user).getAuthorities();
        assertEquals(1, authorities.size());
        assertEquals(
                "ROLE_" + role.name(),
                authorities.stream().map(GrantedAuthority::getAuthority).findFirst().orElseThrow());
    }

    @Test
    @DisplayName("active=true habilita a conta")
    void activeUserIsEnabled() {
        User user = TestFixtures.user();
        user.setActive(true);
        assertTrue(new UserPrincipal(user).isEnabled());
    }

    @Test
    @DisplayName("active=false e active=null desabilitam a conta")
    void inactiveOrNullActiveIsDisabled() {
        User inactive = TestFixtures.user();
        inactive.setActive(false);
        assertFalse(new UserPrincipal(inactive).isEnabled());

        User nullActive = TestFixtures.user();
        nullActive.setActive(null);
        assertFalse(new UserPrincipal(nullActive).isEnabled());
    }

    @Test
    @DisplayName("expiração e bloqueio de conta não são usados neste modelo")
    void accountFlagsAreAlwaysTrue() {
        UserPrincipal principal = new UserPrincipal(TestFixtures.user());
        assertTrue(principal.isAccountNonExpired());
        assertTrue(principal.isAccountNonLocked());
        assertTrue(principal.isCredentialsNonExpired());
    }
}
