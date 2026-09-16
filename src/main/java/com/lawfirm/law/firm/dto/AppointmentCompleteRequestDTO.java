package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload da conclusão de um compromisso.
 *
 * <p>Corpo opcional: concluir depois da hora marcada não exige nada. O campo só existe para o caso
 * de concluir ANTES - que é dizer que já aconteceu algo que, pela agenda, ainda vai acontecer.
 * Mesma forma da confirmação de data no passado na criação e edição: a API recusa uma vez com 422,
 * a tela pergunta, e a segunda chamada vem confirmada.
 */
public class AppointmentCompleteRequestDTO {

    @Schema(
            description =
                    "Confirma a conclusão de um compromisso que ainda não começou. "
                            + "Sem isto, concluir antes da hora devolve 422.",
            example = "false")
    private boolean earlyCompletionAcknowledged;

    public boolean isEarlyCompletionAcknowledged() {
        return earlyCompletionAcknowledged;
    }

    public void setEarlyCompletionAcknowledged(boolean earlyCompletionAcknowledged) {
        this.earlyCompletionAcknowledged = earlyCompletionAcknowledged;
    }
}
