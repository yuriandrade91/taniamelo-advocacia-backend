package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.dto.UpcomingBirthdayDTO;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import com.lawfirm.law.firm.util.OfficeClock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * O card de próximos aniversariantes, contra um banco de verdade.
 *
 * <p>É teste de integração e não de unidade porque tudo que importa aqui acontece no banco. A
 * janela filtra por {@code birth_mmdd}, que é <b>coluna gerada</b> ({@code insertable = false},
 * V22) - a entidade em memória nem sabe o valor dela, quem calcula é o Postgres. E a ordem ("quem
 * faz aniversário primeiro") sai de um {@code CASE WHEN} em SQL que precisa virar o ano
 * corretamente. Um teste com mocks provaria que o Java chama o repositório e não diria nada sobre
 * as duas coisas que podem estar erradas.
 *
 * <p><b>Por que este arquivo existe.</b> A rota nasceu sem teste nenhum: {@code upcomingBirthdays},
 * {@code findBirthdaysInWindow} e {@code BirthdayRow} não apareciam em lugar algum da suíte além do
 * inventário de papéis do contrato de segurança. Quando o celular e o WhatsApp foram acrescentados
 * à projeção, a suíte seguiu com os mesmos 1041 testes verdes sem afirmar uma linha sobre o
 * comportamento novo. Pior: a mudança foi numa <i>constructor expression</i> de JPQL, que o
 * Hibernate só valida no bootstrap - um nome de campo errado ali não quebra teste de unidade,
 * derruba a aplicação na subida.
 *
 * <p>As datas são todas relativas a {@link OfficeClock#today()}, de propósito: um aniversário
 * fixado em data absoluta passaria a cair fora da janela conforme o calendário andasse, e o teste
 * começaria a falhar sozinho meses depois.
 */
@DisplayName("Aniversariantes: janela, ordem e os dados do contato")
class AniversariantesIntegrationTest extends PostgresIntegrationTest {

    private static final int JANELA_DIAS = 30;

    @Autowired private ClientService service;
    @Autowired private ClientRepository repository;

    @AfterEach
    void limpar() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("traz celular e WhatsApp junto, para o parabéns sair do próprio card")
    // Não há caso de celular ausente a cobrir: mobilePhone é @NotBlank e NOT NULL
    // na entidade, logo todo cliente cadastrado tem número.
    void trazDadosDeContato() {
        LocalDate hoje = OfficeClock.today();
        cadastrar("Quem faz hoje", hoje, "529.982.247-25", "(31) 99999-0000", true);

        List<UpcomingBirthdayDTO> card = service.upcomingBirthdays(JANELA_DIAS, 10);

        assertEquals(1, card.size());
        assertEquals("(31) 99999-0000", card.get(0).mobilePhone());
        assertTrue(card.get(0).isWhatsapp());
    }

    @Test
    @DisplayName("quem não tem WhatsApp vem marcado como não - a secretaria liga em vez de mandar")
    void preservaQuemNaoTemWhatsapp() {
        LocalDate hoje = OfficeClock.today();
        cadastrar("Sem WhatsApp", hoje, "529.982.247-25", "(31) 98888-0000", false);

        List<UpcomingBirthdayDTO> card = service.upcomingBirthdays(JANELA_DIAS, 10);

        assertEquals(1, card.size());
        assertFalse(
                card.get(0).isWhatsapp(),
                "false no banco tem de chegar false no card; o tratamento de nulo não pode"
                        + " transformar quem pediu para não receber mensagem em destinatário");
    }

    @Test
    @DisplayName("quem está fora da janela não aparece, e a ordem é a do próximo aniversário")
    void filtraPelaJanelaEOrdena() {
        LocalDate hoje = OfficeClock.today();

        // Cadastrados fora de ordem de propósito: a ordem do resultado tem de vir
        // do ORDER BY da consulta, não da ordem de inserção.
        cadastrar("Em tres dias", hoje.plusDays(3), "111.444.777-35", "(31) 97777-0000", true);
        cadastrar("Hoje", hoje, "529.982.247-25", "(31) 99999-0000", true);
        cadastrar(
                "Daqui a 200 dias", hoje.plusDays(200), "390.533.447-05", "(31) 96666-0000", true);

        List<UpcomingBirthdayDTO> card = service.upcomingBirthdays(JANELA_DIAS, 10);

        assertEquals(
                List.of("Hoje", "Em tres dias"),
                card.stream().map(UpcomingBirthdayDTO::fullName).toList(),
                "esperado só os dois dentro da janela de "
                        + JANELA_DIAS
                        + " dias, do mais próximo ao mais distante");
        assertEquals(0, card.get(0).daysUntil());
        assertEquals(3, card.get(1).daysUntil());
    }

    @Test
    @DisplayName("o limite corta o resultado sem desordenar o que sobrou")
    void respeitaOLimite() {
        LocalDate hoje = OfficeClock.today();
        cadastrar("Hoje", hoje, "529.982.247-25", "(31) 99999-0000", true);
        cadastrar("Em dois dias", hoje.plusDays(2), "111.444.777-35", "(31) 97777-0000", true);

        List<UpcomingBirthdayDTO> card = service.upcomingBirthdays(JANELA_DIAS, 1);

        assertEquals(1, card.size());
        assertEquals(
                "Hoje", card.get(0).fullName(), "o limite corta os mais distantes, não o próximo");
    }

    /**
     * Cria um cliente cujo aniversário cai em {@code quando}, no dia e mês dessa data.
     *
     * <p>O ano de nascimento é fixo em 1980 - ano bissexto, então aceita 29/02 se a data relativa
     * cair ali. Quem importa para a janela é o {@code birth_mmdd} gerado a partir do dia e mês.
     *
     * <p>Monta a entidade à mão em vez de usar {@code TestFixtures.client(...)} de propósito: o
     * fixture atribui o id, e com {@code @GeneratedValue} um id já preenchido faz o Hibernate
     * tratar a entidade como desanexada e emitir {@code UPDATE} em vez de {@code INSERT} - zero
     * linhas afetadas, que chega como {@code ObjectOptimisticLockingFailure}. Quem persiste de
     * verdade deixa o id vir do banco.
     */
    private void cadastrar(
            String nome, LocalDate quando, String cpf, String celular, boolean whatsapp) {
        Client cliente = new Client();
        cliente.setFullName(nome);
        cliente.setBirthDate(LocalDate.of(1980, quando.getMonthValue(), quando.getDayOfMonth()));
        cliente.setCpf(cpf);
        cliente.setMotherName("Mãe do teste");
        cliente.setMobilePhone(celular);
        cliente.setIsWhatsapp(whatsapp);
        cliente.setInssPassword("senha-inss");
        cliente.setGender(Gender.FEMININO);
        cliente.setMaritalStatus(MaritalStatus.SOLTEIRO);
        cliente.setClientType(ClientType.POTENCIAL);
        cliente.setSituation(Situation.FORMULARIO_PREENCHIDO);
        cliente.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        repository.saveAndFlush(cliente);
    }
}
