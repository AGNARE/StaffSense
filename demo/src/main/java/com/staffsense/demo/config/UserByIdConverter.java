package com.staffsense.demo.config;

import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Преобразование идентификатора пользователя из формы в сущность {@link User}.
 *
 * <p>Нужен, чтобы поле формы можно было связать с сущностью напрямую
 * ({@code th:field="*{ownerPm}"}, {@code <option th:value="${pm.id}">})
 * и валидировать его через Bean Validation ({@code @NotNull}).
 *
 * <p>Ранее контроллер принимал отдельный параметр {@code ownerPmId} и делал
 * {@code findById(...).orElse(null)}. При пустом или неизвестном значении
 * {@code ownerPm} оставался {@code null}, {@code save()} падал на ограничении
 * {@code owner_pm_id NOT NULL} из V1, и пользователь получал 409 вместо
 * сообщения у поля (FR-007, ERR-006).
 *
 * <p>Возвращаем {@code null} для пустого/неизвестного значения, а не бросаем
 * исключение: сообщение пользователю формирует Bean Validation ({@code @NotNull})
 * или бизнес-проверка в контроллере, и текст ошибки остаётся на русском.
 */
@Component
public class UserByIdConverter implements Converter<String, User> {

    private static final Logger log = LoggerFactory.getLogger(UserByIdConverter.class);

    private final UserRepository userRepository;

    public UserByIdConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return userRepository.findById(Long.valueOf(source.trim())).orElse(null);
        } catch (NumberFormatException ex) {
            log.warn("Некорректный идентификатор пользователя в форме: {}", source);
            return null;
        }
    }
}
