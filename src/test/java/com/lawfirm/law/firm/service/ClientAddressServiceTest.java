package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.ClientAddressRequestDTO;
import com.lawfirm.law.firm.dto.ClientAddressResponseDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.AddressType;
import com.lawfirm.law.firm.model.ClientAddress;
import com.lawfirm.law.firm.repository.ClientAddressRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.security.UserPrincipal;
import com.lawfirm.law.firm.support.TestFixtures;
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
@DisplayName("ClientAddressService: exatamente um endereço principal por cliente")
class ClientAddressServiceTest {

    private static final UUID ADDRESS_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Mock private ClientAddressRepository repository;
    @Mock private ClientRepository clientRepository;

    private ClientAddressService service;

    @BeforeEach
    void setUp() {
        service = new ClientAddressService(repository, clientRepository);
        when(clientRepository.findById(TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(TestFixtures.client()));
        when(repository.save(any(ClientAddress.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.saveAndFlush(any(ClientAddress.class))).thenAnswer(i -> i.getArgument(0));
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

    private ClientAddressRequestDTO requestDto() {
        ClientAddressRequestDTO dto = new ClientAddressRequestDTO();
        dto.setAddressType("Comercial");
        dto.setStreet("Rua das Flores");
        dto.setAddressNumber("100");
        dto.setComplement("Sala 2");
        dto.setNeighborhood("Centro");
        dto.setCity("Belo Horizonte");
        dto.setState("MG");
        dto.setZipCode("30000-000");
        return dto;
    }

    private ClientAddress existingAddress(boolean primary) {
        ClientAddress address = new ClientAddress();
        address.setId(ADDRESS_ID);
        address.setClient(TestFixtures.client());
        address.setAddressType(AddressType.RESIDENCIAL);
        address.setIsPrimary(primary);
        return address;
    }

    private ClientAddress captureSaved() {
        ArgumentCaptor<ClientAddress> saved = ArgumentCaptor.forClass(ClientAddress.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("o primeiro endereço do cliente vira principal automaticamente")
    void firstAddressBecomesPrimary() {
        when(repository.countByClient_Id(TestFixtures.CLIENT_ID)).thenReturn(0L);
        when(repository.findByClient_IdAndIsPrimaryTrue(TestFixtures.CLIENT_ID))
                .thenReturn(Optional.empty());
        authenticate();

        ClientAddressResponseDTO dto = service.create(TestFixtures.CLIENT_ID, requestDto());

        assertTrue(dto.getIsPrimary());
        ClientAddress saved = captureSaved();
        assertTrue(saved.getIsPrimary());
        assertEquals(AddressType.COMERCIAL, saved.getAddressType());
        assertEquals("Rua das Flores", saved.getStreet());
        assertEquals("30000-000", saved.getZipCode());
        assertEquals(TestFixtures.USER_ID, saved.getCreatedBy());
    }

    @Test
    @DisplayName("um segundo endereço sem isPrimary não é principal")
    void secondAddressIsNotPrimaryByDefault() {
        when(repository.countByClient_Id(TestFixtures.CLIENT_ID)).thenReturn(1L);

        service.create(TestFixtures.CLIENT_ID, requestDto());

        assertFalse(captureSaved().getIsPrimary());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("isPrimary=true desmarca o principal anterior com flush imediato")
    void markingPrimaryUnsetsThePrevious() {
        ClientAddress previous = existingAddress(true);
        when(repository.countByClient_Id(TestFixtures.CLIENT_ID)).thenReturn(1L);
        when(repository.findByClient_IdAndIsPrimaryTrue(TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(previous));

        ClientAddressRequestDTO dto = requestDto();
        dto.setIsPrimary(true);
        service.create(TestFixtures.CLIENT_ID, dto);

        assertFalse(previous.getIsPrimary());
        verify(repository).saveAndFlush(previous);
        assertTrue(captureSaved().getIsPrimary());
    }

    @Test
    @DisplayName("createAll grava a lista inteira, na ordem enviada")
    void createAllPersistsEveryAddressInOrder() {
        // Primeiro endereço do cliente: o count sobe conforme os saves acontecem.
        when(repository.countByClient_Id(TestFixtures.CLIENT_ID)).thenReturn(0L, 1L, 2L);

        ClientAddressRequestDTO first = requestDto();
        first.setStreet("Rua A");
        ClientAddressRequestDTO second = requestDto();
        second.setStreet("Rua B");

        List<ClientAddressResponseDTO> created =
                service.createAll(TestFixtures.CLIENT_ID, List.of(first, second));

        assertEquals(2, created.size());
        assertEquals("Rua A", created.get(0).getStreet());
        assertEquals("Rua B", created.get(1).getStreet());
        verify(repository, times(2)).save(any(ClientAddress.class));
    }

    @Test
    @DisplayName("createAll: só o primeiro da lista vira principal automaticamente")
    void createAllMarksOnlyTheFirstAsPrimary() {
        when(repository.countByClient_Id(TestFixtures.CLIENT_ID)).thenReturn(0L, 1L);

        service.createAll(TestFixtures.CLIENT_ID, List.of(requestDto(), requestDto()));

        ArgumentCaptor<ClientAddress> saved = ArgumentCaptor.forClass(ClientAddress.class);
        verify(repository, times(2)).save(saved.capture());
        assertTrue(saved.getAllValues().get(0).getIsPrimary());
        assertFalse(saved.getAllValues().get(1).getIsPrimary());
    }

    @Test
    @DisplayName("createAll de cliente inexistente estoura 404 antes de gravar qualquer endereço")
    void createAllOfMissingClientSavesNothing() {
        UUID unknown = UUID.randomUUID();
        when(clientRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.createAll(unknown, List.of(requestDto(), requestDto())));

        verify(repository, never()).save(any(ClientAddress.class));
    }

    @Test
    @DisplayName("tipo de endereço ausente cai em Residencial")
    void missingAddressTypeDefaultsToResidential() {
        when(repository.countByClient_Id(TestFixtures.CLIENT_ID)).thenReturn(1L);
        ClientAddressRequestDTO dto = requestDto();
        dto.setAddressType(null);

        service.create(TestFixtures.CLIENT_ID, dto);
        assertEquals(AddressType.RESIDENCIAL, captureSaved().getAddressType());
    }

    @Test
    @DisplayName("tipo de endereço em branco também cai em Residencial")
    void blankAddressTypeDefaultsToResidential() {
        when(repository.countByClient_Id(TestFixtures.CLIENT_ID)).thenReturn(1L);
        ClientAddressRequestDTO dto = requestDto();
        dto.setAddressType("   ");

        service.create(TestFixtures.CLIENT_ID, dto);
        assertEquals(AddressType.RESIDENCIAL, captureSaved().getAddressType());
    }

    @Test
    @DisplayName("tipo de endereço inválido vira 400")
    void invalidAddressTypeThrows() {
        ClientAddressRequestDTO dto = requestDto();
        dto.setAddressType("Submarino");

        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () -> service.create(TestFixtures.CLIENT_ID, dto));
        assertEquals("addressType", ex.getField());
        assertEquals(ValidationErrorCode.INVALID_ENUM_VALUE, ex.getValidationErrorCode());
        assertTrue(ex.getMessage().contains("Submarino"));
    }

    @Test
    @DisplayName("cliente inexistente estoura 404 em todas as operações")
    void missingClientThrowsEverywhere() {
        UUID unknown = UUID.randomUUID();
        when(clientRepository.findById(unknown)).thenReturn(Optional.empty());
        ClientAddressRequestDTO dto = requestDto();

        assertThrows(NotFoundException.class, () -> service.create(unknown, dto));
        assertThrows(NotFoundException.class, () -> service.list(unknown, 1, 10));
        assertThrows(NotFoundException.class, () -> service.get(unknown, ADDRESS_ID));
        assertThrows(NotFoundException.class, () -> service.update(unknown, ADDRESS_ID, dto));
        assertThrows(NotFoundException.class, () -> service.delete(unknown, ADDRESS_ID));
    }

    @Test
    @DisplayName("a listagem traz o principal primeiro e depois por ordem de cadastro")
    void listSortsPrimaryFirst() {
        when(repository.findByClient_Id(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existingAddress(true))));

        assertEquals(1, service.list(TestFixtures.CLIENT_ID, 1, 10).getContent().size());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByClient_Id(any(), pageable.capture());
        assertEquals(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("isPrimary"),
                        org.springframework.data.domain.Sort.Order.asc("createdAt")),
                pageable.getValue().getSort());
    }

    @Test
    @DisplayName("paginação inválida na listagem é normalizada")
    void listNormalizesPaging() {
        when(repository.findByClient_Id(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(TestFixtures.CLIENT_ID, 0, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByClient_Id(any(), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
    }

    @Test
    @DisplayName("get devolve o endereço mapeado com todos os campos")
    void getMapsEveryField() {
        ClientAddress address = existingAddress(true);
        address.setStreet("Rua A");
        address.setCity("BH");
        address.setState("MG");
        when(repository.findByIdAndClient_Id(ADDRESS_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(address));

        ClientAddressResponseDTO dto = service.get(TestFixtures.CLIENT_ID, ADDRESS_ID);

        assertEquals(ADDRESS_ID, dto.getId());
        assertEquals("Residencial", dto.getAddressType());
        assertEquals("Rua A", dto.getStreet());
        assertEquals("MG", dto.getState());
        assertTrue(dto.getIsPrimary());
    }

    @Test
    @DisplayName("endereço sem tipo devolve tipo nulo no DTO")
    void nullAddressTypeMapsToNullLabel() {
        ClientAddress address = existingAddress(false);
        address.setAddressType(null);
        when(repository.findByIdAndClient_Id(ADDRESS_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(address));

        assertEquals(null, service.get(TestFixtures.CLIENT_ID, ADDRESS_ID).getAddressType());
    }

    @Test
    @DisplayName("endereço inexistente estoura 404")
    void missingAddressThrows() {
        when(repository.findByIdAndClient_Id(any(), any())).thenReturn(Optional.empty());
        UUID unknown = UUID.randomUUID();

        NotFoundException ex =
                assertThrows(
                        NotFoundException.class,
                        () -> service.get(TestFixtures.CLIENT_ID, unknown));
        assertTrue(ex.getMessage().startsWith("Endereço"));
    }

    @Test
    @DisplayName("o único endereço do cliente continua principal mesmo com isPrimary=false")
    void theOnlyAddressStaysPrimary() {
        ClientAddress address = existingAddress(false);
        when(repository.findByIdAndClient_Id(ADDRESS_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(address));
        when(repository.countByClient_Id(TestFixtures.CLIENT_ID)).thenReturn(1L);
        when(repository.findByClient_IdAndIsPrimaryTrue(TestFixtures.CLIENT_ID))
                .thenReturn(Optional.empty());

        ClientAddressRequestDTO dto = requestDto();
        dto.setIsPrimary(false);

        assertTrue(service.update(TestFixtures.CLIENT_ID, ADDRESS_ID, dto).getIsPrimary());
    }

    @Test
    @DisplayName("update com isPrimary=false num cliente com vários endereços desmarca este")
    void updateCanUnsetPrimaryWhenThereAreOthers() {
        ClientAddress address = existingAddress(true);
        when(repository.findByIdAndClient_Id(ADDRESS_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(address));
        when(repository.countByClient_Id(TestFixtures.CLIENT_ID)).thenReturn(3L);

        ClientAddressRequestDTO dto = requestDto();
        dto.setIsPrimary(false);

        assertFalse(service.update(TestFixtures.CLIENT_ID, ADDRESS_ID, dto).getIsPrimary());
    }

    @Test
    @DisplayName("promover um endereço a principal desmarca o anterior")
    void updatePromotingToPrimaryUnsetsThePrevious() {
        ClientAddress target = existingAddress(false);
        ClientAddress previous = new ClientAddress();
        previous.setId(UUID.randomUUID());
        previous.setIsPrimary(true);

        when(repository.findByIdAndClient_Id(ADDRESS_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(target));
        when(repository.countByClient_Id(TestFixtures.CLIENT_ID)).thenReturn(2L);
        when(repository.findByClient_IdAndIsPrimaryTrue(TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(previous));
        authenticate();

        ClientAddressRequestDTO dto = requestDto();
        dto.setIsPrimary(true);
        service.update(TestFixtures.CLIENT_ID, ADDRESS_ID, dto);

        assertFalse(previous.getIsPrimary());
        assertTrue(target.getIsPrimary());
        assertEquals(TestFixtures.USER_ID, target.getUpdatedBy());
    }

    @Test
    @DisplayName("já sendo o principal, um update que pede principal não faz nada extra")
    void alreadyPrimaryIsNotReprocessed() {
        ClientAddress target = existingAddress(true);
        when(repository.findByIdAndClient_Id(ADDRESS_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(target));
        when(repository.countByClient_Id(TestFixtures.CLIENT_ID)).thenReturn(2L);

        ClientAddressRequestDTO dto = requestDto();
        dto.setIsPrimary(true);
        service.update(TestFixtures.CLIENT_ID, ADDRESS_ID, dto);

        verify(repository, never()).saveAndFlush(any());
        assertTrue(target.getIsPrimary());
    }

    @Test
    @DisplayName("excluir o principal promove o endereço mais antigo restante")
    void deletingThePrimaryPromotesTheOldestRemaining() {
        ClientAddress primary = existingAddress(true);
        ClientAddress remaining = new ClientAddress();
        remaining.setId(UUID.randomUUID());
        remaining.setIsPrimary(false);

        when(repository.findByIdAndClient_Id(ADDRESS_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(primary));
        when(repository.findByClient_IdOrderByIsPrimaryDescCreatedAtAsc(TestFixtures.CLIENT_ID))
                .thenReturn(List.of(remaining));
        authenticate();

        service.delete(TestFixtures.CLIENT_ID, ADDRESS_ID);

        verify(repository).delete(primary);
        assertTrue(remaining.getIsPrimary());
        assertEquals(TestFixtures.USER_ID, remaining.getUpdatedBy());
        verify(repository).save(remaining);
    }

    @Test
    @DisplayName("excluir um endereço não-principal não promove ninguém")
    void deletingANonPrimaryDoesNotPromote() {
        ClientAddress address = existingAddress(false);
        when(repository.findByIdAndClient_Id(ADDRESS_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(address));

        service.delete(TestFixtures.CLIENT_ID, ADDRESS_ID);

        verify(repository).delete(address);
        verify(repository, never()).findByClient_IdOrderByIsPrimaryDescCreatedAtAsc(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("excluir o último endereço não quebra a promoção")
    void deletingTheLastAddressIsSafe() {
        ClientAddress address = existingAddress(true);
        when(repository.findByIdAndClient_Id(ADDRESS_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(address));
        when(repository.findByClient_IdOrderByIsPrimaryDescCreatedAtAsc(TestFixtures.CLIENT_ID))
                .thenReturn(List.of());

        service.delete(TestFixtures.CLIENT_ID, ADDRESS_ID);

        verify(repository).delete(address);
        verify(repository, never()).save(any());
        assertNotNull(address);
    }
}
