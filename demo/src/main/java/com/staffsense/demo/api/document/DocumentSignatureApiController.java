package com.staffsense.demo.api.document;

import com.staffsense.demo.exception.DocumentTransitionException;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.SignatureDecision;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.document.DocumentRouteService;
import com.staffsense.demo.service.document.DocumentSignatureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Подпись документа и очередь на подпись.
 *
 * <p>Отдельный контроллер, а не расширение {@link DocumentApiController}:
 * реестр и маршрут — это чтение и смена статуса, а здесь решение по подписи
 * со своим SLA. Префикс путей тот же, существующие ручки не меняются.
 *
 * <p>Права: ADMIN и HR подписывают любые документы, PM — только документы
 * своей команды, EMPLOYEE и EXEC получают 403. Проверки уровня данных живут
 * в {@code DocumentSignatureService} — контроллер не дублирует их.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Подписи документов", description = "Подпись, отклонение и очередь на подпись")
public class DocumentSignatureApiController {

    private final DocumentSignatureService signatureService;
    private final DocumentRouteService routeService;
    private final DocumentCardAssembler cardAssembler;
    private final UserRepository userRepository;

    public DocumentSignatureApiController(DocumentSignatureService signatureService,
                                          DocumentRouteService routeService,
                                          DocumentCardAssembler cardAssembler,
                                          UserRepository userRepository) {
        this.signatureService = signatureService;
        this.routeService = routeService;
        this.cardAssembler = cardAssembler;
        this.userRepository = userRepository;
    }

    /**
     * Подписать или отклонить документ.
     *
     * <p>Ответ — карточка документа: клиенту не нужно вторым запросом
     * выяснять, что стало со статусом и подписями. Отклонение возвращает
     * документ в черновики, подпись утверждает — оба перехода пишутся в
     * историю маршрута.
     */
    @PostMapping("/{id}/sign")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM')")
    public DocumentCardDto sign(@PathVariable Long id,
                                @Valid @RequestBody SignRequest request,
                                Authentication auth) {
        User actor = currentUser(auth);
        signatureService.sign(id, actor, request.decision(), request.comment());
        return cardAssembler.of(routeService.requireVisible(id, actor), actor);
    }

    /**
     * Очередь на подпись с просрочкой и остатком часов.
     *
     * <p>Состав очереди считает сервер: PM видит только документы своей
     * команды, HR и ADMIN — все. Просрочка приходит флагом, остаток — числом
     * часов (отрицательный означает, что срок уже прошёл): клиент не
     * сравнивает {@code dueAt} с локальными часами.
     */
    @GetMapping("/signature-queue")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM')")
    public List<DocumentSignatureDto.QueueItem> queue(Authentication auth) {
        User actor = currentUser(auth);
        LocalDateTime now = LocalDateTime.now();
        return signatureService.queue(actor).stream()
                .map(document -> DocumentSignatureDto.QueueItem.from(
                        document, now, signatureService.canSign(actor, document)))
                .toList();
    }

    /**
     * Конфликт решения по подписи — 409, а не 500.
     *
     * <p>Локальный обработчик, как и в {@code DocumentApiController}: глобальный
     * совет намеренно не трогаем, чтобы поведение остальных ручек не менялось.
     */
    @ExceptionHandler(DocumentTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleSignatureConflict(DocumentTransitionException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", HttpStatus.CONFLICT.getReasonPhrase());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("Пользователь не определён");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }

    /** Тело запроса на подпись: решение обязательно, комментарий — нет. */
    public record SignRequest(
            @NotNull(message = "Решение обязательно: APPROVED или REJECTED")
            SignatureDecision decision,
            @Size(max = 500, message = "Комментарий слишком длинный") String comment
    ) {
    }
}
