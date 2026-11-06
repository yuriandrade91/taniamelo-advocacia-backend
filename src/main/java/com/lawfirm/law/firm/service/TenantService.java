package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.TenantPublicDTO;
import com.lawfirm.law.firm.dto.TenantResponseDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.storage.FileStorageException;
import com.lawfirm.law.firm.storage.FileStorageService;
import com.lawfirm.law.firm.storage.StoredFile;
import com.lawfirm.law.firm.tenant.TenantContext;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Consulta ao catálogo de escritórios ({@code public.tenants}) e a logo de cada um. */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository repository;
    private final FileStorageService fileStorageService;

    public TenantService(TenantRepository repository, FileStorageService fileStorageService) {
        this.repository = repository;
        this.fileStorageService = fileStorageService;
    }

    /** O que é seguro dizer antes do login: nunca o nome do schema físico. */
    @Transactional(readOnly = true)
    public TenantPublicDTO resolve(String slug) {
        Tenant tenant = bySlug(slug);
        return new TenantPublicDTO(
                tenant.getId().toString(),
                tenant.getSlug(),
                tenant.getRazaoSocial(),
                logoUrl(tenant));
    }

    @Transactional(readOnly = true)
    public TenantResponseDTO current() {
        return toDTO(currentTenant());
    }

    /**
     * Troca a logo do escritório da sessão. O arquivo novo vai para o storage antes, e sai de lá se
     * a linha não salvar; o anterior só sai depois que o novo já está no banco. Sem transação em
     * volta: o storage não participa dela, e a ordem acima é o que mantém os dois de acordo.
     */
    public TenantResponseDTO uploadLogo(MultipartFile file) {
        String contentType = LogoImage.contentTypeOf(file);
        Tenant tenant = currentTenant();
        String previous = tenant.getLogoKey();
        StoredFile stored = fileStorageService.store(file, tenant.getSchemaName() + "/office");
        Tenant saved;
        try {
            tenant.useLogo(stored.storageKey(), contentType);
            saved = repository.save(tenant);
        } catch (RuntimeException ex) {
            deleteQuietly(stored.storageKey());
            throw ex;
        }
        deleteQuietly(previous);
        return toDTO(saved);
    }

    /** Volta à marca padrão. Idempotente: sem logo, não há o que fazer. */
    public void removeLogo() {
        Tenant tenant = currentTenant();
        String previous = tenant.getLogoKey();
        if (previous == null) {
            return;
        }
        tenant.clearLogo();
        repository.save(tenant);
        deleteQuietly(previous);
    }

    /** A logo enviada pelo escritório do slug; 404 enquanto ele usa a marca padrão. */
    @Transactional(readOnly = true)
    public TenantLogo logo(String slug) {
        Tenant tenant = bySlug(slug);
        if (tenant.getLogoKey() == null) {
            throw NotFoundException.of("Logo do escritório", slug);
        }
        return new TenantLogo(
                fileStorageService.load(tenant.getLogoKey()).resource(),
                tenant.getLogoContentType(),
                version(tenant));
    }

    private Tenant bySlug(String slug) {
        return repository.findBySlug(slug).orElseThrow(() -> NotFoundException.of("Tenant", slug));
    }

    private Tenant currentTenant() {
        String schema = TenantContext.get();
        return repository
                .findBySchemaName(schema)
                .orElseThrow(() -> NotFoundException.of("Tenant", schema));
    }

    private void deleteQuietly(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            fileStorageService.delete(storageKey);
        } catch (FileStorageException ex) {
            // A troca já valeu; o que sobrou é um arquivo sem ninguém apontando para ele.
            log.warn("Logo órfã no storage, remova manualmente: {}", storageKey, ex);
        }
    }

    private static String version(Tenant tenant) {
        return String.valueOf(tenant.getLogoUpdatedAt().toEpochMilli());
    }

    /**
     * Caminho público e versionado; nulo para quem usa a marca padrão. O login devolve o mesmo
     * ({@link AuthService}), para a tela já entrar com a logo certa.
     */
    static String logoUrl(Tenant tenant) {
        if (tenant.getLogoKey() == null) {
            return null;
        }
        return "/api/v1/tenants/logo?slug="
                + URLEncoder.encode(tenant.getSlug(), StandardCharsets.UTF_8)
                + "&v="
                + version(tenant);
    }

    private static TenantResponseDTO toDTO(Tenant tenant) {
        TenantResponseDTO dto = new TenantResponseDTO();
        dto.setId(tenant.getId());
        dto.setSlug(tenant.getSlug());
        dto.setRazaoSocial(tenant.getRazaoSocial());
        dto.setCnpj(tenant.getCnpj());
        dto.setResponsavel(tenant.getResponsavel());
        dto.setEmail(tenant.getEmail());
        dto.setTelefone(tenant.getTelefone());
        dto.setPlano(tenant.getPlano());
        dto.setStatus(tenant.getStatus());
        dto.setCriadoEm(tenant.getCriadoEm());
        dto.setAtualizadoEm(tenant.getAtualizadoEm());
        dto.setLogoUrl(logoUrl(tenant));
        return dto;
    }
}
