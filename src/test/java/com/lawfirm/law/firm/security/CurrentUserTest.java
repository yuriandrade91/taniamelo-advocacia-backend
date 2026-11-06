package com.lawfirm.law.firm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.lawfirm.law.firm.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("CurrentUser: id do usuário autenticado da requisição")
class CurrentUserTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("sem autenticação devolve null (chamadas de sistema e testes)")
    void withoutAuthenticationReturnsNull() {
        SecurityContextHolder.clearContext();
        assertNull(CurrentUser.id());
    }

    @Test
    @DisplayName("com UserPrincipal autenticado devolve o id do usuário")
    void withUserPrincipalReturnsItsId() {
        UserPrincipal principal = new UserPrincipal(TestFixtures.user());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));

        assertEquals(TestFixtures.USER_ID, CurrentUser.id());
    }

    @Test
    @DisplayName("principal que não é UserPrincipal devolve null")
    void withForeignPrincipalReturnsNull() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "anonymous", null, java.util.List.of()));

        assertNull(CurrentUser.id());
    }
}
