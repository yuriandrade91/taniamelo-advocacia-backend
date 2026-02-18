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
            @RequestParam(name = "createdFrom", required = false) String createdFromStr,
            @RequestParam(name = "createdTo", required = false) String createdToStr) {

        int requestedPageNumber = (pageNumberParam != null) ? pageNumberParam : Math.max(1, page);
        int requestedPageSize = (pageSizeParam != null) ? pageSizeParam : (size <= 0 ? 10 : size);
        int pageIndex = requestedPageNumber - 1;

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

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> update(
            @PathVariable UUID id, @Valid @RequestBody ClientDetailsDTO client) {
        return ResponseEntity.ok(ApiResponse.successObject(clientService.update(id, client)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> patch(
            @PathVariable UUID id, @RequestBody ClientPatchRequestDTO patch) {
        return ResponseEntity.ok(ApiResponse.successObject(clientService.patch(id, patch)));
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
