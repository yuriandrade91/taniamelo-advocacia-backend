-- V12 (por tenant): exclusão de cliente vira soft delete.
--
-- Até aqui DELETE /clients/{id} chamava repository.deleteById: apagava a linha de
-- verdade e, pelas FKs ON DELETE CASCADE, levava junto endereços, entrevistas,
-- arquivos, pagamentos e TODO o histórico de situação - o registro que existe
-- justamente para contar o que aconteceu. Em appointments.client_id (ON DELETE
-- SET NULL) o compromisso ficava sem vínculo e, como o serviço zerava client_name
-- quando havia vínculo, sem nome nenhum: a audiência continuava na agenda sem
-- dizer de quem era.
ALTER TABLE clients ADD COLUMN deleted_at TIMESTAMP;

-- Índice parcial: as consultas do dia a dia filtram por deleted_at IS NULL, e
-- indexar só essas linhas mantém o índice do tamanho da base ativa.
CREATE INDEX idx_clients_deleted_at ON clients (deleted_at) WHERE deleted_at IS NOT NULL;

-- Os únicos precisam passar a ignorar excluídos. Sem isto, excluir um cliente e
-- cadastrá-lo de novo com o mesmo CPF estouraria unique violation no banco,
-- enquanto a checagem da aplicação (que já não enxerga o excluído) diria que o
-- CPF está livre - erro 500 onde deveria haver cadastro.
ALTER TABLE clients DROP CONSTRAINT IF EXISTS clients_cpf_key;
ALTER TABLE clients DROP CONSTRAINT IF EXISTS clients_nit_pis_key;
ALTER TABLE clients DROP CONSTRAINT IF EXISTS clients_benefit_number_key;

CREATE UNIQUE INDEX ux_clients_cpf_active
    ON clients (cpf) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_clients_nit_pis_active
    ON clients (nit_pis) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_clients_benefit_number_active
    ON clients (benefit_number) WHERE deleted_at IS NULL;
