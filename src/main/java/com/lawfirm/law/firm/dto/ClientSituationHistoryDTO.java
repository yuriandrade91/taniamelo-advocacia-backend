package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma entrada da linha do tempo de situação do cliente.
 *
 * <p>Traz a situação anterior junto da nova porque a linha do tempo precisa dizer "de X para Y".
 * Com só a nova, a tela consegue afirmar "passou para X" e nada mais - e reconstruir o "de" a
 * partir da entrada anterior da lista é errado na primeira página de uma listagem paginada, onde a
 * entrada anterior pode nem estar carregada.
 *
 * <p>{@code previousSituation} é nulo na primeira entrada (situação inicial, no cadastro).
 *
 * <p>{@code retrocesso} diz se aquela mudança andou para trás no funil. O funil tem ordem (ver
 * {@code Situation}) e voltar é permitido - gente erra a linha da lista, e recusar a correção só
 * empurraria o conserto para fora do sistema. Mas voltar também não é rotina, e quem lê a linha do
 * tempo tem de conseguir ver a diferença sem decorar a ordem das seis etapas.
 */
@JsonPropertyOrder({"id"})
public class ClientSituationHistoryDTO {
    private UUID id;
    private String previousSituation;
    private String currentSituation;
    private Instant changedAt;
    private UUID changedByUserId;

    /** Derivado das duas situações; nunca gravado. Falso na primeira entrada. */
    private boolean retrocesso;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPreviousSituation() {
        return previousSituation;
    }

    public void setPreviousSituation(String previousSituation) {
        this.previousSituation = previousSituation;
    }

    public String getCurrentSituation() {
        return currentSituation;
    }

    public void setCurrentSituation(String currentSituation) {
        this.currentSituation = currentSituation;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public UUID getChangedByUserId() {
        return changedByUserId;
    }

    public void setChangedByUserId(UUID changedByUserId) {
        this.changedByUserId = changedByUserId;
    }

    public boolean isRetrocesso() {
        return retrocesso;
    }

    public void setRetrocesso(boolean retrocesso) {
        this.retrocesso = retrocesso;
    }
}
