package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.*;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;
    private final ClientMapper mapper;

    public ClientController(ClientService clientService, ClientMapper mapper) {
        this.clientService = clientService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> create(@Valid @RequestBody ClientCreateRequestDTO createDto, UriComponentsBuilder uriBuilder) {
        ClientDetailsDTO dto = mapper.fromCreate(createDto);
        ClientDetailsDTO created = clientService.create(dto);
        URI location = uriBuilder.path("/api/v1/clients/{id}").buildAndExpand(created.getId()).toUri();
        ApiResponse<ClientDetailsDTO> response = ApiResponse.successObject(created);
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ClientListResponseDTO>> listAll(
            @RequestParam(defaultValue = "1") int page, // API now expects 1-based page
            @RequestParam(defaultValue = "10") int size,
            // Support alternate parameter names used by some Swagger UI setups
            @RequestParam(name = "pageNumber", required = false) Integer pageNumberParam,
            @RequestParam(name = "pageSize", required = false) Integer pageSizeParam,
        @RequestParam(required = false) String searchTerm,
            @RequestParam(name = "benefitType", required = false) List<BenefitType> benefitType,
            @RequestParam(name = "situation", required = false) List<Situation> situation
            ,@RequestParam(name = "createdFrom", required = false) String createdFromStr
            ,@RequestParam(name = "createdTo", required = false) String createdToStr
    ) {
    // Determine requested page/size: prefer explicit pageNumber/pageSize if provided
    int requestedPageNumber = (pageNumberParam != null) ? pageNumberParam : (page <= 0 ? 1 : page); // 1-based for API
    int requestedPageSize = (pageSizeParam != null) ? pageSizeParam : (size <= 0 ? 10 : size);
    int requestedPageIndex = Math.max(0, requestedPageNumber - 1); // 0-based for service

    // parse createdFrom/createdTo params if present (expected format per param: MM-DD-YYYY)
    java.time.Instant createdFrom = null;
    java.time.Instant createdTo = null;
    // Accept ISO-8601 instants (e.g. 2026-02-01T00:00:00.000Z) while keeping the
    // previous MM-dd-yyyy fallback for backward compatibility.
    java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MM-dd-yyyy");
    try {
        if (createdFromStr != null && !createdFromStr.isBlank()) {
            String s = createdFromStr.trim();
            try {
                // try strict ISO instant first
                createdFrom = java.time.Instant.parse(s);
            } catch (java.time.format.DateTimeParseException isoEx) {
                // fallback to previous MM-dd-yyyy parsing (start of day UTC)
                java.time.LocalDate from = java.time.LocalDate.parse(s, fmt);
                createdFrom = from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            }
        }
        if (createdToStr != null && !createdToStr.isBlank()) {
            String s = createdToStr.trim();
            try {
                // try strict ISO instant first
                createdTo = java.time.Instant.parse(s);
            } catch (java.time.format.DateTimeParseException isoEx) {
                // fallback to previous MM-dd-yyyy parsing (end of day UTC)
                java.time.LocalDate to = java.time.LocalDate.parse(s, fmt);
                createdTo = to.atTime(23, 59, 59).atZone(java.time.ZoneOffset.UTC).toInstant();
            }
        }
    } catch (Exception ex) {
        // invalid format -> ignore range to keep backward compatibility
    }

    org.springframework.data.domain.Page<ClientListResponseDTO> result = clientService.listSummary(requestedPageIndex, requestedPageSize, searchTerm, benefitType, situation, createdFrom, createdTo);

    // build Pagination using requested values and Page metadata
    Pagination p = new Pagination(requestedPageNumber, requestedPageSize, result.getTotalElements());
    p.setTotalPages(result.getTotalPages());
    p.setHasNextPage(result.hasNext());
    p.setHasPreviousPage(result.hasPrevious());

    return ResponseEntity.ok(ApiResponse.successList(result.getContent(), p));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> getById(@PathVariable UUID id) {
        return clientService.findById(id)
                .map(client -> ResponseEntity.ok(ApiResponse.successObject(client)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(java.util.List.of(new ApiError(null, "Client not found", "NOT_FOUND")))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> update(@PathVariable UUID id, @Valid @RequestBody ClientDetailsDTO client) {
        ClientDetailsDTO updated = clientService.update(id, client);
        return ResponseEntity.ok(ApiResponse.successObject(updated));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> patch(@PathVariable UUID id, @RequestBody com.lawfirm.law.firm.dto.ClientPatchRequestDTO patch) {
        ClientDetailsDTO updated = clientService.patch(id, patch);
        return ResponseEntity.ok(ApiResponse.successObject(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
