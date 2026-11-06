-- Só na suíte (src/test): o catálogo de produção nasce vazio, mas os testes precisam de dois
-- escritórios para provar o isolamento entre schemas. O Flyway varre o mesmo caminho
-- (db/migration/shared) em todos os diretórios do classpath, então este arquivo entra sozinho
-- no banco de teste e nunca no JAR. A versão alta evita colidir com migration nova de produção.
INSERT INTO public.tenants (schema_name, slug, razao_social, cnpj, responsavel, email, telefone, plano, status) VALUES
 ('tenant_tania', 'tania', 'Escritório de Teste A', '12.345.678/0001-90', 'Responsável A', 'a@teste.invalid', '(31) 3333-1000', 'pro',   'ativo'),
 ('tenant_demo',  'demo',  'Escritório de Teste B', '98.765.432/0001-10', 'Responsável B', 'b@teste.invalid', '(11) 4444-2000', 'trial', 'ativo');
