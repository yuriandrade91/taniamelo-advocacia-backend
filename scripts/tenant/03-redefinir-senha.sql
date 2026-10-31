-- ============================================================================
-- Opcional - redefinir a senha de um usuário de um escritório.
--
-- A senha de 02-criar-usuarios.sql é mostrada uma única vez; se ela se perder (ou
-- se a conta travar por tentativas erradas), este script gera outra. A API não
-- tem "esqueci a senha" nem redefinição por ADMIN: trocar a própria senha exige
-- saber a atual (PATCH /api/v1/users/me/password).
--
-- A senha nova é sorteada aqui e impressa uma vez (NOTICE). Não escreva a senha
-- num UPDATE com crypt('texto', ...): ela iria para o histórico do shell e do psql.
--
-- Também zera o contador de tentativas e o bloqueio temporário de login.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

DO $$
DECLARE
    -- ▼▼▼ PREENCHA ▼▼▼ -------------------------------------------------------
    v_schema text := 'tenant_prado';
    v_email  text := 'matheus.prado@prado.adv.com';   -- o e-mail OU o username do usuário
    -- ▲▲▲ --------------------------------------------------------------------
    v_senha  text := translate(encode(gen_random_bytes(12), 'base64'), '+/=', 'xyz');
    v_id     uuid;
BEGIN
    IF v_email ILIKE '%matheus.prado@prado.adv.com%' THEN
        RAISE EXCEPTION 'Preencha v_email antes de rodar.';
    END IF;
    IF v_schema !~ '^tenant_[a-z0-9_]{1,50}$' THEN
        RAISE EXCEPTION 'v_schema inválido (%).', v_schema;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.tenants WHERE schema_name = v_schema) THEN
        RAISE EXCEPTION 'O schema % não está no catálogo public.tenants.', v_schema;
    END IF;

    PERFORM set_config('search_path', format('%I, public', v_schema), true);

    UPDATE users
       SET password_hash         = crypt(v_senha, gen_salt('bf', 10)),
           failed_login_attempts = 0,
           locked_until          = NULL,
           updated_at            = now()
     WHERE lower(email) = lower(btrim(v_email))
        OR lower(username) = lower(btrim(v_email))
    RETURNING id INTO v_id;

    IF v_id IS NULL THEN
        RAISE EXCEPTION 'Nenhum usuário com e-mail ou username % em %.', v_email, v_schema;
    END IF;

    INSERT INTO audit_log (entity_name, entity_id, action, performed_by, detail)
    VALUES ('User', v_id, 'UPDATE', NULL, 'Senha redefinida por script SQL');

    RAISE NOTICE 'Senha temporaria de %: %', v_email, v_senha;
END $$;
