package com.staffsense.demo.service.hr;

import com.staffsense.demo.dto.hr.HireEmployeeForm;
import com.staffsense.demo.dto.hr.HireResult;
import com.staffsense.demo.model.EmployeeDocuments;
import com.staffsense.demo.model.TundukVerificationLog;
import com.staffsense.demo.model.TundukVerificationResult;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.EmployeeDocumentsRepository;
import com.staffsense.demo.repository.TundukVerificationLogRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.tunduk.InnValidator;
import com.staffsense.demo.service.tunduk.TundukClient;
import com.staffsense.demo.service.tunduk.TundukLookupResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Приём сотрудника на работу с верификацией через «Тундук» (модуль A).
 *
 * <p>Реализует:
 * <ul>
 *   <li>FR-040 — верификация гражданина по ИНН;</li>
 *   <li>FR-041 — создание личного дела;</li>
 *   <li>BR-023 — реактивация, если ИНН уже есть в системе (дубль не создаётся);</li>
 *   <li>BR-024 — обязательное согласие на обработку персональных данных;</li>
 *   <li>BR-026 — журналирование каждого обращения к госсистеме.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class HireEmployeeService {

    private static final Logger log = LoggerFactory.getLogger(HireEmployeeService.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_CAPACITY_HOURS = 40;

    /** BR-024: единый текст отказа — и для верификации, и для приёма. */
    public static final String CONSENT_REQUIRED_MESSAGE =
            "Требуется согласие кандидата на обработку персональных данных";

    private final TundukClient tundukClient;
    private final InnValidator innValidator;
    private final UserRepository userRepository;
    private final EmployeeDocumentsRepository documentsRepository;
    private final TundukVerificationLogRepository verificationLogRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Проверка ИНН и запрос данных гражданина.
     * Каждый вызов фиксируется в журнале (BR-026).
     *
     * @param consentGiven согласие кандидата на обработку персональных данных
     * @throws IllegalArgumentException если согласия нет (BR-024)
     */
    @Transactional
    public TundukLookupResult verify(String inn, boolean consentGiven, User actor) {
        // BR-024: до получения согласия персональные данные не запрашиваются,
        // поэтому обращение к госсистеме не выполняется и журнал не пишется.
        if (!consentGiven) {
            throw new IllegalArgumentException(CONSENT_REQUIRED_MESSAGE);
        }

        String normalized = inn == null ? "" : inn.trim();

        if (!innValidator.isValid(normalized)) {
            writeLog(normalized, actor, TundukVerificationResult.INVALID, null,
                    innValidator.validationMessage());
            return TundukLookupResult.invalid(innValidator.validationMessage());
        }

        TundukLookupResult result = tundukClient.findByInn(normalized);

        User target = documentsRepository.findByInn(normalized)
                .map(EmployeeDocuments::getUser)
                .orElse(null);

        writeLog(normalized, actor, toLogResult(result), target, result.message());
        return result;
    }

    /**
     * Создание или реактивация сотрудника.
     *
     * @throws IllegalArgumentException если ИНН некорректен, нет согласия на ПДн
     *                                  или email уже занят
     */
    @Transactional
    public HireResult hire(HireEmployeeForm form, User actor) {
        String inn = form.getInn() == null ? "" : form.getInn().trim();

        if (!innValidator.isValid(inn)) {
            throw new IllegalArgumentException(innValidator.validationMessage());
        }
        if (!form.isConsentGiven()) {
            throw new IllegalArgumentException(CONSENT_REQUIRED_MESSAGE);
        }

        Optional<EmployeeDocuments> existingDocs = documentsRepository.findByInn(inn);
        User user;
        boolean alreadyExisted = existingDocs.isPresent();
        boolean reactivated = false;
        String generatedPassword = null;

        if (alreadyExisted) {
            // BR-023: сотрудник уже есть в системе — дубль не создаём.
            // Если он был уволен (active=false) — реактивируем.
            user = existingDocs.get().getUser();
            reactivated = !user.isActive();
            applyCorporateFields(user, form, null);
            user = userRepository.save(user);
            log.info("Hire: сотрудник ID={} {} по ИНН {}",
                    user.getId(), reactivated ? "реактивирован" : "обновлён", mask(inn));
        } else {
            userRepository.findByEmail(form.getEmail()).ifPresent(u -> {
                throw new IllegalArgumentException("Email уже используется: " + form.getEmail());
            });
            user = new User();
            applyCorporateFields(user, form, form.getPassword());
            if (user.getPasswordHash() == null) {
                generatedPassword = generatePassword();
                user.setPasswordHash(passwordEncoder.encode(generatedPassword));
            }
            user = userRepository.save(user);
            log.info("Hire: создан сотрудник ID={} по ИНН {}", user.getId(), mask(inn));
        }

        EmployeeDocuments docs = existingDocs.orElseGet(EmployeeDocuments::new);
        docs.setUser(user);
        applyPersonalFields(docs, form, inn);
        documentsRepository.save(docs);

        return new HireResult(user, alreadyExisted, reactivated, generatedPassword);
    }

    /** Личное дело сотрудника (для экрана HR). */
    public Optional<EmployeeDocuments> findDocuments(Long userId) {
        return documentsRepository.findByUserId(userId);
    }

    // ── Внутренние методы ────────────────────────────────────────────────

    private void applyCorporateFields(User user, HireEmployeeForm form, String rawPassword) {
        user.setFullName(form.getFullName());
        user.setEmail(form.getEmail());
        user.setRole(form.getRole());
        user.setDepartment(form.getDepartment());
        user.setPosition(form.getPosition());
        user.setGradeLevel(form.getGradeLevel());
        user.setSalary(form.getSalary());
        user.setWeeklyCapacityHours(form.getWeeklyCapacityHours() != null
                ? form.getWeeklyCapacityHours()
                : DEFAULT_CAPACITY_HOURS);
        user.setHireDate(form.getHireDate());
        user.setActive(true);

        if (form.getManagerId() != null) {
            user.setManager(userRepository.findById(form.getManagerId()).orElse(null));
        }
        if (rawPassword != null && !rawPassword.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
    }

    private void applyPersonalFields(EmployeeDocuments docs, HireEmployeeForm form, String inn) {
        docs.setInn(inn);
        docs.setPin(form.getPin());
        docs.setBirthDate(form.getBirthDate());
        docs.setGender(form.getGender());
        docs.setPassportSeries(form.getPassportSeries());
        docs.setPassportNumber(form.getPassportNumber());
        docs.setPassportIssuedBy(form.getPassportIssuedBy());
        docs.setPassportIssueDate(form.getPassportIssueDate());
        docs.setRegistrationAddress(form.getRegistrationAddress());
        docs.setPhotoUrl(form.getPhotoUrl());
        docs.setTundukVerified(form.isTundukVerified());

        if (form.isTundukVerified()) {
            docs.setVerifiedAt(LocalDateTime.now());
        }
        // BR-024: согласие фиксируется один раз — при первом оформлении.
        if (docs.getConsentGivenAt() == null) {
            docs.setConsentGivenAt(LocalDateTime.now());
        }
    }

    private void writeLog(String inn, User actor, TundukVerificationResult result,
                          User target, String details) {
        try {
            verificationLogRepository.save(TundukVerificationLog.builder()
                    .inn(inn == null || inn.isBlank() ? "—" : inn)
                    .requestedBy(actor)
                    .requestedAt(LocalDateTime.now())
                    .result(result)
                    .targetUser(target)
                    .details(details)
                    .build());
        } catch (Exception e) {
            // Сбой журналирования не должен прерывать приём сотрудника,
            // но обязательно фиксируется в логе приложения.
            log.warn("Не удалось записать журнал обращений к «Тундук»: {}", e.getMessage());
        }
    }

    private TundukVerificationResult toLogResult(TundukLookupResult result) {
        return switch (result.status()) {
            case FOUND -> TundukVerificationResult.FOUND;
            case NOT_FOUND -> TundukVerificationResult.NOT_FOUND;
            case UNAVAILABLE -> TundukVerificationResult.UNAVAILABLE;
            case INVALID -> TundukVerificationResult.INVALID;
        };
    }

    /** BR-025: ИНН не пишется в журнал приложения в открытом виде. */
    private String mask(String inn) {
        if (inn == null || inn.length() < 4) {
            return "****";
        }
        return "**********" + inn.substring(inn.length() - 4);
    }

    private String generatePassword() {
        return "Ss-" + (100000 + RANDOM.nextInt(900000));
    }
}
