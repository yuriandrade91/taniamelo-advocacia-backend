package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.*;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    private final ClientService clientService;
    private final ClientMapper mapper;

    public ClientController(ClientService clientService, ClientMapper mapper) {
        this.clientService = clientService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> create(
            @Valid @RequestBody ClientCreateRequestDTO createDto,
            UriComponentsBuilder uriBuilder) {
        ClientDetailsDTO created = clientService.create(mapper.fromCreate(createDto));
        URI location = uriBuilder.path("/api/v1/clients/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(ApiResponse.successObject(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ClientListResponseDTO>> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(name = "pageNumber", required = false) Integer pageNumberParam,
            @RequestParam(name = "pageSize", required = false) Integer pageSizeParam,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(name = "benefitType", required = false) List<BenefitType> benefitType,
            @RequestParam(name = "situation", required = false) List<Situation> situation,
            @RequestParam(name = "situation[]", required = false) List<Situation> situationArray,
            @RequestParam(name = "createdFrom", required = false) String createdFromStr,
            @RequestParam(name = "createdTo", required = false) String createdToStr) {

        int requestedPageNumber = (pageNumberParam != null) ? pageNumberParam : Math.max(1, page);
        int requestedPageSize = (pageSizeParam != null) ? pageSizeParam : (size <= 0 ? 10 : size);
        int pageIndex = requestedPageNumber - 1;

        // Support both `situation` and `situation[]` parameter naming (some clients send brackets)
        if ((situation == null || situation.isEmpty()) && situationArray != null && !situationArray.isEmpty()) {
            situation = situationArray;
        } else if (situation != null && situationArray != null && !situationArray.isEmpty()) {
            // merge unique values
            for (Situation s : situationArray) {
                if (!situation.contains(s)) situation.add(s);
            }
        }

        Instant createdFrom = parseInstant(createdFromStr, true);
        Instant createdTo = parseInstant(createdToStr, false);

        Page<ClientListResponseDTO> result = clientService.listSummary(
                pageIndex, requestedPageSize, searchTerm, benefitType, situation, createdFrom, createdTo);

        Pagination p = new Pagination(requestedPageNumber, requestedPageSize, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.successList(result.getContent(), p));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> getById(@PathVariable UUID id) {
        ClientDetailsDTO dto = clientService.findById(id)
                .orElseThrow(() -> new NotFoundException("Client not found with id: " + id));
        return ResponseEntity.ok(ApiResponse.successObject(dto));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<com.lawfirm.law.firm.dto.ClientSituationHistoryDTO>> historyById(
            @PathVariable UUID id,
            @RequestParam(name = "pageNumber", defaultValue = "1") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
    // ensure client exists
    clientService.findById(id).orElseThrow(() -> new NotFoundException("Client not found with id: " + id));
        int pageIndex = Math.max(0, pageNumber - 1);
    // return only the paged history content (data is array of history items)
    var page = clientService.historyByClientId(id, pageIndex, pageSize);
    Pagination p = new Pagination(pageNumber, pageSize, page.getTotalElements());
    return ResponseEntity.ok(ApiResponse.successList(page.getContent(), p));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> update(
            @PathVariable UUID id, @Valid @RequestBody ClientDetailsDTO client) {
        return ResponseEntity.ok(ApiResponse.successObject(clientService.update(id, client)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientPatchResponseDTO>> patch(
            @PathVariable UUID id, @RequestBody ClientPatchRequestDTO patch) {
        // load current state to decide which values actually change
        com.lawfirm.law.firm.dto.ClientDetailsDTO previous = clientService.findById(id)
                .orElseThrow(() -> new NotFoundException("Client not found with id: " + id));

        boolean changedSituation = false;
        if (patch.getSituation() != null) {
            try {
                com.lawfirm.law.firm.model.Situation incoming = com.lawfirm.law.firm.model.Situation.fromLabel(patch.getSituation());
                changedSituation = !java.util.Objects.equals(previous.getSituation(), incoming);
            } catch (IllegalArgumentException ex) {
                // invalid situation; delegate to service.patch to produce consistent validation error
                clientService.patch(id, patch);
            }
        }

        boolean changedNonBillable = false;
        if (patch.getNonBillable() != null) {
            changedNonBillable = !java.util.Objects.equals(previous.getNonBillable(), patch.getNonBillable());
        }

        // perform update (will record history if situation changed)
        clientService.patch(id, patch);

        java.util.List<String> msgs = new java.util.ArrayList<>();
        if (changedSituation) msgs.add("situação atualizada com sucesso!");
        if (changedNonBillable) msgs.add("arrecadação atualizada com sucesso");
        if (msgs.isEmpty()) msgs.add("nenhuma alteração realizada");

        ClientPatchResponseDTO resp = new ClientPatchResponseDTO(String.join(" e ", msgs));
        return ResponseEntity.ok(ApiResponse.successObject(resp));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Private helpers ──

    /**
     * Parses a date string as ISO-8601 instant or MM-dd-yyyy fallback.
     * Returns null if the string is blank or unparseable.
     */
    private static Instant parseInstant(String value, boolean startOfDay) {
        if (value == null || value.isBlank()) return null;
        String s = value.trim();
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) { }
        try {
            LocalDate date = LocalDate.parse(s, DATE_FMT);
            return startOfDay
                    ? date.atStartOfDay(ZoneOffset.UTC).toInstant()
                    : date.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) { }
        return null;
    }
}
