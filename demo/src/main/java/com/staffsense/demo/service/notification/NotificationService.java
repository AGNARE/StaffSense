package com.staffsense.demo.service.notification;

import com.staffsense.demo.model.Notification;
import com.staffsense.demo.model.NotificationType;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Уведомления внутри системы.
 *
 * <p>Заменяют отсутствующую рассылку: событие ждёт человека в интерфейсе,
 * а не теряется, если он зашёл позже. Повторные уведомления одного типа
 * по одному объекту не создаются — иначе ежедневная проверка просрочек
 * засыпала бы список.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    /** Почтовый канал; null, если свойство staffsense.mail.enabled не задано. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MailNotificationSender mailSender;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Создаёт уведомление, если такого же ещё нет. */
    @Transactional
    public void notify(User user, NotificationType type, String title, String message, String link) {
        if (user == null) {
            return;
        }
        boolean exists = repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(user.getId()).stream()
                .anyMatch(n -> n.getType() == type && java.util.Objects.equals(n.getLink(), link));
        if (exists) {
            return;
        }

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .createdAt(LocalDateTime.now())
                .build();

        Notification saved = repository.save(notification);
        if (mailSender != null) {
            mailSender.send(user, saved);
        }
        log.debug("Уведомление {} для {}", type, user.getEmail());
    }

    /** То же для нескольких получателей. */
    @Transactional
    public void notifyAll(Collection<User> users, NotificationType type, String title,
                          String message, String link) {
        users.forEach(user -> notify(user, type, title, message, link));
    }

    public Page<Notification> page(Long userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public List<Notification> unread(Long userId) {
        return repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    /** Отметить прочитанным; чужое уведомление не трогаем. */
    @Transactional
    public boolean markRead(Long userId, Long notificationId) {
        return repository.findById(notificationId)
                .filter(n -> n.getUser() != null && userId.equals(n.getUser().getId()))
                .map(n -> {
                    if (n.getReadAt() == null) {
                        n.setReadAt(LocalDateTime.now());
                        repository.save(n);
                    }
                    return true;
                })
                .orElse(false);
    }

    /** Отметить всё прочитанным. */
    @Transactional
    public int markAllRead(Long userId) {
        List<Notification> unread = repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> n.setReadAt(now));
        repository.saveAll(unread);
        return unread.size();
    }
}
