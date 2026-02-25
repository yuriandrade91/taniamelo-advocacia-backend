-- Update clients_situation_check to reflect current Situation enum labels
-- This migration will drop the existing check (if any) and recreate it with the allowed labels

ALTER TABLE clients DROP CONSTRAINT IF EXISTS clients_situation_check;

ALTER TABLE clients ADD CONSTRAINT clients_situation_check CHECK (
    situation IN (
        'Formulário preenchido',
        'Análise documental',
        'Planejamento em execução',
        'Planejamento concluído',
        'Benefício futuro',
        'Benefício concluído'
    )
);
