# Modelo de dados (baseline V1–V8)

Migrations Flyway em `src/main/resources/db/migration` — conjunto novo, coeso,
para subir o banco do zero (`V1__extensions` ... `V8__client_payments`).
Bancos criados com as migrations antigas (V1–V15 legadas) devem ser recriados:
`DROP SCHEMA public CASCADE; CREATE SCHEMA public;` e subir a aplicação.

## Tabelas

- **users** — usuários do sistema; admin inicial semeado pela aplicação.
- **clients** — agregado raiz. Únicos: `cpf`, `nit_pis`, `benefit_number`.
  Sem endereço embutido (removido), sem colunas geradas `first/last_name`
  (removidas). `inss_password` criptografada em repouso (AES-GCM).
- **client_addresses** — 1:N; exatamente um `is_primary` por cliente (índice
  único parcial); tipo Residencial/Comercial/Correspondência.
- **client_situation_history** — trilha de mudanças de situação; gravada
  automaticamente pela aplicação.
- **client_interviews** — entrevistas: `occurred_at` (data), `duration_minutes`
  (duração) e `content` (rich text do frontend, TEXT). Soft delete.
- **client_files** — collection ÚNICA de arquivos (abas Documentos e
  Simulações), discriminada por `kind`:
  - `DOCUMENT`: `document_type` = um dos 11 tipos (enum `DocumentType`).
  - `SIMULATION`: `simulation_date`, `version` (texto livre, ex. v1.0.2),
    `vinculos` (inteiro), `is_principal` (a mais recente vira principal
    automaticamente; índice único parcial garante uma só).
  - Binário NUNCA no banco: só `storage_key` + metadados. Soft delete.
- **client_payments** — parcelas de honorários; "Atrasado" é derivado em
  runtime, nunca persistido. Soft delete.

## Decisões de padrão (valem para o projeto inteiro)

1. **Listas fixas = enum Java** (com label PT-BR persistido via
   AttributeConverter). Sem CHECK em SQL e sem tabela de domínio — uma fonte de
   verdade única, versionada com o código. Vale para os 11 tipos de documento,
   situação, benefício, gênero, estado civil, tipo de endereço, status/forma de
   pagamento e tipo de cliente.
2. **updated_at é responsabilidade da aplicação** (@PrePersist/@PreUpdate) —
   sem triggers.
3. **Soft delete** (`deleted_at`) onde o registro pode ser evidência
   (arquivos, entrevistas, pagamentos); delete físico em cascata só do
   agregado cliente.
4. **Auditoria** `created_by/updated_by` com FK `users ON DELETE SET NULL`.

## Seeds

`db/mock-data/seed_mock.sql` popula todas as tabelas para exercitar todos os
endpoints (2 usuários com senha `password`, 5 clientes, endereços com
principal, histórico, entrevistas, os 11 tipos de documento, simulações com
principal e parcelas em todos os status).
