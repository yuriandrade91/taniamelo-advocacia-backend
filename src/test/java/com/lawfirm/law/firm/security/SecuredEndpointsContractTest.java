package com.lawfirm.law.firm.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
 *
 * <p>A varredura por {@code @DeleteMapping} sozinha dava falsa segurança: ela garante o VERBO, não
 * o ESTRAGO. {@code PATCH /appointments/{id}/cancel} e {@code PUT /clients/{id}} mudam o mundo e
 * ficavam de fora. Por isso existe também o {@link #everyEndpointIsClassified()}, que trava o mapa
 * inteiro: rota nova de qualquer verbo quebra o teste até alguém dizer, por escrito, que papel ela
 * exige.
 */
@DisplayName("Contrato de segurança: DELETE exige papel")
class SecuredEndpointsContractTest {

    private static final String PACOTE = "com.lawfirm.law.firm.controller";

    /** Rotas destrutivas deliberadamente sem restrição de papel. Vazio - e que continue assim. */
    private static final List<String> EXCECOES = List.of();

    private static final String ABERTO = "-";
    private static final String ADVOGADO = "ADVOGADO";
    private static final String ADMIN = "ADMIN";

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
    @DisplayName("todo @DeleteMapping exige ADMIN ou LAWYER (ou só ADMIN, que é mais estrito)")
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
                // RequerAdmin também vale: é um subconjunto de ADMIN/LAWYER, não uma
                // brecha. O financeiro usa ele no nível da classe.
                boolean exige =
                        temAnotacao(metodo, controller, RequerAdvogado.class)
                                || temAnotacao(metodo, controller, RequerAdmin.class);
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

    /**
     * Inventário completo: rota -> papel exigido.
     *
     * <p>{@code "-"} significa "qualquer usuário autenticado", e é uma decisão, não um
     * esquecimento: STAFF é a secretaria, e cadastrar, editar, marcar, cancelar e concluir são o
     * trabalho dela. O corte está em excluir, restaurar, ler a senha do INSS, ver os usuários do
     * escritório e mexer no financeiro.
     *
     * <p>Mexer aqui é declarar uma decisão de segurança. Se uma linha mudar num PR, é isso que se
     * revisa.
     */
    private static final Map<String, String> PAPEL_EXIGIDO =
            Map.ofEntries(
                    // ── Autenticação: público por SecurityConfig, não por papel ──
                    Map.entry("AuthController#login", ABERTO),
                    Map.entry("AuthController#logout", ABERTO),
                    Map.entry("AuthController#refresh", ABERTO),
                    Map.entry("TenantController#resolve", ABERTO),
                    Map.entry("TenantController#current", ABERTO),

                    // ── Clientes: STAFF opera ──
                    Map.entry("ClientController#list", ABERTO),
                    Map.entry("ClientController#getById", ABERTO),
                    Map.entry("ClientController#create", ABERTO),
                    Map.entry("ClientController#update", ABERTO),
                    Map.entry("ClientController#patch", ABERTO),
                    Map.entry("ClientController#situationHistory", ABERTO),
                    Map.entry("ClientController#delete", ADVOGADO),
                    Map.entry("ClientController#restore", ADVOGADO),
                    Map.entry("ClientController#revealInssPassword", ADVOGADO),
                    Map.entry("ClientPersonalDataController#get", ABERTO),
                    Map.entry("ClientPersonalDataController#update", ABERTO),
                    Map.entry("ClientProfessionalDataController#get", ABERTO),
                    Map.entry("ClientProfessionalDataController#update", ABERTO),

                    // ── Endereços, entrevistas e arquivos: STAFF opera, advogado destrói ──
                    Map.entry("ClientAddressController#list", ABERTO),
                    Map.entry("ClientAddressController#get", ABERTO),
                    Map.entry("ClientAddressController#create", ABERTO),
                    Map.entry("ClientAddressController#createBatch", ABERTO),
                    Map.entry("ClientAddressController#update", ABERTO),
                    Map.entry("ClientAddressController#delete", ADVOGADO),
                    Map.entry("ClientInterviewController#list", ABERTO),
                    Map.entry("ClientInterviewController#get", ABERTO),
                    Map.entry("ClientInterviewController#create", ABERTO),
                    Map.entry("ClientInterviewController#update", ABERTO),
                    Map.entry("ClientInterviewController#delete", ADVOGADO),
                    Map.entry("ClientFileController#listDocuments", ABERTO),
                    Map.entry("ClientFileController#listSimulations", ABERTO),
                    Map.entry("ClientFileController#getDocument", ABERTO),
                    Map.entry("ClientFileController#getSimulation", ABERTO),
                    Map.entry("ClientFileController#download", ABERTO),
                    Map.entry("ClientFileController#uploadDocuments", ABERTO),
                    Map.entry("ClientFileController#uploadSimulations", ABERTO),
                    Map.entry("ClientFileController#updateDocument", ABERTO),
                    Map.entry("ClientFileController#updateSimulation", ABERTO),
                    Map.entry("ClientFileController#markPrincipal", ABERTO),
                    Map.entry("ClientFileController#delete", ADVOGADO),

                    // ── Agenda: STAFF marca, cancela e conclui; advogado exclui e restaura ──
                    Map.entry("AppointmentController#list", ABERTO),
                    Map.entry("AppointmentController#get", ABERTO),
                    Map.entry("AppointmentController#summary", ABERTO),
                    Map.entry("AppointmentController#conflicts", ABERTO),
                    Map.entry("AppointmentController#history", ABERTO),
                    Map.entry("AppointmentController#create", ABERTO),
                    Map.entry("AppointmentController#update", ABERTO),
                    Map.entry("AppointmentController#cancel", ABERTO),
                    Map.entry("AppointmentController#complete", ABERTO),
                    Map.entry("AppointmentController#delete", ADVOGADO),
                    Map.entry("AppointmentController#restore", ADVOGADO),

                    // ── Financeiro: ADMIN, inclusive para ler ──
                    Map.entry("ClientPaymentController#list", ADMIN),
                    Map.entry("ClientPaymentController#get", ADMIN),
                    Map.entry("ClientPaymentController#create", ADMIN),
                    Map.entry("ClientPaymentController#update", ADMIN),
                    Map.entry("ClientPaymentController#delete", ADMIN),

                    // ── Usuários do escritório ──
                    Map.entry("UserController#list", ADVOGADO));

    @Test
    @DisplayName("toda rota está classificada, e com o papel que se decidiu")
    void everyEndpointIsClassified() {
        Map<String, String> atual = new TreeMap<>();
        for (Class<?> controller : controllers()) {
            for (Method metodo : controller.getDeclaredMethods()) {
                if (verboDe(metodo) == null) {
                    continue;
                }
                atual.put(
                        controller.getSimpleName() + "#" + metodo.getName(),
                        papelDe(metodo, controller));
            }
        }

        List<String> naoClassificadas =
                atual.keySet().stream().filter(r -> !PAPEL_EXIGIDO.containsKey(r)).toList();
        assertTrue(
                naoClassificadas.isEmpty(),
                "rota nova sem decisão de papel - declare em PAPEL_EXIGIDO: " + naoClassificadas);

        List<String> sumiram =
                PAPEL_EXIGIDO.keySet().stream()
                        .filter(r -> !atual.containsKey(r))
                        .sorted()
                        .toList();
        assertTrue(
                sumiram.isEmpty(), "rota declarada que não existe mais - limpe o mapa: " + sumiram);

        List<String> divergentes =
                atual.entrySet().stream()
                        .filter(e -> !PAPEL_EXIGIDO.get(e.getKey()).equals(e.getValue()))
                        .map(
                                e ->
                                        e.getKey()
                                                + ": esperado "
                                                + PAPEL_EXIGIDO.get(e.getKey())
                                                + ", está "
                                                + e.getValue())
                        .toList();
        assertTrue(
                divergentes.isEmpty(), "o papel exigido mudou sem a decisão mudar: " + divergentes);
    }

    private static String verboDe(Method metodo) {
        if (AnnotatedElementUtils.hasAnnotation(metodo, GetMapping.class)) return "GET";
        if (AnnotatedElementUtils.hasAnnotation(metodo, PostMapping.class)) return "POST";
        if (AnnotatedElementUtils.hasAnnotation(metodo, PutMapping.class)) return "PUT";
        if (AnnotatedElementUtils.hasAnnotation(metodo, PatchMapping.class)) return "PATCH";
        if (AnnotatedElementUtils.hasAnnotation(metodo, DeleteMapping.class)) return "DELETE";
        return null;
    }

    private static String papelDe(Method metodo, Class<?> controller) {
        if (temAnotacao(metodo, controller, RequerAdmin.class)) return ADMIN;
        if (temAnotacao(metodo, controller, RequerAdvogado.class)) return ADVOGADO;
        return ABERTO;
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

    private static boolean temAnotacao(
            Method metodo,
            Class<?> controller,
            Class<? extends java.lang.annotation.Annotation> a) {
        return AnnotatedElementUtils.hasAnnotation(metodo, a)
                || AnnotatedElementUtils.hasAnnotation(controller, a);
    }

    @Test
    @DisplayName("o financeiro do cliente é ADMIN no recurso inteiro")
    void paymentsRequireAdmin() throws Exception {
        // Na classe, e não método a método: rota nova no financeiro nasce fechada.
        Class<?> controller =
                Class.forName("com.lawfirm.law.firm.controller.ClientPaymentController");
        assertTrue(
                AnnotatedElementUtils.hasAnnotation(controller, RequerAdmin.class),
                "ClientPaymentController deveria exigir ADMIN na classe");
    }

    private static void assertRequerAdvogado(String classe, String metodo, Class<?>... parametros)
            throws Exception {
        Method alvo = Class.forName(classe).getDeclaredMethod(metodo, parametros);
        assertTrue(
                AnnotatedElementUtils.hasAnnotation(alvo, RequerAdvogado.class),
                classe + "#" + metodo + " deveria exigir ADMIN/LAWYER");
    }
}
