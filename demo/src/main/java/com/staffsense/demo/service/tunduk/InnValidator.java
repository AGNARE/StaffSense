package com.staffsense.demo.service.tunduk;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Проверка формата ИНН гражданина Кыргызской Республики.
 *
 * <p>По решению заказчика (вопрос Q-015) проверяется <b>формат</b> — ровно
 * 14 цифр. Алгоритм контрольной суммы не применяется: в ТЗ он помечен как
 * «требует уточнения», а для эмуляции госсистемы достаточно формата.
 */
@Component
public class InnValidator {

    private static final Pattern INN_PATTERN = Pattern.compile("\\d{14}");

    public boolean isValid(String inn) {
        return inn != null && INN_PATTERN.matcher(inn.trim()).matches();
    }

    /** Сообщение для пользователя при некорректном ИНН (ERR-013). */
    public String validationMessage() {
        return "Введите корректный 14-значный ИНН (только цифры)";
    }
}
