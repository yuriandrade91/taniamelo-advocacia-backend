package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.ClientInterviewRequestDTO;
import com.lawfirm.law.firm.dto.ClientInterviewResponseDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.ClientInterview;
import com.lawfirm.law.firm.repository.ClientInterviewRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.security.UserPrincipal;
import com.lawfirm.law.firm.support.TestFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClientInterviewService: entrevistas do cliente com soft delete")
class ClientInterviewServiceTest {

    private static final UUID INTERVIEW_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    @Mock private ClientInterviewRepository repository;
    @Mock private ClientRepository clientRepository;

    private ClientInterviewService service;

    @BeforeEach
    void setUp() {
        service = new ClientInterviewService(repository, clientRepository);
        when(clientRepository.findById(TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(TestFixtures.client()));
        when(repository.save(any(ClientInterview.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(TestFixtures.user());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }

    private ClientInterview existing() {
        ClientInterview interview = new ClientInterview();
        interview.setId(INTERVIEW_ID);
        interview.setClient(TestFixtures.client());
        interview.setContent("<p>anotações</p>");
        interview.setDurationMinutes(60);
        interview.setOccurredAt(Instant.parse("2026-03-01T10:00:00Z"));
        return interview;
    }

    private ClientInterview captureSaved() {
        ArgumentCaptor<ClientInterview> saved = ArgumentCaptor.forClass(ClientInterview.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("create grava conteúdo, duração e autor")
    void createStoresEveryField() {
        ClientInterviewRequestDTO dto = new ClientInterviewRequestDTO();
        dto.setContent("<p>primeira conversa</p>");
        dto.setDurationMinutes(45);
        dto.setOccurredAt(Instant.parse("2026-04-01T14:00:00Z"));
        authenticate();

        ClientInterviewResponseDTO response = service.create(TestFixtures.CLIENT_ID, dto);

        assertEquals("<p>primeira conversa</p>", response.getContent());
        assertEquals(45, response.getDurationMinutes());
        assertEquals(Instant.parse("2026-04-01T14:00:00Z"), response.getOccurredAt());
        assertEquals(TestFixtures.USER_ID, captureSaved().getCreatedBy());
    }

    @Test
    @DisplayName("sem data informada, usa o instante atual")
    void createDefaultsOccurredAtToNow() {
        Instant before = Instant.now();
        ClientInterviewRequestDTO dto = new ClientInterviewRequestDTO();
        dto.setContent("x");

        service.create(TestFixtures.CLIENT_ID, dto);

        Instant occurredAt = captureSaved().getOccurredAt();
        assertNotNull(occurredAt);
        assertTrue(!occurredAt.isBefore(before));
    }

    @Test
    @DisplayName("cliente inexistente estoura 404 em todas as operações")
    void missingClientThrowsEverywhere() {
        UUID unknown = UUID.randomUUID();
        when(clientRepository.findById(unknown)).thenReturn(Optional.empty());
        ClientInterviewRequestDTO dto = new ClientInterviewRequestDTO();

        assertThrows(NotFoundException.class, () -> service.create(unknown, dto));
        assertThrows(NotFoundException.class, () -> service.list(unknown, 1, 10));
        assertThrows(NotFoundException.class, () -> service.get(unknown, INTERVIEW_ID));
        assertThrows(NotFoundException.class, () -> service.update(unknown, INTERVIEW_ID, dto));
        assertThrows(NotFoundException.class, () -> service.delete(unknown, INTERVIEW_ID));
    }

    @Test
    @DisplayName("a listagem traz só as ativas, mais recente primeiro")
    void listReturnsActiveOnesNewestFirst() {
        when(repository.findByClient_IdAndDeletedAtIsNull(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existing())));

        assertEquals(1, service.list(TestFixtures.CLIENT_ID, 1, 10).getContent().size());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByClient_IdAndDeletedAtIsNull(any(), pageable.capture());
        assertEquals(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "occurredAt"),
                pageable.getValue().getSort());
        assertEquals(0, pageable.getValue().getPageNumber());
    }

    @Test
    @DisplayName("paginação inválida é normalizada")
    void listNormalizesPaging() {
        when(repository.findByClient_IdAndDeletedAtIsNull(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(TestFixtures.CLIENT_ID, -3, -1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByClient_IdAndDeletedAtIsNull(any(), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
    }

    @Test
    @DisplayName("get devolve a entrevista mapeada")
    void getMapsFields() {
        when(repository.findByIdAndClient_IdAndDeletedAtIsNull(
                        INTERVIEW_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(existing()));

        ClientInterviewResponseDTO dto = service.get(TestFixtures.CLIENT_ID, INTERVIEW_ID);

        assertEquals(INTERVIEW_ID, dto.getId());
        assertEquals("<p>anotações</p>", dto.getContent());
        assertEquals(60, dto.getDurationMinutes());
    }

    @Test
    @DisplayName("entrevista inexistente ou já excluída estoura 404")
    void missingInterviewThrows() {
        when(repository.findByIdAndClient_IdAndDeletedAtIsNull(any(), any()))
                .thenReturn(Optional.empty());
        UUID unknown = UUID.randomUUID();

        NotFoundException ex =
                assertThrows(
                        NotFoundException.class,
                        () -> service.get(TestFixtures.CLIENT_ID, unknown));
        assertTrue(ex.getMessage().startsWith("Entrevista"));
    }

    @Test
    @DisplayName("update substitui o conteúdo e marca o autor")
    void updateReplacesContent() {
        ClientInterview interview = existing();
        when(repository.findByIdAndClient_IdAndDeletedAtIsNull(
                        INTERVIEW_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(interview));
        authenticate();

        ClientInterviewRequestDTO dto = new ClientInterviewRequestDTO();
        dto.setContent("<p>novo conteúdo</p>");
        dto.setOccurredAt(Instant.parse("2026-05-01T09:00:00Z"));
        dto.setDurationMinutes(30);

        ClientInterviewResponseDTO response =
                service.update(TestFixtures.CLIENT_ID, INTERVIEW_ID, dto);

        assertEquals("<p>novo conteúdo</p>", response.getContent());
        assertEquals(Instant.parse("2026-05-01T09:00:00Z"), interview.getOccurredAt());
        assertEquals(30, interview.getDurationMinutes());
        assertEquals(TestFixtures.USER_ID, interview.getUpdatedBy());
    }

    @Test
    @DisplayName("data e duração nulas no update preservam os valores atuais")
    void updateKeepsCurrentDateAndDurationWhenOmitted() {
        ClientInterview interview = existing();
        when(repository.findByIdAndClient_IdAndDeletedAtIsNull(
                        INTERVIEW_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(interview));

        ClientInterviewRequestDTO dto = new ClientInterviewRequestDTO();
        dto.setContent("só o texto mudou");

        service.update(TestFixtures.CLIENT_ID, INTERVIEW_ID, dto);

        assertEquals(Instant.parse("2026-03-01T10:00:00Z"), interview.getOccurredAt());
        assertEquals(60, interview.getDurationMinutes());
    }

    @Test
    @DisplayName("delete é soft: marca deletedAt em vez de remover a linha")
    void deleteIsSoft() {
        ClientInterview interview = existing();
        when(repository.findByIdAndClient_IdAndDeletedAtIsNull(
                        INTERVIEW_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(interview));
        authenticate();

        service.delete(TestFixtures.CLIENT_ID, INTERVIEW_ID);

        assertNotNull(interview.getDeletedAt());
        assertEquals(TestFixtures.USER_ID, interview.getUpdatedBy());
        verify(repository).save(interview);
        verify(repository, org.mockito.Mockito.never()).delete(any());
    }
}
