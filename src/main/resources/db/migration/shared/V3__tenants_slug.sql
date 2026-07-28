-- V3 (compartilhada / public): slug do tenant.
-- Identificador público amigável (ex.: "tania", "demo") usado pelo frontend em
-- subdomínio/URL/cabeçalho, ao lado do UUID (tenants.id) que é o id canônico
-- opaco. Nenhum dos dois expõe o nome físico do schema.
ALTER TABLE public.tenants ADD COLUMN slug VARCHAR(60);

UPDATE public.tenants SET slug = 'tania' WHERE schema_name = 'tenant_tania';
UPDATE public.tenants SET slug = 'demo'  WHERE schema_name = 'tenant_demo';

ALTER TABLE public.tenants ALTER COLUMN slug SET NOT NULL;
ALTER TABLE public.tenants ADD CONSTRAINT ux_tenants_slug UNIQUE (slug);
