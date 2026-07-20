package com.lawfirm.law.firm.config;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Situation;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class EnumConverters implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(
                new LabelAwareEnumConverter<>(BenefitType.class, BenefitType::fromLabel));
        registry.addConverter(new LabelAwareEnumConverter<>(Situation.class, Situation::fromLabel));
    }

    /** Generic converter: tries enum constant name first, then falls back to fromLabel(). */
    static class LabelAwareEnumConverter<E extends Enum<E>> implements Converter<String, E> {

        private final Class<E> enumType;
        private final Function<String, E> fromLabel;

        LabelAwareEnumConverter(Class<E> enumType, Function<String, E> fromLabel) {
            this.enumType = enumType;
            this.fromLabel = fromLabel;
        }

        @Override
        public E convert(String source) {
            if (source == null) return null;
            String s = source.trim();
            if (s.isEmpty()) return null;
            try {
                return Enum.valueOf(enumType, s.toUpperCase(Locale.ROOT).replace(' ', '_'));
            } catch (IllegalArgumentException ignored) {
            }
            try {
                return fromLabel.apply(s);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Unknown " + enumType.getSimpleName() + ": " + source);
            }
        }
    }
}
