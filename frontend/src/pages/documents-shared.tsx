import type { DocumentHistoryEntry, DocumentStatus } from '../documents-api';
import { formatShortDateTime } from '../format';

/**
 * Общие детали экранов «Входящие» и «Карточка документа».
 *
 * <p>Статусы, шаги маршрута и таймлайн нужны обоим экранам: если продублировать
 * их, подписи и цвета разойдутся. Историю и допустимые действия присылает
 * сервер — здесь только отрисовка.
 */

export const STATUS_TITLE: Record<DocumentStatus, string> = {
  DRAFT: 'Черновик',
  ON_APPROVAL: 'На согласовании',
  APPROVED: 'Утверждён',
  ARCHIVED: 'В архиве'
};

export const STATUS_BADGE: Record<DocumentStatus, string> = {
  DRAFT: 'badge-neutral',
  ON_APPROVAL: 'badge-warn',
  APPROVED: 'badge-ok',
  ARCHIVED: 'badge-outline'
};

export const STATUS_ORDER: DocumentStatus[] = ['DRAFT', 'ON_APPROVAL', 'APPROVED', 'ARCHIVED'];

/** Короткая метка типа вместо формата файла: вложений сервер не хранит. */
export function docTypeShort(type: string): string {
  switch (type) {
    case 'CONTRACT':
      return 'ДГ';
    case 'CERTIFICATE':
      return 'СП';
    default:
      return 'ПР';
  }
}

function docTypeClass(type: string): string {
  switch (type) {
    case 'CONTRACT':
      return 'pdf';
    case 'CERTIFICATE':
      return 'xls';
    default:
      return 'doc';
  }
}

export function DocIcon({ type }: { type: string }) {
  return <div className={'doc-ico ' + docTypeClass(type)}>{docTypeShort(type)}</div>;
}

/** Подпись кнопки перехода: зависит и от целевого, и от текущего статуса. */
export function actionTitle(from: DocumentStatus, target: DocumentStatus): string {
  switch (target) {
    case 'ON_APPROVAL':
      return 'На согласование';
    case 'APPROVED':
      return from === 'ARCHIVED' ? 'Вернуть из архива' : 'Утвердить';
    case 'ARCHIVED':
      return 'В архив';
    case 'DRAFT':
      return 'Вернуть';
    default:
      return target;
  }
}

function stepDate(status: DocumentStatus, history: DocumentHistoryEntry[]): string {
  const entry = [...history].reverse().find((item) => item.toStatus === status);
  return entry?.createdAt ? formatShortDateTime(entry.createdAt) : '—';
}

/** Горизонтальный маршрут: где документ сейчас и когда прошёл прошлые шаги. */
export function RouteStepper(props: { status: DocumentStatus; history: DocumentHistoryEntry[] }) {
  const { status, history } = props;
  const current = Math.max(0, STATUS_ORDER.indexOf(status));

  return (
    <div className="stepper">
      {STATUS_ORDER.map((step, index) => {
        const state = index < current ? 'is-done' : index === current ? 'is-now' : '';
        return (
          <div className={'step ' + state} key={step}>
            <div className="step-dot">{index < current ? '✓' : index + 1}</div>
            <div className="step-name">{STATUS_TITLE[step]}</div>
            <div className="step-when">{stepDate(step, history)}</div>
          </div>
        );
      })}
    </div>
  );
}

/** Таймлайн истории: последние переходы сверху. */
export function HistoryTimeline(props: { history: DocumentHistoryEntry[] }) {
  const { history } = props;

  if (history.length === 0) {
    return (
      <div className="empty">
        <div className="empty-ico">—</div>
        <div className="t-sm muted">История пуста: документ ещё не проходил шагов маршрута</div>
      </div>
    );
  }

  return (
    <div className="timeline">
      {[...history].reverse().map((entry) => (
        <div className="tl-item is-done" key={entry.id}>
          <div className="tl-dot" />
          <div className="tl-when">{formatShortDateTime(entry.createdAt)}</div>
          <div className="t-sm">
            {entry.actorName}
            {entry.fromStatusTitle
              ? `: «${entry.fromStatusTitle}» → «${entry.toStatusTitle}»`
              : `: документ создан в статусе «${entry.toStatusTitle}»`}
          </div>
          {entry.comment && <div className="t-xs muted">комментарий: «{entry.comment}»</div>}
        </div>
      ))}
    </div>
  );
}

/** Пара «ключ — значение» в реквизитах документа. */
export function Kv({ k, v, mono }: { k: string; v: string; mono?: boolean }) {
  return (
    <div className="kv">
      <span className="k">{k}</span>
      <span className={'v' + (mono ? ' num' : '')}>{v}</span>
    </div>
  );
}
