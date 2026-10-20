package com.lawfirm.law.firm.model;

import com.lawfirm.law.firm.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Colunas que {@link ClientPayment}, {@link OfficeExpense} e {@link OfficeRevenue} têm em comum:
 * valor, vencimento, pagamento, situação e autoria.
 *
 * <p><b>É só mapeamento, não herança de domínio.</b> {@code @MappedSuperclass} não é entidade: cada
 * subclasse continua na sua tabela, e não existe consulta que traga as três juntas ({@code FROM
 * FinanceEntry} nem compila). Isso é o que importa aqui — dinheiro do cliente, gasto e receita do
 * escritório não podem ser somados por engano (ver {@link ClientPayment}).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class FinanceEntry implements Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDENTE;

    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = PaymentStatus.PENDENTE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
