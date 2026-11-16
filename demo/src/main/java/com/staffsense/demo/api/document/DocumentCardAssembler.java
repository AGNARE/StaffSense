package com.staffsense.demo.api.document;

import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.User;
import com.staffsense.demo.service.document.DocumentAccessPolicy;
import com.staffsense.demo.service.document.DocumentRouteService;
import com.staffsense.demo.service.document.DocumentSignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Сборка карточки документа в одном месте.
 *
 * <p>Карточку отдают два контроллера: реестр ({@code DocumentApiController})
 * и подпись ({@code DocumentSignatureApiController}). Если каждый соберёт её
 * сам, подписи и {@code canSign} появятся только в одном из ответов —
 * классическая рассинхронизация контракта. Поэтому история, допустимые
 * переходы, подписи и признак «можно подписать» считаются здесь.
 */
@Component
@RequiredArgsConstructor
public class DocumentCardAssembler {

    private final DocumentRouteService routeService;
    private final DocumentAccessPolicy accessPolicy;
    private final DocumentSignatureService signatureService;

    public DocumentCardDto of(Document document, User actor) {
        return DocumentCardDto.from(
                document,
                routeService.history(document.getId()),
                accessPolicy.allowedTransitions(actor, document),
                DocumentSignatureDto.Entry.from(signatureService.signatures(document.getId())),
                signatureService.canSign(actor, document));
    }
}
