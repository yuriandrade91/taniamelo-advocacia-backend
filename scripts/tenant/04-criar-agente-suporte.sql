-- ============================================================================
-- Cria um agente de suporte da plataforma (public.support_users).
--
-- Nenhuma conta nasce sozinha no boot: o agente que abre sessão de suporte em
-- qualquer escritório (docs/SUPORTE_MULTITENANT.md) entra por este script. Ele não
-- pertence a escritório nenhum, por isso é independente dos passos 1 e 2.
--
-- A senha é aleatória, gerada aqui e impressa UMA vez (linha "NOTICE"); só o hash
-- bcrypt (força 10, a mesma do PasswordEncoder da aplicação) é gravado.
--
-- NÃO coloque dado real neste arquivo dentro do repositório: copie para fora, edite a
-- cópia e rode a cópia.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

DO $$
DECLARE
    -- ▼▼▼ PREENCHA ▼▼▼ -------------------------------------------------------
    v_nome  text := 'PREENCHER: nome da pessoa';
    v_email text := 'PREENCHER: e-mail';
    -- ▲▲▲ --------------------------------------------------------------------

    v_senha text := translate(encode(gen_random_bytes(12), 'base64'), '+/=', 'xyz');
    v_id    uuid;
BEGIN
    IF v_nome LIKE 'PREENCHER%' OR v_email LIKE 'PREENCHER%' THEN
        RAISE EXCEPTION 'Preencha v_nome e v_email antes de rodar.';
    END IF;
    IF btrim(v_email) !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' THEN
        RAISE EXCEPTION 'E-mail inválido (%).', v_email;
    END IF;

    INSERT INTO public.support_users (full_name, email, password_hash, active)
    VALUES (btrim(v_nome), lower(btrim(v_email)), public.crypt(v_senha, public.gen_salt('bf', 10)), true)
    RETURNING id INTO v_id;

    RAISE NOTICE 'Agente criado: % | % | senha temporaria: %', v_id, lower(btrim(v_email)), v_senha;
END $$;
