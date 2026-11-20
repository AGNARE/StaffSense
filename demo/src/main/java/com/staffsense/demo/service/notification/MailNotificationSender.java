package com.staffsense.demo.service.notification;

import com.staffsense.demo.model.Notification;
import com.staffsense.demo.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Отправка уведомлений на почту.
 *
 * <p>Канал включается свойством {@code staffsense.mail.enabled}: без него
 * уведомления остаются только внутри системы, как было до появления SMTP.
 * Такое разделение нужно, чтобы тесты и локальный запуск не пытались
 * подключиться к внешнему серверу.
 *
 * <p>Ошибка отправки не ломает бизнес-операцию: уведомление уже сохранено
 * в базе, и человек увидит его при входе. Поэтому исключение только
 * логируется — иначе недоступность почты откатывала бы, например, подпись
 * документа.
 */
@Service
@ConditionalOnProperty(name = "staffsense.mail.enabled", havingValue = "true")
public class MailNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String probeTo;

    public MailNotificationSender(JavaMailSender mailSender,
                                  @Value("${staffsense.mail.from:}") String from,
                                  @Value("${staffsense.mail.probe-to:}") String probeTo) {
        this.mailSender = mailSender;
        this.from = from;
        this.probeTo = probeTo;
    }


    /**
     * Проверочное письмо при старте.
     *
     * <p>Нужно, чтобы настройку канала можно было проверить без ожидания
     * события: если {@code staffsense.mail.probe-to} задан, при запуске уходит
     * короткое письмо. В обычной работе свойство пустое.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void probeOnStartup() {
        if (probeTo == null || probeTo.isBlank()) {
            return;
        }
        for (String address : probeTo.split(",")) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(address.trim());
                message.setSubject("[StaffSense] Канал уведомлений подключён");
                message.setText("Проверка SMTP при запуске приложения. Канал работает.");
                mailSender.send(message);
                log.info("Проверочное письмо отправлено на {}", address.trim());
            } catch (Exception e) {
                log.warn("Проверочное письмо не ушло на {}: {}", address.trim(), e.getMessage());
            }
        }
    }

    /** Письмо о событии; адресат — сам сотрудник. */
    public void send(User user, Notification notification) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(user.getEmail());
            message.setSubject("[StaffSense] " + notification.getTitle());
            message.setText(buildBody(user, notification));
            mailSender.send(message);
            log.info("Письмо отправлено: {} → {}", notification.getType(), user.getEmail());
        } catch (Exception e) {
            // Почта — вспомогательный канал: сбой не должен отменять действие.
            log.warn("Не удалось отправить письмо на {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private String buildBody(User user, Notification notification) {
        StringBuilder body = new StringBuilder();
        body.append("Здравствуйте, ").append(user.getFullName()).append("!\n\n");
        body.append(notification.getTitle()).append("\n");
        if (notification.getMessage() != null && !notification.getMessage().isBlank()) {
            body.append(notification.getMessage()).append("\n");
        }
        if (notification.getLink() != null && !notification.getLink().isBlank()) {
            body.append("\nОткрыть в системе: http://localhost:9091/app/")
                    .append(notification.getLink()).append("\n");
        }
        body.append("\n--\nStaffSense · уведомление создано автоматически, отвечать не нужно.");
        return body.toString();
    }
}
