-- ============================================================================
-- Passo 1 de 2 - cadastrar um escritório (tenant) no catálogo public.tenants.
--
-- Este script só escreve UMA linha no catálogo. Quem cria o schema e as tabelas é
-- o Flyway, no próximo boot da aplicação: o MultiTenantFlywayMigrator lê os
-- tenants 'ativo' daqui e migra cada um. Criar o schema ou as tabelas à mão
-- deixaria o escritório sem flyway_schema_history e quebraria a próxima migration.
--
-- Depois de rodar: reinicie a aplicação e então rode 02-criar-usuarios.sql.
-- Roteiro completo: docs/CADASTRO_DE_TENANT.md.
--
-- NÃO coloque dado real neste arquivo dentro do repositório. Copie-o para fora
-- (cp scripts/tenant/01-cadastrar-tenant.sql ~/01-prado.sql), edite a cópia e
-- rode a cópia.
--
-- Atômico: tudo ou nada. Se alguma validação falhar, nada é gravado.
-- ============================================================================

DO $$
DECLARE
    -- ▼▼▼ PREENCHA ▼▼▼ -------------------------------------------------------
    v_slug         text := 'prado';         -- identificador público (URL, X-Tenant-Id). Minúsculas, números e hífen.
    v_schema       text := 'tenant_prado';  -- nome físico do schema. Sempre 'tenant_' + algo. Nunca exposto na API.
    v_razao_social text := 'Escritório Prado Advocacia'; -- nome completo do escritório (é UNIQUE)
    v_cnpj         text := '00.000.000/0000-00';     -- no formato 00.000.000/0000-00 (é UNIQUE)
    -- Opcionais: deixe NULL se não souber (sem aspas).
    v_responsavel  text := NULL;
    v_email        text := NULL;
    v_telefone     text := NULL;            -- até 20 caracteres
    v_plano        text := 'trial';            -- texto livre; hoje existem 'pro' e 'trial'
    -- ▲▲▲ --------------------------------------------------------------------
    v_id uuid;
BEGIN
    IF v_razao_social ILIKE '%PREENCHER%' OR v_cnpj ILIKE '%PREENCHER%' THEN
        RAISE EXCEPTION 'Preencha v_razao_social e v_cnpj antes de rodar.';
    END IF;

    -- O nome do schema vira SQL no migrator e no restante do projeto: não pode ser livre.
    IF v_schema !~ '^tenant_[a-z0-9_]{1,50}$' THEN
        RAISE EXCEPTION 'v_schema inválido (%). Use ''tenant_'' + minúsculas, números ou _.', v_schema;
    END IF;
    IF v_slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$' OR length(v_slug) > 60 THEN
        RAISE EXCEPTION 'v_slug inválido (%). Use minúsculas, números e hífen, até 60 caracteres.', v_slug;
    END IF;
    IF v_cnpj !~ '^\d{2}\.\d{3}\.\d{3}/\d{4}-\d{2}$' THEN
        RAISE EXCEPTION 'v_cnpj inválido (%). Formato esperado: 00.000.000/0000-00.', v_cnpj;
    END IF;

    -- O Flyway cria o schema com o usuário da APLICAÇÃO. Se ele não puder, o boot
    -- inteiro falha - para todos os escritórios, não só para este. Rode este script
    -- com o mesmo usuário que a aplicação usa (SPRING_DATASOURCE_USERNAME).
    IF NOT has_database_privilege(current_user, current_database(), 'CREATE') THEN
        RAISE EXCEPTION 'O usuário % não tem CREATE no banco %: o Flyway não conseguiria criar o schema.',
            current_user, current_database();
    END IF;

    -- Sobra de uma tentativa anterior. Prefiro parar a deixar o Flyway migrar em cima
    -- de um schema que alguém já mexeu.
    IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = v_schema) THEN
        RAISE EXCEPTION 'O schema % já existe no banco. Confira o que é antes de cadastrar.', v_schema;
    END IF;

    -- slug, schema e CNPJ duplicados são recusados pelas restrições UNIQUE da tabela.
    INSERT INTO public.tenants (schema_name, slug, razao_social, cnpj, responsavel, email, telefone, plano, status)
    VALUES (v_schema, v_slug, v_razao_social, v_cnpj, v_responsavel, v_email, v_telefone, v_plano, 'ativo')
    RETURNING id INTO v_id;

    RAISE NOTICE 'Tenant cadastrado: id=%, slug=%, schema=%.', v_id, v_slug, v_schema;
    RAISE NOTICE 'PRÓXIMO PASSO: reinicie a aplicação (o Flyway cria e migra o schema no boot) e rode 02-criar-usuarios.sql.';
END $$;

-- Conferência: o novo tenant tem de aparecer aqui com status 'ativo'.
SELECT slug, schema_name, razao_social, cnpj, plano, status
  FROM public.tenants
 ORDER BY criado_em;
