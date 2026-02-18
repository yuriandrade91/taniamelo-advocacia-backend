package com.lawfirm.law.firm.config;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Situation;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

@Configuration
public class EnumConverters implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToBenefitTypeConverter());
        registry.addConverter(new StringToSituationConverter());
    }

    static class StringToBenefitTypeConverter implements Converter<String, BenefitType> {
        @Override
        public BenefitType convert(String source) {
            if (source == null) return null;
            String s = source.trim();
            if (s.isEmpty()) return null;
            try {
                String candidate = s.toUpperCase(Locale.ROOT).replace(' ', '_');
                return BenefitType.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
            }
            try {
                return BenefitType.fromLabel(s);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown BenefitType: " + source);
            }
        }
    }

    static class StringToSituationConverter implements Converter<String, Situation> {
        @Override
        public Situation convert(String source) {
            if (source == null) return null;
            String s = source.trim();
            if (s.isEmpty()) return null;
            try {
                String candidate = s.toUpperCase(Locale.ROOT).replace(' ', '_');
                return Situation.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
            }
            try {
                return Situation.fromLabel(s);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown Situation: " + source);
            }
        }
    }
}
