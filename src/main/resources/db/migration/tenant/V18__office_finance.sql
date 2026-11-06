-- V18: o dinheiro DO ESCRITÓRIO ganha tabela própria.
--
-- Até aqui o único dinheiro modelado era `client_payments`, e o nome engana:
-- aquilo é o que o **INSS paga ao cliente** (atrasados da concessão, benefício
-- mensal, parcela de acordo). Não passa pelo caixa do escritório.
--
-- O honorário — a receita de quem trabalha — não tinha tabela nenhuma. A
-- Carteira somava `client_payments` como entrada e mostrava o dinheiro dos
-- clientes como faturamento: inflado por um fator que depende do tamanho dos
-- atrasados, com saldo plausível, positivo e falso.
--
-- A tentação é acrescentar uma coluna `tipo` em `client_payments`, já que o
-- formato é quase igual. Resolve no papel e não na prática: o primeiro
-- lançamento sem tipo, ou com o tipo errado, contamina os dois relatórios ao
-- mesmo tempo, e não há consulta capaz de separar depois. Tabela separada
-- torna a confusão impossível por construção — que é o único nível de garantia
-- que vale em dado financeiro.
--
-- Ambas ficam no schema do tenant: escritório é o tenant.

-- ── Despesas ──
--
-- Sem `client_id`, de propósito. Despesa de escritório não pertence a cliente;
-- custa processual adiantada por um cliente específico é valor a reembolsar, e
-- isso já tem lugar (`client_payments`). Uma FK opcional aqui convidaria a
-- registrar as duas coisas no mesmo lugar, que é o erro que esta migration
-- existe para evitar.
CREATE TABLE office_expenses (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    description    VARCHAR(255)  NOT NULL,
    amount         NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    category       VARCHAR(40),
    supplier       VARCHAR(255),
    due_date       DATE          NOT NULL,
    paid_date      DATE,
    status         VARCHAR(20)   NOT NULL DEFAULT 'Pendente',
    payment_method VARCHAR(20),
    notes          TEXT,
    created_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    updated_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at     TIMESTAMP,
    deleted_at     TIMESTAMP
);

-- ── Receitas ──
--
-- `client_id` é OPCIONAL: nem toda receita vem de cliente (parecer avulso,
-- reembolso). `source_payment_id` aponta o `client_payments` de origem quando o
-- honorário é percentual do êxito — é o que permite conferir "30% dos
-- atrasados" contra o valor de fato, meses depois, sem depender da memória de
-- quem lançou.
--
-- ON DELETE SET NULL nas duas: excluir um cliente não pode apagar a receita que
-- o escritório já apurou. O dinheiro entrou; o histórico contábil não é do
-- cliente.
CREATE TABLE office_revenues (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    description       VARCHAR(255)  NOT NULL,
    amount            NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    due_date          DATE          NOT NULL,
    paid_date         DATE,
    status            VARCHAR(20)   NOT NULL DEFAULT 'Pendente',
    payment_method    VARCHAR(20),
    client_id         UUID REFERENCES clients (id) ON DELETE SET NULL,
    source_payment_id UUID REFERENCES client_payments (id) ON DELETE SET NULL,
    notes             TEXT,
    created_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    updated_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at        TIMESTAMP,
    deleted_at        TIMESTAMP
);

-- ── Índices ──
--
-- Dois por tabela, e os dois são necessários porque as telas fazem perguntas
-- diferentes sobre a mesma linha:
--
--   due_date  -> "o que vence no mês" (competência), a tela de Pagamentos.
--   paid_date -> "o que entrou e saiu no mês" (caixa), a Carteira.
--
-- Sem o índice por paid_date, o resumo da Carteira varre a tabela inteira todo
-- mês. Parciais em `deleted_at IS NULL` porque nenhuma consulta da aplicação
-- olha linha excluída, no mesmo padrão de `client_payments`.
CREATE INDEX idx_office_expenses_due_active
    ON office_expenses (due_date)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_office_expenses_paid_active
    ON office_expenses (paid_date)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_office_revenues_due_active
    ON office_revenues (due_date)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_office_revenues_paid_active
    ON office_revenues (paid_date)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_office_revenues_client_active
    ON office_revenues (client_id)
    WHERE deleted_at IS NULL;
