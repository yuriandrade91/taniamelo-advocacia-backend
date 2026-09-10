package com.lawfirm.law.firm.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato transversal: <b>nenhuma operação destrutiva sem papel exigido</b>.
 *
 * <p>Percorre os controllers por reflexão em vez de listar as rotas de hoje, porque o defeito que
 * importa é o de amanhã: um {@code DELETE} novo, criado por alguém que não conhece a regra,
 * nasceria aberto a qualquer usuário autenticado e ninguém perceberia. Um teste por rota não pega
 * isso; este pega.
 *
 * <p>Se um endpoint destrutivo precisar mesmo ficar aberto, a exceção é declarada abaixo, com o
 * motivo escrito.
 */
@DisplayName("Contrato de segurança: DELETE exige advogado")
class SecuredEndpointsContractTest {

    private static final String PACOTE = "com.lawfirm.law.firm.controller";

    /** Rotas destrutivas deliberadamente sem restrição de papel. Vazio - e que continue assim. */
    private static final List<String> EXCECOES = List.of();

    private static List<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> encontrados = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(PACOTE)) {
            try {
                encontrados.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return encontrados;
    }

    @Test
    @DisplayName("todo @DeleteMapping exige ADMIN ou LAWYER")
    void everyDeleteRequiresLawyer() {
        List<Class<?>> alvos = controllers();
        assertTrue(alvos.size() >= 8, "a varredura não encontrou os controllers: " + alvos);

        List<String> desprotegidos = new ArrayList<>();
        int protegidos = 0;

        for (Class<?> controller : alvos) {
            for (Method metodo : controller.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(metodo, DeleteMapping.class)) {
                    continue;
                }
                String nome = controller.getSimpleName() + "#" + metodo.getName();
                if (EXCECOES.contains(nome)) {
                    continue;
                }
                boolean exige =
                        AnnotatedElementUtils.hasAnnotation(metodo, RequerAdvogado.class)
                                || AnnotatedElementUtils.hasAnnotation(
                                        controller, RequerAdvogado.class);
                if (exige) {
                    protegidos++;
                } else {
                    desprotegidos.add(nome);
                }
            }
        }

        assertTrue(protegidos > 0, "nenhum DELETE encontrado - a varredura quebrou");
        assertTrue(
                desprotegidos.isEmpty(),
                "operação destrutiva aberta a qualquer usuário autenticado: " + desprotegidos);
    }

    @Test
    @DisplayName("restaurar e ler dado sensível também exigem advogado")
    void restoreAndSensitiveReadsRequireLawyer() throws Exception {
        assertRequerAdvogado(
                "com.lawfirm.law.firm.controller.ClientController",
                "restore",
                java.util.UUID.class);
        assertRequerAdvogado(
                "com.lawfirm.law.firm.controller.AppointmentController",
                "restore",
                java.util.UUID.class);
        // Listar quem trabalha no escritório não é da conta de quem só opera.
        assertRequerAdvogado(
                "com.lawfirm.law.firm.controller.UserController", "list", boolean.class);
        // A senha do INSS é leitura, mas é a leitura mais sensível da API.
        assertRequerAdvogado(
                "com.lawfirm.law.firm.controller.ClientController",
                "revealInssPassword",
                java.util.UUID.class);
    }

    private static void assertRequerAdvogado(String classe, String metodo, Class<?>... parametros)
            throws Exception {
        Method alvo = Class.forName(classe).getDeclaredMethod(metodo, parametros);
        assertTrue(
                AnnotatedElementUtils.hasAnnotation(alvo, RequerAdvogado.class),
                classe + "#" + metodo + " deveria exigir ADMIN/LAWYER");
    }
}
