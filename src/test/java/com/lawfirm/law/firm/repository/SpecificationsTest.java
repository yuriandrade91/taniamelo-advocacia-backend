package com.lawfirm.law.firm.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.model.Appointment;
import com.lawfirm.law.firm.model.AppointmentStatus;
import com.lawfirm.law.firm.model.AppointmentType;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.Situation;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

/**
 * As specifications montam predicados da Criteria API. Aqui o CriteriaBuilder é mockado: o que
 * importa é QUAIS predicados são montados (e, principalmente, quando nenhum é - filtro ignorado).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Specifications de Cliente e Agenda")
class SpecificationsTest {

    @Mock private CriteriaBuilder cb;
    @Mock private CriteriaQuery<?> query;
    @Mock private Predicate predicate;

    @SuppressWarnings("rawtypes")
    @Mock
    private Path path;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void stubBuilder() {
        when(cb.lower(any())).thenReturn(path);
        when(cb.function(anyString(), any(), any(Expression[].class))).thenReturn(path);
        when(cb.like(any(Expression.class), anyString())).thenReturn(predicate);
        when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.between(any(Expression.class), any(Instant.class), any(Instant.class)))
                .thenReturn(predicate);
        when(cb.greaterThanOrEqualTo(any(Expression.class), any(Instant.class)))
                .thenReturn(predicate);
        when(cb.lessThanOrEqualTo(any(Expression.class), any(Instant.class))).thenReturn(predicate);
        when(cb.equal(any(), any(Object.class))).thenReturn(predicate);
        when(cb.isNull(any(Expression.class))).thenReturn(predicate);
        when(cb.literal(any())).thenReturn(path);
        when(path.get(anyString())).thenReturn(path);
        when(path.in(any(java.util.Collection.class))).thenReturn(predicate);
    }

    @SuppressWarnings("unchecked")
    private <T> Predicate apply(Specification<T> spec, Root<T> root) {
        return spec.toPredicate(root, (CriteriaQuery<?>) query, cb);
    }

    @Nested
    @DisplayName("ClientSpecification")
    class ClientSpecs {

        @SuppressWarnings("unchecked")
        @Mock
        private Root<Client> root;

        @BeforeEach
        void stubRoot() {
            when(root.get(anyString())).thenReturn(path);
        }

        @Test
        @DisplayName("busca livre nula/vazia é ignorada")
        void blankSearchTermIsIgnored() {
            assertNull(apply(ClientSpecification.searchTerm(null), root));
            assertNull(apply(ClientSpecification.searchTerm(""), root));
            assertNull(apply(ClientSpecification.searchTerm("   "), root));
        }

        @Test
        @DisplayName("busca por nome usa unaccent(lower(full_name)) com o termo sem acento")
        void nameSearchFoldsAccents() {
            assertSame(predicate, apply(ClientSpecification.searchTerm("José"), root));

            verify(cb).function(eq("unaccent"), eq(String.class), any(Expression[].class));
            ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
            verify(cb).like(any(Expression.class), pattern.capture());
            org.junit.jupiter.api.Assertions.assertEquals("%jose%", pattern.getValue());
        }

        @Test
        @DisplayName("busca com dígitos também procura no CPF, comparando só os números")
        void searchWithDigitsAlsoMatchesCpf() {
            assertSame(predicate, apply(ClientSpecification.searchTerm("529.982.247-25"), root));

            verify(cb).function(eq("regexp_replace"), eq(String.class), any(Expression[].class));
            verify(cb).or(any(Predicate.class), any(Predicate.class));
        }

        @Test
        @DisplayName("busca sem dígitos não monta o predicado de CPF")
        void searchWithoutDigitsSkipsCpf() {
            apply(ClientSpecification.searchTerm("Maria da Silva"), root);
            verify(cb, org.mockito.Mockito.never()).or(any(Predicate.class), any(Predicate.class));
        }

        @Test
        @DisplayName("listas de benefício/situação vazias, nulas ou só com null são ignoradas")
        void emptyEnumFiltersAreIgnored() {
            assertNull(apply(ClientSpecification.benefitIn(null), root));
            assertNull(apply(ClientSpecification.benefitIn(List.of()), root));
            assertNull(
                    apply(ClientSpecification.benefitIn(Arrays.asList((BenefitType) null)), root));
            assertNull(apply(ClientSpecification.situationIn(null), root));
            assertNull(apply(ClientSpecification.situationIn(List.of()), root));
            assertNull(
                    apply(ClientSpecification.situationIn(Arrays.asList((Situation) null)), root));
        }

        @Test
        @DisplayName("listas com valores montam um IN")
        void enumFiltersBuildInPredicate() {
            assertSame(
                    predicate,
                    apply(
                            ClientSpecification.benefitIn(List.of(BenefitType.APOSENTADORIA_RURAL)),
                            root));
            assertSame(
                    predicate,
                    apply(
                            ClientSpecification.situationIn(List.of(Situation.ANALISE_DOCUMENTAL)),
                            root));
        }

        @Test
        @DisplayName("nulos dentro da lista são descartados antes do IN")
        void nullsInsideListAreDropped() {
            assertSame(
                    predicate,
                    apply(
                            ClientSpecification.benefitIn(
                                    Arrays.asList(BenefitType.APOSENTADORIA_RURAL, null)),
                            root));
        }

        @Test
        @DisplayName("intervalo de criação cobre os quatro casos (nenhum, só de, só até, ambos)")
        void createdBetweenCoversEveryCombination() {
            Instant from = Instant.parse("2026-01-01T00:00:00Z");
            Instant to = Instant.parse("2026-12-31T00:00:00Z");

            assertNull(apply(ClientSpecification.createdBetween(null, null), root));
            assertSame(predicate, apply(ClientSpecification.createdBetween(from, to), root));
            assertSame(predicate, apply(ClientSpecification.createdBetween(from, null), root));
            assertSame(predicate, apply(ClientSpecification.createdBetween(null, to), root));

            verify(cb).between(any(Expression.class), eq(from), eq(to));
            verify(cb).greaterThanOrEqualTo(any(Expression.class), eq(from));
            verify(cb).lessThanOrEqualTo(any(Expression.class), eq(to));
        }

        @Test
        @DisplayName("combine ignora nulos e devolve null quando todos são nulos")
        void combineIgnoresNulls() {
            assertNull(ClientSpecification.combine(List.of()));
            assertNull(ClientSpecification.combine(Arrays.asList(null, null)));

            Specification<Client> only = ClientSpecification.searchTerm("maria");
            assertSame(only, ClientSpecification.combine(Arrays.asList(null, only, null)));

            assertNotNull(
                    ClientSpecification.combine(
                            List.of(
                                    ClientSpecification.searchTerm("maria"),
                                    ClientSpecification.benefitIn(
                                            List.of(BenefitType.APOSENTADORIA_RURAL)))));
        }
    }

    @Nested
    @DisplayName("AppointmentSpecification")
    class AppointmentSpecs {

        @SuppressWarnings("unchecked")
        @Mock
        private Root<Appointment> root;

        @BeforeEach
        void stubRoot() {
            when(root.get(anyString())).thenReturn(path);
        }

        @Test
        @DisplayName("notDeleted filtra deletedAt IS NULL")
        void notDeleted() {
            assertSame(predicate, apply(AppointmentSpecification.notDeleted(), root));
            verify(cb).isNull(any(Expression.class));
        }

        @Test
        @DisplayName("startBetween cobre os quatro casos de intervalo")
        void startBetweenCoversEveryCombination() {
            Instant from = Instant.parse("2026-01-01T00:00:00Z");
            Instant to = Instant.parse("2026-12-31T00:00:00Z");

            assertNull(apply(AppointmentSpecification.startBetween(null, null), root));
            assertSame(predicate, apply(AppointmentSpecification.startBetween(from, to), root));
            assertSame(predicate, apply(AppointmentSpecification.startBetween(from, null), root));
            assertSame(predicate, apply(AppointmentSpecification.startBetween(null, to), root));
        }

        @Test
        @DisplayName("filtros de tipo e situação vazios são ignorados")
        void emptyEnumFiltersAreIgnored() {
            assertNull(apply(AppointmentSpecification.typeIn(null), root));
            assertNull(apply(AppointmentSpecification.typeIn(List.of()), root));
            assertNull(apply(AppointmentSpecification.statusIn(null), root));
            assertNull(apply(AppointmentSpecification.statusIn(List.of()), root));
        }

        @Test
        @DisplayName("filtros de tipo e situação com valores montam um IN")
        void enumFiltersBuildIn() {
            assertSame(
                    predicate,
                    apply(AppointmentSpecification.typeIn(List.of(AppointmentType.PERICIA)), root));
            assertSame(
                    predicate,
                    apply(
                            AppointmentSpecification.statusIn(List.of(AppointmentStatus.AGENDADO)),
                            root));
        }

        @Test
        @DisplayName("anos e meses vazios são ignorados")
        void emptyYearsAndMonthsAreIgnored() {
            assertNull(apply(AppointmentSpecification.yearsIn(null), root));
            assertNull(apply(AppointmentSpecification.yearsIn(List.of()), root));
            assertNull(apply(AppointmentSpecification.monthsIn(null), root));
            assertNull(apply(AppointmentSpecification.monthsIn(List.of()), root));
        }

        @Test
        @DisplayName("anos usam date_part('year', start_at)")
        void yearsUseDatePart() {
            assertSame(
                    predicate, apply(AppointmentSpecification.yearsIn(List.of(2026, 2027)), root));
            verify(cb).function(eq("date_part"), eq(Double.class), any(Expression[].class));
        }

        @Test
        @DisplayName("meses usam date_part('month', start_at)")
        void monthsUseDatePart() {
            assertSame(predicate, apply(AppointmentSpecification.monthsIn(List.of(1, 12)), root));
            verify(cb).function(eq("date_part"), eq(Double.class), any(Expression[].class));
        }

        @Test
        @DisplayName("clientId nulo é ignorado; com valor monta a igualdade")
        void clientFilter() {
            assertNull(apply(AppointmentSpecification.clientIs(null), root));
            assertSame(
                    predicate, apply(AppointmentSpecification.clientIs(UUID.randomUUID()), root));
        }

        @Test
        @DisplayName("busca por título em branco é ignorada; com valor usa unaccent sem acento")
        void titleSearch() {
            assertNull(apply(AppointmentSpecification.titleContains(null), root));
            assertNull(apply(AppointmentSpecification.titleContains("   "), root));

            assertSame(predicate, apply(AppointmentSpecification.titleContains("Perícia"), root));

            ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
            verify(cb).like(any(Expression.class), pattern.capture());
            org.junit.jupiter.api.Assertions.assertEquals("%pericia%", pattern.getValue());
        }

        @Test
        @DisplayName("combine ignora nulos")
        void combineIgnoresNulls() {
            assertNull(AppointmentSpecification.combine(List.of()));
            assertNull(AppointmentSpecification.combine(Arrays.asList(null, null)));

            Specification<Appointment> only = AppointmentSpecification.notDeleted();
            assertSame(only, AppointmentSpecification.combine(Arrays.asList(null, only)));
            assertNotNull(
                    AppointmentSpecification.combine(
                            List.of(
                                    AppointmentSpecification.notDeleted(),
                                    AppointmentSpecification.clientIs(UUID.randomUUID()))));
        }
    }
}
