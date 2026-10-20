package com.lawfirm.law.firm.model;

import com.lawfirm.law.firm.audit.AuditLogListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Saída de caixa do escritório: aluguel, salário, software, imposto.
 *
 * <p>Não tem coluna de cliente, de propósito. Despesa de escritório não pertence a cliente, e custa
 * processual adiantada por um cliente específico é valor a reembolsar - que já tem lugar em {@link
 * ClientPayment}. Uma FK opcional aqui convidaria a registrar as duas coisas no mesmo lugar, e aí o
 * mesmo dinheiro sairia do resultado duas vezes.
 *
 * <p>Reaproveita {@link PaymentStatus} e {@link PaymentMethod}: uma despesa também está
 * Pendente/Paga/Cancelada e também sai por Pix. Um segundo par de enums com os mesmos valores só
 * daria duas listas para manter sincronizadas.
 *
 * <p>"Atrasada" não é status e não é persistido: deriva de Pendente + vencimento no passado, como
 * em {@link ClientPayment}.
 */
@Entity
@Table(name = "office_expenses")
@EntityListeners(AuditLogListener.class)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class OfficeExpense extends FinanceEntry {

    @Column(name = "category", length = 40)
    private ExpenseCategory category;

    @Column(name = "supplier", length = 255)
    private String supplier;
}
