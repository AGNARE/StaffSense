package com.staffsense.demo.api.me;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Кто я — минимальный эндпоинт для клиента.
 *
 * <p>Нужен, чтобы шапка нового интерфейса показывала реального пользователя,
 * а не статичное имя из макета. Отдаёт только безобидные поля: имя, роль, id.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Профиль", description = "Текущий пользователь")
public class MeApiController {

    private final UserRepository userRepository;

    public MeApiController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public MeDto me(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("Пользователь не определён");
        }
        User user = userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));

        return new MeDto(
                user.getId(),
                user.getFullName(),
                user.getRole().name(),
                user.getEmail(),
                user.getDepartment(),
                user.getPosition(),
                RoleScreens.forRole(user.getRole()));
    }

    public record MeDto(
            Long id,
            String fullName,
            String role,
            String email,
            String department,
            String position,
            java.util.List<RoleScreens.Screen> screens
    ) {
    }
}
