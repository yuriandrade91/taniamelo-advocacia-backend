-- Mantém os CPFs de demonstração fora das faixas usadas pelas fixtures antigas.
-- V23 é imutável: a versão aplicada em produção permanece com o checksum original.
DO $$
BEGIN
    IF current_schema() NOT IN ('tenant_demo', 'tenant_tania') THEN
        RETURN;
    END IF;

    UPDATE clients AS seeded
    SET cpf = seed.new_cpf
    FROM (VALUES
        ('d0000023-0000-4000-8000-000000000001'::uuid, '529.982.247-25', '870.111.111-66'),
        ('d0000023-0000-4000-8000-000000000002'::uuid, '111.444.777-35', '870.112.222-35'),
        ('d0000023-0000-4000-8000-000000000003'::uuid, '390.533.447-05', '870.113.333-04'),
        ('d0000023-0000-4000-8000-000000000004'::uuid, '168.995.350-09', '870.114.444-83'),
        ('d0000023-0000-4000-8000-000000000005'::uuid, '931.261.800-07', '870.115.555-52'),
        ('d0000023-1000-4000-8000-000000000001'::uuid, '295.379.955-93', '870.116.666-21'),
        ('d0000023-1000-4000-8000-000000000002'::uuid, '606.957.600-15', '870.117.777-09'),
        ('d0000023-1000-4000-8000-000000000003'::uuid, '718.561.280-75', '870.118.888-70')
    ) AS seed(id, old_cpf, new_cpf)
    WHERE seeded.id = seed.id
      AND seeded.cpf = seed.old_cpf
      AND seeded.deleted_at IS NULL
      AND NOT EXISTS (
          SELECT 1
          FROM clients AS existing
          WHERE existing.cpf = seed.new_cpf
            AND existing.deleted_at IS NULL
            AND existing.id <> seeded.id
      );
END $$;