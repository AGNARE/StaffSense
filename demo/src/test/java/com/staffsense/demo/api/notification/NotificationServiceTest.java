package com.staffsense.demo.api.notification;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.NotificationType;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.NotificationRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.notification.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Уведомления: счётчик, прочтение и защита от дублей.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceTest {

    @Autowired
    private NotificationService service;

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Непрочитанное считается, прочтение уменьшает счётчик")
    void unreadCounter() {
        User user = employee("notif.one@test.local");
        service.notify(user, NotificationType.DOCUMENT_TO_SIGN, "Документ на подпись", "№ 1", "#/documents/signature");
        service.notify(user, NotificationType.MEMO_TO_APPROVE, "Записка", "отпуск", "#/memos");

        assertThat(repository.countByUserIdAndReadAtIsNull(user.getId())).isEqualTo(2);

        var first = service.unread(user.getId()).get(0);
        service.markRead(user.getId(), first.getId());

        assertThat(repository.countByUserIdAndReadAtIsNull(user.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Повторное уведомление по тому же объекту не дублируется")
    void noDuplicates() {
        User user = employee("notif.dup@test.local");
        service.notify(user, NotificationType.DOCUMENT_TO_SIGN, "Документ", "№ 2", "#/documents/2");
        service.notify(user, NotificationType.DOCUMENT_TO_SIGN, "Документ", "№ 2", "#/documents/2");

        assertThat(repository.countByUserIdAndReadAtIsNull(user.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Чужое уведомление отметить нельзя")
    void cannotMarkForeign() {
        User owner = employee("notif.owner@test.local");
        User stranger = employee("notif.stranger@test.local");
        service.notify(owner, NotificationType.VACATION_GRANTED, "Отпуск", "28 дней", "#/vacations");

        var notification = service.unread(owner.getId()).get(0);
        assertThat(service.markRead(stranger.getId(), notification.getId())).isFalse();
        assertThat(repository.countByUserIdAndReadAtIsNull(owner.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("«Прочитать всё» обнуляет счётчик")
    void markAllRead() {
        User user = employee("notif.all@test.local");
        service.notify(user, NotificationType.DOCUMENT_SIGNED, "Подписан", null, "#/documents/1");
        service.notify(user, NotificationType.MEMO_TO_APPROVE, "Записка", null, "#/memos");

        int marked = service.markAllRead(user.getId());

        assertThat(marked).isEqualTo(2);
        assertThat(repository.countByUserIdAndReadAtIsNull(user.getId())).isZero();
    }

    private User employee(String email) {
        return userRepository.save(User.builder()
                .fullName("Уведомление " + email)
                .email(email)
                .passwordHash("hash")
                .role(Role.EMPLOYEE)
                .department("Design")
                .position("Specialist")
                .salary(new BigDecimal("50000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2024, 2, 1))
                .active(true)
                .build());
    }
}
