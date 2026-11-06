package com.lawfirm.law.firm.security;

import com.lawfirm.law.firm.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * O parâmetro é o identificador de login, que pode ser o e-mail OU o username (a autenticação
     * aceita os dois). A busca é feita no schema do tenant corrente (resolvido antes desta
     * chamada).
     */
    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        return userRepository
                .findByEmailIgnoreCaseOrUsernameIgnoreCase(login, login)
                .map(UserPrincipal::new)
                .orElseThrow(
                        () -> new UsernameNotFoundException("Usuário não encontrado: " + login));
    }
}
