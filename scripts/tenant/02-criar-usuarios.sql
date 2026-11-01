-- ============================================================================
-- Passo 2 de 2 - criar os usuários de um escritório (tenant).
--
-- Pré-requisito: o passo 1 foi rodado E a aplicação reiniciou, de modo que o
-- Flyway já criou o schema. Não há endpoint para criar usuário: a API só lista,
-- edita o próprio perfil e troca a própria senha. Este script é o caminho - e
-- serve também para acrescentar gente a um escritório que já funciona.
--
-- SENHAS. Cada usuário recebe uma senha temporária aleatória, gerada aqui dentro
-- e impressa UMA vez (linhas "NOTICE") no fim da execução. Nada é gravado em
-- texto: só o hash bcrypt (força 10, a mesma do BCryptPasswordEncoder da
-- aplicação). Por isso não há senha no arquivo, no histórico do shell nem no
-- log do Postgres. Não existe "trocar no primeiro acesso" (ver
-- docs/CADASTRO_DE_TENANT.md): peça a cada pessoa que troque a dela em
-- PATCH /api/v1/users/me/password (mínimo de 8 caracteres).
--
-- Perdeu a senha de alguém? 03-redefinir-senha.sql gera outra.
--
-- NÃO coloque dado real neste arquivo dentro do repositório: copie para fora,
-- edite a cópia e rode a cópia.
--
-- Atômico: se qualquer validação falhar, nenhum usuário é criado.
-- ============================================================================

-- O hash bcrypt e o sorteio das senhas vêm do pgcrypto. É extensão "trusted" desde o
-- Postgres 13 (o dono do banco instala) e fica no public, ao lado do unaccent.
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

DO $$
DECLARE
    -- ▼▼▼ PREENCHA ▼▼▼ -------------------------------------------------------
    v_schema text := 'tenant_exemplo';

    -- Papéis: ADMIN (tudo, inclusive o financeiro), SUPPORT (perfil de advogado:
    -- exclui e restaura registros), STAFF (o dia a dia). "username" é opcional;
    -- sem ele vale a parte do e-mail antes do @. Exige ao menos um ADMIN.
    v_usuarios jsonb := '[
        {"nome": "Fulana de Tal",  "email": "fulana@exemplo.adv.br", "username": "fulana", "role": "ADMIN"},
        {"nome": "Ciclano de Tal", "email": "ciclano@exemplo.adv.br", "username": "ciclano", "role": "STAFF"}
    ]';

    -- Só no primeiro uso, logo depois do passo 1: a aplicação cria sozinha um ADMIN
    -- "Administrador" com o APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD GLOBAIS do .env num
    -- schema vazio. Isso daria ao escritório um login com a credencial de outro.
    -- Informe aqui o e-mail dele para removê-lo. Descubra com:
    --   grep ^APP_ADMIN_EMAIL= .env
    -- NULL = não remove nada (use NULL ao acrescentar usuários a um escritório em uso).
    v_remover_admin_padrao text := NULL;
    -- ▲▲▲ --------------------------------------------------------------------

    v_roles_validos constant text[] := ARRAY['ADMIN', 'SUPPORT', 'STAFF'];
    v_emails        text[];
    v_removido      uuid;
    r               record;
BEGIN
    IF v_usuarios::text ILIKE '%@exemplo.adv.br%' THEN
        RAISE EXCEPTION 'Preencha v_usuarios antes de rodar: ainda há e-mail de exemplo.';
    END IF;
    IF v_schema !~ '^tenant_[a-z0-9_]{1,50}$' THEN
        RAISE EXCEPTION 'v_schema inválido (%).', v_schema;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.tenants WHERE schema_name = v_schema) THEN
        RAISE EXCEPTION 'O schema % não está no catálogo public.tenants. Rode o passo 1 antes.', v_schema;
    END IF;
    IF to_regclass(format('%I.users', v_schema)) IS NULL THEN
        RAISE EXCEPTION 'O schema % ainda não tem a tabela users. Reinicie a aplicação e confira no log "Flyway: migrations de tenant aplicadas (schema %)".',
            v_schema, v_schema;
    END IF;

    -- Daqui em diante os nomes sem schema (users, audit_log) valem para o escritório.
    -- is_local = true: volta ao normal sozinho no fim da transação.
    PERFORM set_config('search_path', format('%I, public', v_schema), true);

    -- Valida a lista inteira antes de gravar qualquer coisa.
    SELECT array_agg(lower(btrim(u.email)))
      INTO v_emails
      FROM jsonb_to_recordset(v_usuarios) AS u(nome text, email text, username text, role text);

    IF v_emails IS NULL THEN
        RAISE EXCEPTION 'A lista v_usuarios está vazia.';
    END IF;


    IF EXISTS (SELECT 1
                 FROM jsonb_to_recordset(v_usuarios) AS u(nome text, email text, username text, role text)
                WHERE btrim(coalesce(u.nome, '')) = ''
                   OR coalesce(btrim(u.email), '') !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$') THEN
        RAISE EXCEPTION 'Há usuário sem nome ou com e-mail inválido em v_usuarios.';
    END IF;

    -- O papel é validado só no código Java (sem CHECK no banco): um valor desconhecido
    -- derruba o login desse usuário. Por isso confere aqui.
    IF EXISTS (SELECT 1
                 FROM jsonb_to_recordset(v_usuarios) AS u(nome text, email text, username text, role text)
                WHERE upper(btrim(coalesce(u.role, ''))) <> ALL (v_roles_validos)) THEN
        RAISE EXCEPTION 'Papel inválido em v_usuarios. Use um destes: %.', v_roles_validos;
    END IF;
    IF NOT EXISTS (SELECT 1
                     FROM jsonb_to_recordset(v_usuarios) AS u(nome text, email text, username text, role text)
                    WHERE upper(btrim(u.role)) = 'ADMIN') THEN
        RAISE EXCEPTION 'v_usuarios precisa ter ao menos um ADMIN.';
    END IF;

    IF v_remover_admin_padrao IS NOT NULL
       AND lower(btrim(v_remover_admin_padrao)) = ANY (v_emails) THEN
        RAISE EXCEPTION 'v_remover_admin_padrao (%) está também na lista de novos usuários.', v_remover_admin_padrao;
    END IF;

    -- E-mail e username são únicos no banco diferenciando maiúsculas, mas o login
    -- ignora a caixa (findByEmailIgnoreCase...). Gravar tudo em minúsculas evita dois
    -- usuários que o login confundiria. E-mail ou username repetido, na lista ou no
    -- banco, é recusado pelas restrições UNIQUE e desfaz tudo.
    FOR r IN
        WITH lista AS MATERIALIZED (
            SELECT btrim(u.nome)                                                                      AS nome,
                   lower(btrim(u.email))                                                              AS email,
                   lower(coalesce(nullif(btrim(u.username), ''), split_part(btrim(u.email), '@', 1))) AS username,
                   upper(btrim(u.role))                                                               AS role,
                   -- 16 caracteres de base64 sem +/= (que atrapalham copiar e digitar).
                   translate(encode(gen_random_bytes(12), 'base64'), '+/=', 'xyz')                    AS senha
              FROM jsonb_to_recordset(v_usuarios) AS u(nome text, email text, username text, role text)
        ), criados AS (
            INSERT INTO users (full_name, email, username, role, password_hash, active)
            SELECT nome, email, username, role, crypt(senha, gen_salt('bf', 10)), true
              FROM lista
            RETURNING id, email
        )
        SELECT c.id, l.email, l.username, l.role, l.senha
          FROM criados c
          JOIN lista l ON l.email = c.email
         ORDER BY l.role, l.email
    LOOP
        -- performed_by NULL = "foi o sistema/operação", como na V19.
        INSERT INTO audit_log (entity_name, entity_id, action, performed_by, detail)
        VALUES ('User', r.id, 'CREATE', NULL, 'Criado por script SQL (provisionamento do tenant)');

        RAISE NOTICE '% | % | usuario: % | senha temporaria: %', r.role, r.email, r.username, r.senha;
    END LOOP;

    IF v_remover_admin_padrao IS NOT NULL THEN
        -- Todas as chaves para users são ON DELETE SET NULL (refresh_tokens, CASCADE):
        -- apagar não deixa referência pendurada.
        DELETE FROM users
         WHERE lower(email) = lower(btrim(v_remover_admin_padrao))
        RETURNING id INTO v_removido;

        IF v_removido IS NULL THEN
            RAISE EXCEPTION 'v_remover_admin_padrao (%) não existe em %. Confira o e-mail.',
                v_remover_admin_padrao, v_schema;
        END IF;

        INSERT INTO audit_log (entity_name, entity_id, action, performed_by, detail)
        VALUES ('User', v_removido, 'DELETE', NULL, 'ADMIN padrao do seeder removido (provisionamento do tenant)');
        RAISE NOTICE 'ADMIN padrão removido: %', v_remover_admin_padrao;
    END IF;

    -- Quadro final do escritório, para conferir se sobrou algo que não devia.
    FOR r IN SELECT email, username, role, active FROM users ORDER BY role, email LOOP
        RAISE NOTICE 'em % -> % | % | ativo=% | usuario=%', v_schema, r.role, r.email, r.active, r.username;
    END LOOP;
END $$;
