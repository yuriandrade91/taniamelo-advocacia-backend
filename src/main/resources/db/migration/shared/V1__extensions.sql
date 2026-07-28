-- Migration COMPARTILHADA (roda uma vez no schema public).
-- unaccent é um objeto de banco (não por-schema); fica em public e é
-- resolvido pelas queries dos tenants via search_path "<tenant>", public.
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;
