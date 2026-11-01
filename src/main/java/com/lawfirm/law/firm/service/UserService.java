package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.UserSummaryDTO;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Usuários do escritório, para traduzir em nome os ids que os demais recursos devolvem. */
@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    /** Sem paginação: um escritório tem uma dezena de usuários (ver {@code UserController}). */
    @Transactional(readOnly = true)
    public List<UserSummaryDTO> list(boolean includeInactive) {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "fullName")).stream()
                .filter(user -> includeInactive || Boolean.TRUE.equals(user.getActive()))
                .map(UserService::toDTO)
                .toList();
    }

    private static UserSummaryDTO toDTO(User user) {
        return new UserSummaryDTO(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getRole(),
                Boolean.TRUE.equals(user.getActive()));
    }
}
