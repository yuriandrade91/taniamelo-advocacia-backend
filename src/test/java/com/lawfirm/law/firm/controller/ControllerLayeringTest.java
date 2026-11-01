package com.lawfirm.law.firm.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

/**
 * A regra {@code controller → service → repository} de {@code docs/ARQUITETURA.md}, em forma de
 * teste: escrita só no documento, ela já tinha sido quebrada em três controllers.
 */
@DisplayName("Camadas: controller não fala direto com o repositório")
class ControllerLayeringTest {

    @Test
    @DisplayName("nenhum controller guarda repositório como dependência")
    void controllersDoNotDependOnRepositories() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<BeanDefinition> controllers =
                scanner.findCandidateComponents("com.lawfirm.law.firm.controller");
        assertFalse(controllers.isEmpty(), "o scan não achou controller nenhum");

        List<String> violations =
                controllers.stream().flatMap(ControllerLayeringTest::repositoryFields).toList();

        assertEquals(List.of(), violations);
    }

    private static Stream<String> repositoryFields(BeanDefinition definition) {
        try {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            return Stream.of(controller.getDeclaredFields())
                    .filter(ControllerLayeringTest::isRepository)
                    .map(field -> controller.getSimpleName() + "." + field.getName());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean isRepository(Field field) {
        return field.getType().getPackageName().endsWith(".repository");
    }
}
