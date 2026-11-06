-- ============================================================================
-- Opcional - redefinir a senha de um ou mais usuários de um escritório.
--
-- A senha de 02-criar-usuarios.sql é mostrada uma única vez; se ela se perder (ou
-- se a conta travar por tentativas erradas), este script gera outra. A API não
-- tem "esqueci a senha" nem redefinição por ADMIN: trocar a própria senha exige
-- saber a atual (PATCH /api/v1/users/me/password).
--
-- Cada senha nova é sorteada aqui e impressa uma vez (NOTICE). Não escreva a senha
-- num UPDATE com crypt('texto', ...): ela iria para o histórico do shell e do psql.
--
-- Também zera o contador de tentativas e o bloqueio temporário de login.
--
-- NÃO coloque dado real neste arquivo dentro do repositório: copie para fora,
-- edite a cópia e rode a cópia.
--
-- Atômico: se algum usuário da lista não existir, nenhuma senha é alterada.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

DO $$
DECLARE
    -- ▼▼▼ PREENCHA ▼▼▼ -------------------------------------------------------
    v_schema   text   := 'tenant_exemplo';
    -- O e-mail OU o username de cada conta.
    v_usuarios text[] := ARRAY['fulana@exemplo.adv.br', 'ciclano@exemplo.adv.br'];
    -- ▲▲▲ --------------------------------------------------------------------
    v_login text;
    v_senha text;
    v_id    uuid;
    v_n     integer;
BEGIN
    IF array_to_string(v_usuarios, ',') ILIKE '%@exemplo.adv.br%' THEN
        RAISE EXCEPTION 'Preencha v_usuarios antes de rodar: ainda há e-mail de exemplo.';
    END IF;
    IF v_usuarios IS NULL OR cardinality(v_usuarios) = 0 THEN
        RAISE EXCEPTION 'A lista v_usuarios está vazia.';
    END IF;
    IF v_schema !~ '^tenant_[a-z0-9_]{1,50}$' THEN
        RAISE EXCEPTION 'v_schema inválido (%).', v_schema;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.tenants WHERE schema_name = v_schema) THEN
        RAISE EXCEPTION 'O schema % não está no catálogo public.tenants.', v_schema;
    END IF;
    IF to_regclass(format('%I.users', v_schema)) IS NULL THEN
        RAISE EXCEPTION 'O schema % ainda não tem a tabela users. Reinicie a aplicação primeiro.', v_schema;
    END IF;

    PERFORM set_config('search_path', format('%I, public', v_schema), true);

    FOREACH v_login IN ARRAY v_usuarios LOOP
        v_senha := translate(encode(gen_random_bytes(12), 'base64'), '+/=', 'xyz');

        UPDATE users
           SET password_hash         = crypt(v_senha, gen_salt('bf', 10)),
               failed_login_attempts = 0,
               locked_until          = NULL,
               updated_at            = now()
         WHERE lower(email) = lower(btrim(v_login))
            OR lower(username) = lower(btrim(v_login))
        RETURNING id INTO v_id;
        GET DIAGNOSTICS v_n = ROW_COUNT;

        IF v_n = 0 THEN
            RAISE EXCEPTION 'Nenhum usuário com e-mail ou username % em %. Nada foi alterado.', v_login, v_schema;
        END IF;

        INSERT INTO audit_log (entity_name, entity_id, action, performed_by, detail)
        VALUES ('User', v_id, 'UPDATE', NULL, 'Senha redefinida por script SQL');

        RAISE NOTICE 'Senha temporaria de %: %', v_login, v_senha;
    END LOOP;
END $$;
