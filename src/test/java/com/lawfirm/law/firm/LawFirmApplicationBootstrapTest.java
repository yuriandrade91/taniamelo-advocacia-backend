package com.lawfirm.law.firm;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@DisplayName("LawFirmApplication: ponto de entrada da aplicação")
class LawFirmApplicationBootstrapTest {

    @Test
    @DisplayName("main delega para o SpringApplication.run com a própria classe")
    void mainDelegatesToSpringApplication() {
        ConfigurableApplicationContext context = Mockito.mock(ConfigurableApplicationContext.class);

        try (MockedStatic<SpringApplication> spring = Mockito.mockStatic(SpringApplication.class)) {
            spring.when(
                            () ->
                                    SpringApplication.run(
                                            eq(LawFirmApplication.class), any(String[].class)))
                    .thenReturn(context);

            LawFirmApplication.main(new String[] {"--server.port=0"});

            spring.verify(
                    () -> SpringApplication.run(eq(LawFirmApplication.class), any(String[].class)));
        }
    }

    @Test
    @DisplayName("a classe é uma aplicação Spring Boot instanciável")
    void isASpringBootApplication() {
        assertTrue(LawFirmApplication.class.isAnnotationPresent(SpringBootApplication.class));
        assertNotNull(new LawFirmApplication());
    }
}
