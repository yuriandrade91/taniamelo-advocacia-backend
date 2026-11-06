package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.AppointmentResponseDTO;
import com.lawfirm.law.firm.dto.ClientAddressResponseDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientDisabilityPeriodResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileDocumentResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationResponseDTO;
import com.lawfirm.law.firm.dto.ClientInterviewResponseDTO;
import com.lawfirm.law.firm.dto.ClientRequestStageHistoryDTO;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.PaymentListItemDTO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tudo o que vai impresso na ficha, já recortado pelo papel de quem pediu.
 *
 * <p>{@code paymentsVisible} existe ao lado da lista porque "sem parcelas" e "sem permissão para
 * vê-las" são respostas diferentes: a seção vazia diria a quem é STAFF que o cliente não tem nada a
 * receber, o que pode ser falso. Sem permissão, a seção simplesmente não é impressa.
 */
record ClientRecord(
        String officeName,
        Instant generatedAt,
        ClientDetailsDTO client,
        List<ClientAddressResponseDTO> addresses,
        List<ClientDisabilityPeriodResponseDTO> disabilityPeriods,
        List<ClientInterviewResponseDTO> interviews,
        List<ClientFileDocumentResponseDTO> documents,
        List<ClientFileSimulationResponseDTO> simulations,
        List<ClientSituationHistoryDTO> situationHistory,
        List<ClientRequestStageHistoryDTO> requestStageHistory,
        List<AppointmentResponseDTO> appointments,
        boolean paymentsVisible,
        List<PaymentListItemDTO> payments,
        Map<UUID, String> userNames) {}
