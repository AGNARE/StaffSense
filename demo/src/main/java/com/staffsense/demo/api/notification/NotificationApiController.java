package com.staffsense.demo.api.notification;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Notification;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Уведомления текущего пользователя.
 *
 * <p>Свои и только свои: идентификатор берётся из аутентификации, а не из
 * запроса. Чужое уведомление отметить нельзя — сервис проверяет владельца.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Уведомления", description = "Внутрисистемные уведомления сотрудника")
public class NotificationApiController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationApiController(NotificationService notificationService,
                                     UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    /** Список с счётчиком непрочитанных. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public NotificationPage list(Authentication auth,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        User user = currentUser(auth);
        var result = notificationService.page(user.getId(),
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));

        List<Item> content = result.getContent().stream().map(Item::from).toList();
        return new NotificationPage(content, notificationService.unreadCount(user.getId()),
                result.getTotalElements());
    }

    /** Отметить прочитанным. */
    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ReadResult read(Authentication auth, @PathVariable Long id) {
        User user = currentUser(auth);
        boolean changed = notificationService.markRead(user.getId(), id);
        if (!changed) {
            throw new ResourceNotFoundException("Уведомление не найдено: " + id);
        }
        return new ReadResult(notificationService.unreadCount(user.getId()));
    }

    /** Отметить всё прочитанным. */
    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ReadResult readAll(Authentication auth) {
        User user = currentUser(auth);
        notificationService.markAllRead(user.getId());
        return new ReadResult(notificationService.unreadCount(user.getId()));
    }

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }

    /** Одно уведомление. */
    public record Item(Long id, String type, String typeTitle, String title,
                       String message, String link, LocalDateTime readAt, LocalDateTime createdAt) {
        static Item from(Notification notification) {
            return new Item(notification.getId(), notification.getType().name(),
                    notification.getType().getTitle(), notification.getTitle(),
                    notification.getMessage(), notification.getLink(),
                    notification.getReadAt(), notification.getCreatedAt());
        }
    }

    /** Страница уведомлений и счётчик непрочитанных. */
    public record NotificationPage(List<Item> content, long unread, long total) {
    }

    /** Сколько осталось непрочитанных. */
    public record ReadResult(long unread) {
    }
}
