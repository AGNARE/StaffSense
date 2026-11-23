import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { Memo, MemoStatus, MemoType } from '../types';
import { formatDate } from '../format';

const STATUS_BADGE: Record<MemoStatus, string> = {
  DRAFT: 'badge-neutral',
  PENDING_PM: 'badge-warn',
  PENDING_HR: 'badge-info',
  APPROVED: 'badge-ok',
  REJECTED: 'badge-danger',
  CANCELLED: 'badge-neutral'
};

const TYPE_TITLE: Record<MemoType, string> = {
  VACATION: 'Отпуск',
  SICK_LEAVE: 'Больничный',
  OTHER: 'Прочее'
};

const ACTION_TITLE: Record<string, string> = {
  'approve-pm': 'Виза руководителя',
  'approve-hr': 'Утвердить',
  cancel: 'Отозвать',
  reject: 'Отклонить'
};

/**
 * Служебные записки: список, создание, согласование.
 *
 * Кнопки действий берутся из allowedActions — маршрут согласования
 * (BR-003, BR-029) живёт на сервере, клиент его не повторяет.
 */
export function MemosPage() {
  const [memos, setMemos] = useState<Memo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [onlyPending, setOnlyPending] = useState(false);

  const load = () => {
    setLoading(true);
    setError(null);
    (onlyPending ? api.pendingMemos() : api.memos())
      .then(setMemos)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(load, [onlyPending]);

  const act = (memo: Memo, action: string, reason?: string) => {
    setError(null);
    setNotice(null);
    api.memoAction(memo.id, action as 'approve-pm' | 'approve-hr' | 'cancel' | 'reject', reason)
      // Сообщение строится по ответу сервера: показывать прежний статус было бы враньём.
      .then((updated) => { setNotice('Готово: ' + updated.employeeName + ' — ' + updated.statusTitle); load(); })
      .catch((e: Error) => setError(e.message));
  };

  return (
    <Shell
      active="memos"
      crumbs={['Люди', 'Записки']}
      actions={
        <>
          <button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>
          <MemosForm onCreated={load} onError={setError} onNotice={setNotice} />
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Люди · служебные записки</div>
          <h1 className="t-hero">Записки</h1>
          <div className="t-sm muted mt-8">
            Маршрут: виза руководителя → утверждение HR. Отклонение возможно на любом шаге.
          </div>
        </div>
      </div>

      <div className="card card-flush">
        <div className="table-toolbar">
          <div className="seg">
            <button type="button" className={!onlyPending ? 'active' : ''} onClick={() => setOnlyPending(false)}>
              Все
            </button>
            <button type="button" className={onlyPending ? 'active' : ''} onClick={() => setOnlyPending(true)}>
              Ждут моего решения
            </button>
          </div>
          <div className="spacer" />
          <span className="badge badge-neutral">{memos.length}</span>
        </div>

        <div className="table-wrap">
          <table className="table table-wide">
            <thead>
              <tr>
                <th>Сотрудник</th>
                <th>Тип</th>
                <th>Период</th>
                <th>Дней</th>
                <th>Статус</th>
                <th>Решение</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {error && <tr><td colSpan={7} className="muted" style={{ padding: 24, color: 'var(--danger)' }}>{error}</td></tr>}
              {!error && loading && <tr><td colSpan={7} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>}
              {!error && !loading && memos.length === 0 && (
                <tr><td colSpan={7} className="muted" style={{ padding: 24 }}>Записок нет</td></tr>
              )}
              {memos.map((memo) => (
                <MemoRow key={memo.id} memo={memo} onAction={act} />
              ))}
            </tbody>
          </table>
        </div>

        <div className="table-foot">
          <span className="muted t-xs">
            {notice ?? 'Всего записей: ' + memos.length}
          </span>
        </div>
      </div>
    </Shell>
  );
}

function MemoRow(props: { memo: Memo; onAction: (memo: Memo, action: string, reason?: string) => void }) {
  const { memo, onAction } = props;
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');

  const canReject = memo.allowedActions.includes('reject');

  return (
    <tr>
      <td>
        <div className="t-main">{memo.employeeName}</div>
        <div className="sub">{memo.department ?? '—'}</div>
      </td>
      <td>{memo.typeTitle}</td>
      <td className="muted t-xs">
        {formatDate(memo.startDate)} — {formatDate(memo.endDate)}
      </td>
      <td className="num">{memo.days}</td>
      <td>
        <span className={'badge ' + (STATUS_BADGE[memo.status] ?? 'badge-neutral')}>
          {memo.statusTitle}
        </span>
        {memo.rejectionReason && (
          <div className="t-xs muted mt-4">Причина: {memo.rejectionReason}</div>
        )}
      </td>
      <td className="muted t-xs">
        {memo.pmDecisionBy && <div>PM: {memo.pmDecisionBy}</div>}
        {memo.hrDecisionBy && <div>HR: {memo.hrDecisionBy}</div>}
        {!memo.pmDecisionBy && !memo.hrDecisionBy && '—'}
      </td>
      <td className="t-actions">
        <div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
          {memo.allowedActions
            .filter((action) => action !== 'edit' && action !== 'reject')
            .map((action) => (
              <button
                key={action}
                className="btn btn-sm btn-outline"
                type="button"
                onClick={() => onAction(memo, action)}
              >
                {ACTION_TITLE[action] ?? action}
              </button>
            ))}
          {canReject && !rejecting && (
            <button className="btn btn-sm btn-ghost" type="button" onClick={() => setRejecting(true)}>
              Отклонить
            </button>
          )}
        </div>

        {rejecting && (
          <div style={{ marginTop: 6 }}>
            <input
              className="input input-sm"
              placeholder="Причина отклонения"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
            <div className="row mt-4" style={{ gap: 6 }}>
              <button
                className="btn btn-sm btn-danger"
                type="button"
                disabled={!reason.trim()}
                onClick={() => { onAction(memo, 'reject', reason.trim()); setRejecting(false); setReason(''); }}
              >
                Отклонить
              </button>
              <button className="btn btn-sm btn-ghost" type="button" onClick={() => setRejecting(false)}>
                Отмена
              </button>
            </div>
          </div>
        )}
      </td>
    </tr>
  );
}

/** Форма создания записки: тип, период, причина. */
function MemosForm(props: {
  onCreated: () => void;
  onError: (message: string) => void;
  onNotice: (message: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [type, setType] = useState<MemoType>('VACATION');
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const cardRef = useRef<HTMLDivElement>(null);

  // Escape закрывает панель так же, как кнопка «Отмена»: форма — модальное
  // окно, и клавиатурный выход из него ожидаем. Фокус на карточке нужен,
  // чтобы клавиша приходила в диалог, а не оставалась на кнопке в шапке.
  useEffect(() => {
    if (!open) {
      return;
    }
    cardRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open]);

  /**
   * Даты в input[type=date] приходят строками ISO (YYYY-MM-DD), поэтому
   * сравнение строк совпадает с хронологическим и не зависит от часового пояса.
   */
  const datesMissing = !start || !end;
  const periodReversed = Boolean(start && end && end < start);
  const canSubmit = !busy && !datesMissing && !periodReversed;

  if (!open) {
    return (
      <button className="btn btn-sm btn-primary" type="button" onClick={() => setOpen(true)}>
        Новая записка
      </button>
    );
  }

  // Портал в body: верхняя панель имеет backdrop-filter и создаёт
  // containing block, из-за которого position: fixed считался бы от неё,
  // а не от окна — панель снова уезжала бы вверх.
  return createPortal(
    <div
      onClick={() => setOpen(false)}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(6,14,26,.46)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 120,
        padding: 20
      }}
    >
      <div
        ref={cardRef}
        className="card card-bd"
        role="dialog"
        aria-modal="true"
        aria-label="Новая записка"
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
        style={{ minWidth: 320, maxWidth: 520, width: '100%', maxHeight: '86vh', overflowY: 'auto', outline: 'none' }}
      >
        <div className="t-h3 mb-8">Новая записка</div>
        <div style={{ display: 'grid', gap: 8 }}>
          <select className="input" value={type} onChange={(event) => setType(event.target.value as MemoType)}>
            {Object.entries(TYPE_TITLE).map(([value, title]) => (
              <option key={value} value={value}>{title}</option>
            ))}
          </select>
          <div className="row gap-8">
            <input
              className="input"
              type="date"
              value={start}
              aria-label="Дата начала"
              onChange={(event) => setStart(event.target.value)}
            />
            <input
              className="input"
              type="date"
              value={end}
              aria-label="Дата окончания"
              onChange={(event) => setEnd(event.target.value)}
            />
          </div>
          {/* Перевёрнутый период — ошибка формы, а не повод узнать о ней от сервера. */}
          {periodReversed && (
            <div className="t-xs" style={{ color: 'var(--danger)' }}>
              Дата окончания раньше даты начала
            </div>
          )}
          {!periodReversed && datesMissing && (start || end) && (
            <div className="t-xs muted">Укажите обе даты: начало и окончание</div>
          )}
          <input
            className="input"
            placeholder="Причина (необязательно)"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
          <div className="row gap-8">
            <button
              className="btn btn-sm btn-primary"
              type="button"
              disabled={!canSubmit}
              title={periodReversed
                ? 'Дата окончания раньше даты начала'
                : (datesMissing ? 'Укажите период записки' : undefined)}
              onClick={() => {
                if (!canSubmit) {
                  return;
                }
                setBusy(true);
                api.createMemo({ type, startDate: start, endDate: end, reason: reason || undefined })
                  .then(() => {
                    props.onNotice('Записка создана и отправлена на согласование');
                    props.onCreated();
                    setOpen(false);
                    setStart(''); setEnd(''); setReason('');
                  })
                  .catch((e: Error) => props.onError(e.message))
                  .finally(() => setBusy(false));
              }}
            >
              {busy ? 'Отправка…' : 'Отправить'}
            </button>
            <button className="btn btn-sm btn-ghost" type="button" onClick={() => setOpen(false)}>
              Отмена
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
}
