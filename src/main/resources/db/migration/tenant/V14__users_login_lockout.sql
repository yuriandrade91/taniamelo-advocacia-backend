-- V14 (por tenant): bloqueio temporário de conta após tentativas de login falhas.
--
-- O login não tinha nenhuma defesa contra força bruta: nem limite de tentativas,
-- nem bloqueio, com a instância em IP público e a porta aberta. Um dicionário
-- contra `dr.tania` rodava a noite inteira sem encontrar resistência.
--
-- Esta é a metade persistida da defesa: ela sobrevive a restart e vale para
-- qualquer número de instâncias, porque o estado está no banco. A outra metade
-- (limite por IP e por login em memória) está no LoginThrottleService e reage
-- mais rápido, mas é por instância.
ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;

-- Nulo = conta liberada. Guardar o instante, e não um booleano, faz o desbloqueio
-- acontecer sozinho com a passagem do tempo - sem job, sem alguém para lembrar.
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP;
