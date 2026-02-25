package com.lawfirm.law.firm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class ClientControllerTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
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
        ClientDetailsDTO dto = new ClientDetailsDTO();
        dto.setFullName("Test User");
        dto.setBirthDate(LocalDate.of(1990,1,1));
        dto.setCpf("000.000.000-00");
        dto.setRg("MG-12.345.678");
        dto.setEmail("test.user@example.com");
        dto.setMobilePhone("+5511999999999");
        dto.setReferencePhone("+5511988888888");
    dto.setBeneficiaryNumber("BN123");
        dto.setNitPis("NIT123");
        dto.setCtps("CTPS123");
        dto.setCtpsSeries("S1");
        dto.setCreatedBy(1);
        dto.setGender(Gender.Masculino);
        dto.setMaritalStatus(MaritalStatus.SOLTEIRO);
        dto.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        dto.setSituation(Situation.FORMULARIO_PREENCHIDO);
        // required fields
        dto.setMotherName("Test Mother");
        dto.setInssPassword("pwd123");

        String body = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.fullName").value("Test User"))
                .andExpect(jsonPath("$.data.email").value("test.user@example.com"))
                .andExpect(jsonPath("$.data.gender").value("Masculino"))
                .andExpect(jsonPath("$.data.maritalStatus").value("Solteiro(a)"))
                .andExpect(jsonPath("$.data.benefit").value("Aposentadoria por idade"))
                .andExpect(jsonPath("$.data.situation").value("formulário preenchido"));
    }
}
