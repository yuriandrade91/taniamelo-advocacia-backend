package com.lawfirm.law.firm.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liga a multi-tenancy por schema no Hibernate e garante a ordem correta na subida:
 *
 * <ul>
 *   <li>Registra o {@link MultiTenantConnectionProvider} e o {@link
 *       CurrentTenantIdentifierResolver} nas propriedades do Hibernate — a presença do connection
 *       provider já ativa o modo SCHEMA.
 *   <li>Faz o {@code EntityManagerFactory} depender do {@link MultiTenantFlywayMigrator}, para as
 *       migrations de todos os schemas rodarem antes de o Hibernate validar o mapeamento.
 * </ul>
 */
@Configuration
public class MultiTenantJpaConfig {

    @Bean
    public HibernatePropertiesCustomizer multiTenancyHibernateCustomizer(
            MultiTenantConnectionProvider<String> connectionProvider,
            CurrentTenantIdentifierResolver<String> tenantResolver) {
        return hibernateProperties -> {
            hibernateProperties.put(
                    AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            hibernateProperties.put(
                    AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }

    /**
     * Declara que o EntityManagerFactory só pode ser criado depois do bean que roda o Flyway
     * multi-schema. Estático porque é um BeanFactoryPostProcessor.
     */
    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor
            entityManagerFactoryDependsOnFlywayMigrator() {
        return new EntityManagerFactoryDependsOnPostProcessor("multiTenantFlywayMigrator");
    }

    /**
     * Impede o registro automático do {@link TenantResolutionFilter} como servlet filter avulso:
     * ele roda só onde é inserido explicitamente (cadeia do Spring Security, antes do filtro JWT),
     * para a ordem ser determinística.
     */
    @Bean
    public FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilterRegistration(
            TenantResolutionFilter filter) {
        FilterRegistrationBean<TenantResolutionFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
