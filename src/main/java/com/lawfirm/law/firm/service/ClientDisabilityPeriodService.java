package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientDisabilityPeriodRequestDTO;
import com.lawfirm.law.firm.dto.ClientDisabilityPeriodResponseDTO;
import com.lawfirm.law.firm.dto.DisabilityConversionDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientDisabilityPeriod;
import com.lawfirm.law.firm.model.DisabilityGrade;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.repository.ClientDisabilityPeriodRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.security.CurrentUser;
import com.lawfirm.law.firm.util.OfficeClock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD dos intervalos de deficiência e a conversão de tempo da LC 142/2013.
 *
 * <p>As regras que este serviço defende existem porque o cálculo depende delas, não por capricho de
 * formulário: intervalos sobrepostos fariam o mesmo dia contar duas vezes, e dois intervalos em
 * aberto significariam dois graus vigentes hoje — em ambos os casos o total sairia errado com cara
 * de certo, que é o pior defeito possível num número que vai para dentro de um requerimento.
 */
@Service
public class ClientDisabilityPeriodService {

    private final ClientDisabilityPeriodRepository repository;
    private final ClientRepository clientRepository;

    public ClientDisabilityPeriodService(
            ClientDisabilityPeriodRepository repository, ClientRepository clientRepository) {
        this.repository = repository;
        this.clientRepository = clientRepository;
    }

    @Transactional
    public ClientDisabilityPeriodResponseDTO create(
            UUID clientId, ClientDisabilityPeriodRequestDTO dto) {
        Client client = findClientOrThrow(clientId);

        ClientDisabilityPeriod entity = new ClientDisabilityPeriod();
        entity.setClient(client);
        applyFields(entity, dto);
        entity.setCreatedBy(CurrentUser.id());

        rejectOverlap(clientId, entity, null);
        return toDTO(repository.save(entity), OfficeClock.today());
    }

    public List<ClientDisabilityPeriodResponseDTO> list(UUID clientId) {
        findClientOrThrow(clientId);
        LocalDate today = OfficeClock.today();
        return repository.findByClient_IdOrderByStartedOnAsc(clientId).stream()
                .map(period -> toDTO(period, today))
                .toList();
    }

    public ClientDisabilityPeriodResponseDTO get(UUID clientId, UUID periodId) {
        return toDTO(findPeriodOrThrow(clientId, periodId), OfficeClock.today());
    }

    @Transactional
    public ClientDisabilityPeriodResponseDTO update(
            UUID clientId, UUID periodId, ClientDisabilityPeriodRequestDTO dto) {
        ClientDisabilityPeriod entity = findPeriodOrThrow(clientId, periodId);
        applyFields(entity, dto);
        entity.setUpdatedBy(CurrentUser.id());

        rejectOverlap(clientId, entity, periodId);
        return toDTO(repository.save(entity), OfficeClock.today());
    }

    @Transactional
    public void delete(UUID clientId, UUID periodId) {
        repository.delete(findPeriodOrThrow(clientId, periodId));
    }

    /**
     * O tempo dos intervalos convertido para uma base, com a conta aberta.
     *
     * @param to base de destino; nulo significa a regra geral ("Sem deficiência"), que é o que o
     *     escritório quer saber na maioria dos casos — quanto tempo comum este cliente tem.
     */
    public DisabilityConversionDTO conversion(UUID clientId, DisabilityGrade to) {
        Client client = findClientOrThrow(clientId);
        Gender gender = client.getGender();

        if (!DisabilityGrade.hasBasisFor(gender)) {
            // Melhor recusar do que devolver um número com aparência de certo:
            // a LC 142/2013 tem duas colunas, e escolher uma por conta própria
            // seria uma decisão jurídica tomada em silêncio por um servidor.
            throw new ValidationException(
                    "gender",
                    ValidationErrorCode.CONFLICTING_PARAMETERS,
                    "a conversão da LC 142/2013 tem tabela apenas para masculino e feminino; "
                            + "o sexo deste cliente está como \""
                            + (gender == null ? "não informado" : gender.getLabel())
                            + "\"");
        }

        DisabilityGrade target = to != null ? to : DisabilityGrade.SEM_DEFICIENCIA;
        LocalDate today = OfficeClock.today();
        List<ClientDisabilityPeriod> periods =
                repository.findByClient_IdOrderByStartedOnAsc(clientId);

        List<DisabilityConversionDTO.Line> lines = new ArrayList<>();
        for (ClientDisabilityPeriod period : periods) {
            long days = DisabilityTimeConversion.daysOf(period, today);
            DisabilityConversionDTO.Line line = new DisabilityConversionDTO.Line();
            line.setGrade(period.getGrade());
            line.setGradeYears(period.getGrade().requiredYears(gender));
            line.setDays(days);
            line.setFactor(DisabilityTimeConversion.factor(period.getGrade(), target, gender));
            line.setConvertedDays(
                    DisabilityTimeConversion.convertDays(days, period.getGrade(), target, gender));
            line.setStartedOn(period.getStartedOn());
            line.setEndedOn(period.effectiveEnd(today));
            line.setOngoing(period.isOngoing());
            lines.add(line);
        }

        var total = DisabilityTimeConversion.totalConverted(periods, target, gender, today);

        DisabilityConversionDTO dto = new DisabilityConversionDTO();
        dto.setConvertedTo(target);
        dto.setTargetYears(target.requiredYears(gender));
        dto.setLines(lines);
        dto.setTotalDays(total.totalDays());
        dto.setTotalYears(total.years());
        dto.setTotalMonths(total.months());
        dto.setTotalRemainingDays(total.days());
        dto.setTotalLabel(label(total.years(), total.months(), total.days()));
        return dto;
    }

    /** "33 anos, 11 meses e 5 dias" — omite o que for zero, e "0 dias" se tudo for. */
    static String label(int years, int months, int days) {
        List<String> parts = new ArrayList<>();
        if (years > 0) parts.add(years + (years == 1 ? " ano" : " anos"));
        if (months > 0) parts.add(months + (months == 1 ? " mês" : " meses"));
        if (days > 0) parts.add(days + (days == 1 ? " dia" : " dias"));
        if (parts.isEmpty()) return "0 dias";
        if (parts.size() == 1) return parts.get(0);
        return String.join(", ", parts.subList(0, parts.size() - 1))
                + " e "
                + parts.get(parts.size() - 1);
    }

    private void applyFields(ClientDisabilityPeriod entity, ClientDisabilityPeriodRequestDTO dto) {
        DisabilityGrade grade =
                RequestEnums.single("grade", dto.getGrade(), DisabilityGrade::fromLabel);
        if (grade == null) {
            throw new ValidationException("grade", ValidationErrorCode.REQUIRED_FIELD);
        }
        if (!grade.isDisability()) {
            // "Sem deficiência" existe no enum para ser destino de conversão,
            // não para ser gravado: um intervalo de deficiência sem deficiência
            // entraria na soma multiplicando por 1 e inflaria o total.
            throw new ValidationException(
                    "grade",
                    ValidationErrorCode.INVALID_ENUM_VALUE,
                    "o grau de um intervalo tem de ser Leve, Moderada ou Grave");
        }

        LocalDate startedOn = dto.getStartedOn();
        LocalDate endedOn = dto.getEndedOn();
        LocalDate today = OfficeClock.today();

        if (startedOn.isAfter(today)) {
            throw new ValidationException(
                    "startedOn",
                    ValidationErrorCode.INVALID_DATE,
                    "a deficiência não pode começar no futuro");
        }
        if (endedOn != null) {
            if (endedOn.isBefore(startedOn)) {
                throw new ValidationException(
                        "endedOn",
                        ValidationErrorCode.INVALID_DATE_RANGE,
                        "a data de cessação é anterior à data de início");
            }
            if (endedOn.isAfter(today)) {
                throw new ValidationException(
                        "endedOn",
                        ValidationErrorCode.INVALID_DATE,
                        "a deficiência não pode cessar no futuro; para uma deficiência que se "
                                + "mantém, deixe a data de cessação em branco");
            }
        }

        entity.setGrade(grade);
        entity.setStartedOn(startedOn);
        entity.setEndedOn(endedOn);
    }

    /**
     * Recusa intervalo que invada outro do mesmo cliente.
     *
     * <p>Sobreposição faria o mesmo dia contar duas vezes na soma, e com graus diferentes contaria
     * duas vezes com fatores diferentes. O banco não tem como expressar isso num CHECK, então a
     * regra mora aqui — e a mensagem nomeia o intervalo conflitante, porque "datas inválidas" não
     * diz a quem cadastrou qual das linhas ele precisa olhar.
     */
    private void rejectOverlap(UUID clientId, ClientDisabilityPeriod candidate, UUID ignoringId) {
        LocalDate today = OfficeClock.today();
        LocalDate newStart = candidate.getStartedOn();
        LocalDate newEnd = candidate.effectiveEnd(today);

        for (ClientDisabilityPeriod other :
                repository.findByClient_IdOrderByStartedOnAsc(clientId)) {
            if (ignoringId != null && other.getId().equals(ignoringId)) continue;

            LocalDate otherEnd = other.effectiveEnd(today);
            boolean overlaps = !newStart.isAfter(otherEnd) && !other.getStartedOn().isAfter(newEnd);
            if (overlaps) {
                throw new ValidationException(
                        "startedOn",
                        ValidationErrorCode.INVALID_DATE_RANGE,
                        "este intervalo se sobrepõe ao de "
                                + other.getStartedOn()
                                + (other.isOngoing()
                                        ? " em diante (sem data de cessação)"
                                        : " a " + other.getEndedOn()));
            }
        }
    }

    private ClientDisabilityPeriodResponseDTO toDTO(
            ClientDisabilityPeriod entity, LocalDate today) {
        ClientDisabilityPeriodResponseDTO dto = new ClientDisabilityPeriodResponseDTO();
        dto.setId(entity.getId());
        dto.setGrade(entity.getGrade());
        dto.setStartedOn(entity.getStartedOn());
        dto.setEndedOn(entity.getEndedOn());
        dto.setOngoing(entity.isOngoing());
        dto.setDays(DisabilityTimeConversion.daysOf(entity, today));
        return dto;
    }

    private Client findClientOrThrow(UUID clientId) {
        return clientRepository
                .findById(clientId)
                .orElseThrow(() -> NotFoundException.of("Cliente", clientId));
    }

    private ClientDisabilityPeriod findPeriodOrThrow(UUID clientId, UUID periodId) {
        return repository
                .findByIdAndClient_Id(periodId, clientId)
                .orElseThrow(() -> NotFoundException.of("Período de deficiência", periodId));
    }
}
