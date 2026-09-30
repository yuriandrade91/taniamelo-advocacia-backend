-- V20 (por tenant): atribuição do agente de suporte na trilha de auditoria.
--
-- Quando um usuário de suporte da plataforma age dentro de um tenant
-- (impersonation), ele NÃO existe na tabela users deste schema - então
-- performed_by (FK -> users) fica NULL. Esta coluna guarda QUEM de verdade
-- executou a ação: o id do support_user (em public.support_users). Sem FK de
-- propósito (cruzaria schema); é referência lógica, documentada.
ALTER TABLE audit_log
    ADD COLUMN acting_support_user_id UUID;

COMMENT ON COLUMN audit_log.acting_support_user_id IS
    'public.support_users.id quando a acao veio de uma sessao de suporte (impersonation); NULL caso contrario.';
