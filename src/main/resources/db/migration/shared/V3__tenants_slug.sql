-- V3 (compartilhada / public): slug do tenant.
-- Identificador público amigável (ex.: "meu-escritorio") usado pelo frontend em
-- subdomínio/URL/cabeçalho, ao lado do UUID (tenants.id) que é o id canônico
-- opaco. Nenhum dos dois expõe o nome físico do schema.
ALTER TABLE public.tenants ADD COLUMN slug VARCHAR(60);

ALTER TABLE public.tenants ALTER COLUMN slug SET NOT NULL;
ALTER TABLE public.tenants ADD CONSTRAINT ux_tenants_slug UNIQUE (slug);
