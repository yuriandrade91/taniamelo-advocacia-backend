package com.lawfirm.law.firm.model;

import com.lawfirm.law.firm.audit.AuditLogListener;
import com.lawfirm.law.firm.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Um intervalo em que a deficiência do cliente foi reconhecida, num grau.
 *
 * <p>{@code endedOn} nulo significa "a deficiência se mantém até a presente data", opção que a tela
 * oferece com essas palavras. Não é dado faltando: é a informação de que não houve cessação, e o
 * índice único parcial da V21 garante que só exista um desses por cliente.
 *
 * <p>Para o cálculo, o fim de um intervalo aberto é hoje no fuso do escritório — ver {@link
 * #effectiveEnd(LocalDate)}. Em UTC, entre 21h e meia-noite "hoje" já era amanhã, e o intervalo
 * rendia um dia a mais do que tinha; é o mesmo motivo pelo qual a idade do cliente é calculada no
 * fuso do escritório.
 */
@Entity
@Table(name = "client_disability_periods")
@EntityListeners(AuditLogListener.class)
public class ClientDisabilityPeriod implements Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    /**
     * O grau, gravado como RÓTULO no banco ("Grave", não "GRAVE").
     *
     * <p>Sem {@code @Enumerated}: quem traduz é {@code DisabilityGradeConverter}, {@code
     * autoApply}, como todos os outros enums do modelo. Não é detalhe de estilo — era um defeito.
     * Com {@code @Enumerated(EnumType.STRING)} o Hibernate grava e lê o NOME da constante, e a
     * aplicação passava porque lia de volta o que ela mesma havia escrito. Qualquer outra origem de
     * dado — seed, correção manual, importação — virava 500 na leitura.
     */
    @Column(name = "grade", nullable = false, length = 20)
    private DisabilityGrade grade;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    /** Nulo = sem data de cessação (deficiência em curso). */
    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Verdadeiro quando não houve cessação. */
    public boolean isOngoing() {
        return endedOn == null;
    }

    /**
     * O fim que vale para o cálculo: a data de cessação, ou {@code today} se o intervalo está em
     * aberto.
     *
     * <p>O "hoje" entra por parâmetro em vez de ser lido aqui dentro para que o cálculo seja
     * verificável sem relógio — e para que todas as linhas de um mesmo cálculo usem o mesmo dia, em
     * vez de cada uma ler o seu.
     */
    public LocalDate effectiveEnd(LocalDate today) {
        return endedOn != null ? endedOn : today;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public DisabilityGrade getGrade() {
        return grade;
    }

    public void setGrade(DisabilityGrade grade) {
        this.grade = grade;
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }

    public void setStartedOn(LocalDate startedOn) {
        this.startedOn = startedOn;
    }

    public LocalDate getEndedOn() {
        return endedOn;
    }

    public void setEndedOn(LocalDate endedOn) {
        this.endedOn = endedOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
