package com.lawfirm.law.firm.tenant;

import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.repository.TenantRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Traduz o identificador PÚBLICO do tenant (UUID canônico {@code tenants.id} OU {@code slug}) para
 * o nome físico do SCHEMA - que nunca é exposto na API. Carrega o catálogo {@code public.tenants}
 * sob demanda e mantém em cache; {@link #reload()} atualiza (ex.: após cadastrar um novo
 * escritório).
 *
 * <p>Assim, header/JWT/respostas trafegam só o UUID/slug; a topologia de banco (schema) fica
 * interna.
 */
@Component
public class TenantRegistry {

    private final TenantRepository tenantRepository;
    private volatile Map<String, String> publicToSchema;

    public TenantRegistry(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /** Schema do tenant a partir do UUID ou do slug público. Vazio se desconhecido/inativo. */
    public Optional<String> schemaFor(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache().get(publicId.trim()));
    }

    public void reload() {
        this.publicToSchema = load();
    }

    private Map<String, String> cache() {
        Map<String, String> local = this.publicToSchema;
        if (local == null) {
            synchronized (this) {
                local = this.publicToSchema;
                if (local == null) {
                    local = load();
                    this.publicToSchema = local;
                }
            }
        }
        return local;
    }

    private Map<String, String> load() {
        Map<String, String> map = new HashMap<>();
        for (Tenant t : tenantRepository.findByStatusOrderByRazaoSocialAsc("ativo")) {
            map.put(t.getId().toString(), t.getSchemaName());
            map.put(t.getSlug(), t.getSchemaName());
        }
        return map;
    }
}
