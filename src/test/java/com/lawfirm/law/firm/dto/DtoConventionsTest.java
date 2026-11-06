package com.lawfirm.law.firm.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * Sem a anotação, o Jackson 3 ordena as propriedades em ordem alfabética e o id cai no meio do
 * JSON. Record fica de fora porque serializa na ordem em que foi declarado.
 */
@DisplayName("DTOs: resposta com id começa pelo id")
class DtoConventionsTest {

    @Test
    @DisplayName("todo DTO de resposta com campo id declara @JsonPropertyOrder")
    void responseDtosWithIdDeclareTheOrder() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(
                new RegexPatternTypeFilter(java.util.regex.Pattern.compile(".*DTO")));
        Set<BeanDefinition> dtos = scanner.findCandidateComponents("com.lawfirm.law.firm.dto");
        assertFalse(dtos.isEmpty(), "o scan não achou DTO nenhum");

        List<String> missing = new java.util.ArrayList<>();
        for (BeanDefinition definition : dtos) {
            Class<?> dto = Class.forName(definition.getBeanClassName());
            if (isResponseWithId(dto) && !dto.isAnnotationPresent(JsonPropertyOrder.class)) {
                missing.add(dto.getSimpleName());
            }
        }

        assertEquals(List.of(), missing);
    }

    private static boolean isResponseWithId(Class<?> dto) {
        return !dto.isRecord()
                && !dto.getSimpleName().contains("Request")
                && Stream.of(dto.getDeclaredFields())
                        .anyMatch(field -> field.getName().equals("id"));
    }
}
