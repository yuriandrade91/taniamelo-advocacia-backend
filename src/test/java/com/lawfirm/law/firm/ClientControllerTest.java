package com.lawfirm.law.firm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lawfirm.law.firm.dto.ClientCreateRequestDTO;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientRepository;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class ClientControllerTest {

    @Autowired private WebApplicationContext wac;

    @Autowired private ClientRepository clientRepository;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        // A suite roda contra um banco real: garante o mesmo ponto de partida em toda execucao,
        // senao o cliente criado por um teste faz o seguinte falhar (lista nao vazia / CPF
        // duplicado).
        clientRepository.deleteAll();
    }

    @AfterEach
    public void cleanup() {
        clientRepository.deleteAll();
    }

    /** CPF valido gerado na hora, para o teste nunca esbarrar num CPF ja gravado. */
    private static String randomValidCpf() {
        int[] digits = new int[11];
        for (int i = 0; i < 9; i++) {
            digits[i] = ThreadLocalRandom.current().nextInt(10);
        }
        digits[9] = checkDigit(digits, 9, 10);
        digits[10] = checkDigit(digits, 10, 11);
        StringBuilder cpf = new StringBuilder();
        for (int digit : digits) {
            cpf.append(digit);
        }
        return cpf.toString();
    }

    private static int checkDigit(int[] digits, int length, int startWeight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digits[i] * (startWeight - i);
        }
        int check = 11 - (sum % 11);
        return check >= 10 ? 0 : check;
    }

    @Test
    public void listFilterByBenefitTypeAndSituation() throws Exception {
        mockMvc.perform(
                        get("/api/v1/clients")
                                .param("pageNumber", "1")
                                .param("pageSize", "10")
                                .param("benefitType", "Aposentadoria por idade")
                                .param("situation", "Formulário preenchido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    public void listFilterByInvalidBenefitTypeReturns400WithClearMessage() throws Exception {
        mockMvc.perform(get("/api/v1/clients").param("benefitType", "not-a-benefit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].field").value("benefitType"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_ENUM_VALUE"));
    }

    @Test
    public void listInitiallyEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    public void createClientThenReturnIt() throws Exception {
        ClientCreateRequestDTO dto = new ClientCreateRequestDTO();
        dto.setFullName("Test User");
        dto.setBirthDate(LocalDate.of(1990, 1, 1));
        dto.setCpf(randomValidCpf());
        dto.setRg("MG-12.345.678");
        dto.setEmail("admin@taniamelo.adv.br");
        dto.setMobilePhone("+5511999999999");
        dto.setReferencePhone("+5511988888888");
        dto.setBeneficiaryNumber("BN123");
        dto.setNitPis("NIT123");
        dto.setCtps("CTPS123");
        dto.setCtpsSeries("S1");
        dto.setGender(Gender.MASCULINO);
        dto.setMaritalStatus(MaritalStatus.SOLTEIRO);
        dto.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        dto.setSituation(Situation.FORMULARIO_PREENCHIDO);
        // required fields
        dto.setMotherName("Test Mother");
        dto.setInssPassword("pwd123");
        // Obrigatorios na pratica: as colunas sao NOT NULL e o mapper sobrescreve os defaults
        // da entidade com o null do DTO. Ver ClientMapperTest#entityDefaultsAreOverwrittenByNull.
        dto.setNationality("Brasileira");
        dto.setIsWhatsapp(true);
        dto.setHasDisability(false);

        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(
                        post("/api/v1/clients")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.fullName").value("Test User"))
                .andExpect(jsonPath("$.data.email").value("admin@taniamelo.adv.br"))
                .andExpect(jsonPath("$.data.gender").value("Masculino"))
                .andExpect(jsonPath("$.data.maritalStatus").value("Solteiro(a)"))
                .andExpect(jsonPath("$.data.benefit").value("Aposentadoria por idade"))
                .andExpect(jsonPath("$.data.situation").value("Formulário preenchido"));
    }
}
