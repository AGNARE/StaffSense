package com.staffsense.demo.service.admin;

import com.staffsense.demo.model.AdminAuditEntry;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AdminAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Журнал действий администратора.
 *
 * <p>Пишет четыре события: создание учётной записи, смена роли, блокировка
 * и сброс пароля. Актор берётся из контекста безопасности, а не из аргументов
 * метода: иначе вызывающий код мог бы «назначить» действие другому.
 */
@Service
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    public static final String USER_CREATED = "USER_CREATED";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String ACTIVE_CHANGED = "ACTIVE_CHANGED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String VACATION_GRANTED = "VACATION_GRANTED";
    public static final String DOCUMENT_SIGNED = "DOCUMENT_SIGNED";
    public static final String DOCUMENT_REJECTED = "DOCUMENT_REJECTED";
    public static final String VACANCY_CREATED = "VACANCY_CREATED";
    public static final String VACANCY_CLOSED = "VACANCY_CLOSED";
    public static final String CANDIDATE_ADDED = "CANDIDATE_ADDED";
    public static final String CANDIDATE_STAGE_CHANGED = "CANDIDATE_STAGE_CHANGED";

    private final AdminAuditRepository repository;

    public AdminAuditService(AdminAuditRepository repository) {
        this.repository = repository;
    }

    /** Записывает действие текущего администратора. */
    @Transactional
    public void record(String action, User target, String details) {
        AdminAuditEntry entry = AdminAuditEntry.builder()
                .actorEmail(currentActorEmail())
                .action(action)
                .targetId(target != null ? target.getId() : null)
                .targetEmail(target != null ? target.getEmail() : null)
                .details(trim(details))
                .createdAt(LocalDateTime.now())
                .build();

        repository.save(entry);
        log.info("Аудит: {} → {} ({})", action, entry.getTargetEmail(), entry.getDetails());
    }

    /** Последние записи, новые сверху. */
    public Page<AdminAuditEntry> recent(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private String currentActorEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private String trim(String details) {
        if (details == null) {
            return null;
        }
        return details.length() > 500 ? details.substring(0, 500) : details;
    }
}
