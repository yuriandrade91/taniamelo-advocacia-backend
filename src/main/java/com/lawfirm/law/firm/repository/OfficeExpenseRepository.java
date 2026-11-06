package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.dto.CategoryAmountDTO;
import com.lawfirm.law.firm.model.OfficeExpense;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OfficeExpenseRepository
        extends JpaRepository<OfficeExpense, UUID>, JpaSpecificationExecutor<OfficeExpense> {

    /**
     * Composição do gasto por categoria, no período.
     *
     * <p>Só o que foi <b>pago</b>: incluir o pendente mudaria o gráfico de "no que gastamos" para
     * "no que pretendemos gastar", que é outra pergunta — e a resposta errada para quem está
     * olhando o mês fechado.
     *
     * <p>Recorta por {@code paidDate} porque a pergunta é de caixa. Despesa paga fora da janela não
     * entra mesmo que tenha vencido dentro dela.
     *
     * <p>As bordas nunca chegam nulas: quem chama troca a ausência por uma data extrema. A forma
     * natural — {@code :de IS NULL OR ...} — quebra no Postgres com "could not determine data type
     * of parameter", porque um parâmetro solto num {@code IS NULL} não dá ao banco nenhuma pista do
     * tipo. Achado pelo teste de integração; num teste com mock não apareceria.
     */
    @Query(
            """
            SELECT new com.lawfirm.law.firm.dto.CategoryAmountDTO(
                     COALESCE(CAST(e.category AS string), 'Outros'), SUM(e.amount))
              FROM OfficeExpense e
             WHERE e.status = com.lawfirm.law.firm.model.PaymentStatus.PAGO
               AND e.paidDate >= :de
               AND e.paidDate <= :ate
             GROUP BY e.category
             ORDER BY SUM(e.amount) DESC
            """)
    List<CategoryAmountDTO> somarPorCategoria(
            @Param("de") LocalDate de, @Param("ate") LocalDate ate);
}
