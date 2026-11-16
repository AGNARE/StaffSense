import { useCallback, useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';
import { Modal } from '../components/Modal';
import { formatShortDateTime } from '../format';
import { signatureApi } from '../signature-api';
import type { SignatureDecision, SignatureQueueItem } from '../signature-api';
import { Kv } from './documents-shared';

/**
 * «Очередь на подпись»: что ждёт решения, сколько осталось и что уже горит.
 *
 * <p>Состав очереди и признак просрочки считает сервер: PM видит документы
 * своей команды, HR и ADMIN — все; сотруднику раздел закрыт. Клиент не
 * сравнивает {@code dueAt} с локальными часами — остаток приходит числом,
 * поэтому сбитые часы или другой часовой пояс не превращают свежий документ
 * в просроченный.
 *
 * <p>Решение принимается в модальном окне: подпись комментарий не требует,
 * отклонение — требует, иначе в истории останется «отклонено» без причины.
 */

/** Русские формы часов: 1 час, 2 часа, 5 часов, 21 час, 72 часа. */
function hoursLabel(hours: number): string {
  const value = Math.abs(hours);
  const mod10 = value % 10;
  const mod100 = value % 100;
  if (mod10 === 1 && mod100 !== 11) {
    return value + ' час';
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return value + ' часа';
  }
  return value + ' часов';
}

/** Подпись к колонке «осталось / просрочено»: текст и цвет бейджа. */
function remaining(item: SignatureQueueItem): { text: string; badge: string } {
  if (item.dueAt === null || item.remainingHours === null) {
    return { text: 'без срока', badge: 'badge-neutral' };
  }
  if (item.overdue) {
    return { text: 'просрочено на ' + hoursLabel(item.remainingHours), badge: 'badge-danger' };
  }
  if (item.remainingHours <= 0) {
    return { text: 'меньше часа', badge: 'badge-warn' };
  }
  return {
    text: 'осталось ' + hoursLabel(item.remainingHours),
    badge: item.remainingHours <= 4 ? 'badge-warn' : 'badge-ok'
  };
}

/** Почему кнопки недоступны — одной фразой, без технических кодов. */
function cannotSignReason(item: SignatureQueueItem): string {
  if (item.status !== 'ON_APPROVAL') {
    return 'Документ уже не на согласовании';
  }
  return 'Вы автор документа, уже подписали его или он относится к чужой команде';
}

export function SignatureQueuePage() {
  const [items, setItems] = useState<SignatureQueueItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [draft, setDraft] = useState<{ item: SignatureQueueItem; decision: SignatureDecision } | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    signatureApi.queue()
      .then(setItems)
      .catch((e: Error) => {
        setError(e.message);
        setItems([]);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const overdueCount = useMemo(() => items.filter((item) => item.overdue).length, [items]);

  return (
    <Shell
      active="documentsSignature"
      crumbs={['Работа', 'Документы']}
      actions={
        <>
          <button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>
          <a className="btn btn-sm btn-ghost" href="#/documents/inbox">Входящие</a>
          <a className="btn btn-sm btn-ghost" href="#/documents">Весь реестр</a>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Работа · документооборот</div>
          <h1 className="t-hero">Очередь на подпись</h1>
          <div className="t-sm muted mt-8">
            Срок подписи считается от перехода в «На согласовании»; просрочка — сигнал, а не автодействие
          </div>
        </div>
        <div className="spacer" />
        <div className="row gap-8">
          <span className="badge badge-warn">{items.length} в очереди</span>
          {overdueCount > 0 && <span className="badge badge-danger">{overdueCount} просрочено</span>}
        </div>
      </div>

      {error && <div className="card card-bd mb-8" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd mb-8" style={{ color: 'var(--ok)' }}>{notice}</div>}

      <div className="card card-flush">
        <div className="table-toolbar">
          <h2 className="t-h3">Ждут решения</h2>
          <div className="spacer" />
          <span className="t-xs muted">просроченные — первыми</span>
        </div>

        <div className="table-wrap">
          <table className="table table-wide">
            <thead>
              <tr>
                <th>Документ</th>
                <th>Срок</th>
                <th>Осталось / просрочено</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={4} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>
              )}
              {!loading && items.length === 0 && (
                <tr>
                  <td colSpan={4} style={{ padding: 0 }}>
                    <div className="empty">
                      <div className="empty-ico">✓</div>
                      <div className="t-sm strong">Очередь пуста</div>
                      <div className="t-xs muted">
                        На согласовании нет документов, доступных вашей роли
                      </div>
                    </div>
                  </td>
                </tr>
              )}

              {!loading && items.map((item) => {
                const left = remaining(item);
                return (
                  <tr key={item.id}>
                    <td>
                      <div className="t-main">{item.title}</div>
                      <div className="sub">
                        № {item.regNumber} · {item.typeTitle}
                        {item.employeeName ? ' · ' + item.employeeName : ''}
                        {item.authorName ? ' · автор ' + item.authorName : ''}
                      </div>
                    </td>
                    <td className="num t-xs">
                      {item.dueAt ? formatShortDateTime(item.dueAt) : '—'}
                    </td>
                    <td>
                      <span className={'badge ' + left.badge}>{left.text}</span>
                    </td>
                    <td className="t-actions">
                      <div className="row" style={{ gap: 6, justifyContent: 'flex-end' }}>
                        <button
                          className="btn btn-sm btn-primary"
                          type="button"
                          disabled={!item.canSign}
                          title={item.canSign ? 'Подписать документ' : cannotSignReason(item)}
                          onClick={() => setDraft({ item, decision: 'APPROVED' })}
                        >
                          Подписать
                        </button>
                        <button
                          className="btn btn-sm btn-danger"
                          type="button"
                          disabled={!item.canSign}
                          title={item.canSign ? 'Отклонить документ с комментарием' : cannotSignReason(item)}
                          onClick={() => setDraft({ item, decision: 'REJECTED' })}
                        >
                          Отклонить
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <div className="table-foot">
          <span>{items.length} документов · {overdueCount} просрочено</span>
          <div className="spacer" />
          <a className="btn btn-sm btn-ghost" href="#/documents">Все документы →</a>
        </div>
      </div>

      {draft && (
        <DecisionModal
          item={draft.item}
          decision={draft.decision}
          onClose={() => setDraft(null)}
          onDone={(message) => {
            setDraft(null);
            setError(null);
            setNotice(message);
            load();
          }}
          onError={(message) => {
            setDraft(null);
            setNotice(null);
            setError(message);
          }}
        />
      )}
    </Shell>
  );
}

/** Модальное окно решения: подтверждение подписи или причина отклонения. */
function DecisionModal(props: {
  item: SignatureQueueItem;
  decision: SignatureDecision;
  onClose: () => void;
  onDone: (message: string) => void;
  onError: (message: string) => void;
}) {
  const { item, decision } = props;
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);

  const approve = decision === 'APPROVED';
  const left = remaining(item);
  // Отклонение без причины бессмысленно: в истории останется «отклонено» и всё.
  const commentRequired = !approve && comment.trim().length < 3;
  const title = approve ? 'Подписать документ' : 'Отклонить документ';

  return (
    <Modal
      title={title}
      subtitle={`№ ${item.regNumber} · ${item.typeTitle}`}
      onClose={props.onClose}
      footer={
        <>
          <button
            className={'btn btn-sm ' + (approve ? 'btn-primary' : 'btn-danger')}
            type="button"
            disabled={busy || commentRequired}
            title={commentRequired ? 'Опишите причину: без неё отклонение невозможно' : undefined}
            onClick={() => {
              setBusy(true);
              signatureApi.sign(item.id, decision, comment)
                .then((card) => props.onDone(
                  'Документ № ' + card.regNumber + ': ' + card.statusTitle
                ))
                .catch((e: Error) => props.onError(e.message))
                .finally(() => setBusy(false));
            }}
          >
            {busy ? 'Отправка…' : approve ? 'Подписать' : 'Отклонить'}
          </button>
          <button className="btn btn-sm btn-ghost" type="button" onClick={props.onClose}>Отмена</button>
        </>
      }
    >
      <div className="t-main">{item.title}</div>
      <div className="t-xs muted mt-4">
        {item.employeeName ?? 'Без сотрудника'}
        {item.authorName ? ' · автор ' + item.authorName : ''}
      </div>

      <div className="grid g2 gap-12 mt-16">
        <Kv k="Срок подписи" v={item.dueAt ? formatShortDateTime(item.dueAt) : 'без срока'} mono />
        <Kv k="Осталось" v={left.text} />
      </div>

      <div className="mt-16">
        <div className="micro mb-8">
          {approve ? 'Комментарий к подписи (необязательно)' : 'Причина отклонения (обязательно)'}
        </div>
        <textarea
          className="textarea"
          style={{ width: '100%' }}
          rows={4}
          value={comment}
          placeholder={approve ? 'Например: оклад соответствует грейду' : 'Что нужно исправить'}
          onChange={(event) => setComment(event.target.value)}
        />
      </div>

      <div className="t-xs muted mt-8">
        {approve
          ? 'Документ станет «Утверждён». Подпись, время и комментарий останутся в карточке.'
          : 'Документ вернётся в черновик. Причина останется в истории маршрута.'}
      </div>
    </Modal>
  );
}
