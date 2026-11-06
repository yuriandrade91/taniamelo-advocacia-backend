package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.RequestStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Quando a mudança de etapa no INSS pede justificativa - a regra que PATCH e PUT compartilham
 * ({@link ClientServiceImpl#justificativaDaTransicao}).
 */
@DisplayName("Etapa no INSS: justificativa obrigatória em exigência, indeferimento e recurso")
class JustificativaDaEtapaTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = RequestStage.class,
            names = {"EXIGENCIA", "INDEFERIMENTO", "RECURSO"})
    @DisplayName("as etapas que existem por causa de um motivo pedem justificativa")
    void etapasComMotivoExigem(RequestStage etapa) {
        assertTrue(etapa.exigeJustificativa());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = RequestStage.class,
            names = {"REQUERIMENTO", "CUMPRIMENTO_DE_EXIGENCIA", "DEFERIMENTO"})
    @DisplayName("as que se explicam sozinhas, não")
    void demaisNaoExigem(RequestStage etapa) {
        assertFalse(etapa.exigeJustificativa());
    }

    @Test
    @DisplayName("entrar numa etapa que exige, sem justificativa ou com ela em branco, é recusado")
    void semJustificativaRecusa() {
        for (String vazia : new String[] {null, "", "   "}) {
            ValidationException error =
                    assertThrows(
                            ValidationException.class,
                            () ->
                                    ClientServiceImpl.justificativaDaTransicao(
                                            RequestStage.REQUERIMENTO,
                                            RequestStage.EXIGENCIA,
                                            vazia));
            assertEquals("requestStageJustification", error.getField());
            assertEquals(ValidationErrorCode.REQUIRED_FIELD, error.getValidationErrorCode());
        }
    }

    @Test
    @DisplayName(
            "vale também para o primeiro registro: cliente sem etapa que entra direto em exigência")
    void semEtapaAnteriorTambemExige() {
        assertThrows(
                ValidationException.class,
                () ->
                        ClientServiceImpl.justificativaDaTransicao(
                                null, RequestStage.EXIGENCIA, null));
    }

    @Test
    @DisplayName("a justificativa volta sem espaços nas pontas")
    void devolveLimpa() {
        assertEquals(
                "Falta de carência.",
                ClientServiceImpl.justificativaDaTransicao(
                        RequestStage.EXIGENCIA,
                        RequestStage.INDEFERIMENTO,
                        "  Falta de carência.  "));
    }

    @Test
    @DisplayName("repetir a etapa gravada não é transição e não pede nada")
    void mesmaEtapaNaoPede() {
        assertNull(
                ClientServiceImpl.justificativaDaTransicao(
                        RequestStage.RECURSO, RequestStage.RECURSO, null));
    }

    @Test
    @DisplayName("nas demais etapas é opcional, e guardada se vier")
    void opcionalNasDemais() {
        assertNull(
                ClientServiceImpl.justificativaDaTransicao(
                        RequestStage.EXIGENCIA, RequestStage.CUMPRIMENTO_DE_EXIGENCIA, null));
        assertEquals(
                "Documentos entregues na agência.",
                ClientServiceImpl.justificativaDaTransicao(
                        RequestStage.EXIGENCIA,
                        RequestStage.CUMPRIMENTO_DE_EXIGENCIA,
                        " Documentos entregues na agência. "));
    }

    @Test
    @DisplayName("voltar a sem requerimento (PUT com etapa nula) não pede justificativa")
    void voltarASemRequerimentoNaoPede() {
        assertNull(ClientServiceImpl.justificativaDaTransicao(RequestStage.RECURSO, null, null));
    }
}
