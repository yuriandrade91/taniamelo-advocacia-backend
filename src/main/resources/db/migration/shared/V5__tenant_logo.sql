-- V5 (compartilhada / public): logo do escritório.
--
-- A imagem vive no storage (disco ou S3), na pasta do schema do tenant, como os
-- arquivos de cliente; aqui ficam só a chave, o tipo e quando mudou. Tudo NULL
-- é o estado de quem nunca enviou logo: o front mostra a marca padrão.
-- logo_updated_at vai na URL pública como versão, para o navegador trocar a
-- imagem assim que ela muda.
ALTER TABLE public.tenants
    ADD COLUMN logo_key          VARCHAR(500),
    ADD COLUMN logo_content_type VARCHAR(100),
    ADD COLUMN logo_updated_at   TIMESTAMPTZ;
