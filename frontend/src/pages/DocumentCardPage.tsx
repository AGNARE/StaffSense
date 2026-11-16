import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { documentsApi } from '../documents-api';
import type { DocumentCard, DocumentStatus } from '../documents-api';
import { formatDate, formatShortDateTime } from '../format';
import { STATUS_BADGE, STATUS_TITLE, DocIcon, RouteStepper, HistoryTimeline, Kv } from './documents-shared';

/**
 * Карточка документа: реквизиты, маршрут, история и доступные действия.
 *
 * Действия приходят с сервера (`allowedTransitions`) — клиент не решает,
 * что можно сделать дальше: правило маршрута живёт в домене.
 */
export function DocumentCardPage(props: { documentId: number }) {
  const [card, setCard] = useState<DocumentCard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);

  const load = () => {
    setError(null);
    documentsApi.card(props.documentId)
      .then(setCard)
      .catch((e: Error) => setError(e.message));
  };

  useEffect(load, [props.documentId]);

  const move = (target: DocumentStatus) => {
    if (!card || busy) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    documentsApi.setStatus(card.id, target, comment.trim() || undefined)
      .then(() => {
        setNotice('Статус изменён: ' + STATUS_TITLE[target]);
        setComment('');
        load();
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false));
  };

  return (
    <Shell
      active="documents"
      crumbs={['Работа', 'Документы']}
      actions={<a className="btn btn-sm btn-ghost" href="#/documents">← К реестру</a>}
    >
      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}

      {!card && !error && (
        <div className="card card-bd muted">Загрузка…</div>
      )}

      {card && (
        <>
          <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
            <div>
              <div className="micro mb-8">Документ · № {card.regNumber}</div>
              <h1 className="t-hero" style={{ fontSize: 30 }}>{card.title}</h1>
              <div className="t-sm muted mt-8">
                {card.typeTitle}
                {card.employeeName ? ' · ' + card.employeeName : ''}
                {card.employeeDepartment ? ' · ' + card.employeeDepartment : ''}
              </div>
            </div>
            <div className="spacer" />
            <span className={'badge ' + (STATUS_BADGE[card.status] ?? 'badge-neutral')}>
              {STATUS_TITLE[card.status] ?? card.statusTitle}
            </span>
          </div>

          <div className="grid g-2-1" style={{ alignItems: 'start' }}>
            <div className="grid" style={{ gap: 14 }}>
              <div className="card card-flush">
                <div className="card-hd">
                  <h2 className="t-h3">Маршрут</h2>
                  <div className="spacer" />
                  <DocIcon type={card.type} />
                </div>
                <div className="card-bd">
                  <RouteStepper status={card.status} history={card.history} />
                </div>
              </div>

              <div className="card card-flush">
                <div className="card-hd"><h2 className="t-h3">Реквизиты</h2></div>
                <div className="card-bd">
                  <Kv k="Регистрационный номер" v={card.regNumber} mono />
                  <Kv k="Тип" v={card.typeTitle} />
                  <Kv k="Сотрудник" v={card.employeeName ?? '—'} />
                  <Kv k="Автор" v={card.authorName ?? '—'} />
                  <Kv k="Дата документа" v={formatDate(card.documentDate)} />
                  <Kv
                    k="Хранить до"
                    v={card.permanentRetention ? 'постоянно' : (card.retentionUntil ? formatDate(card.retentionUntil) : '—')}
                  />
                  <Kv k="Создан" v={card.createdAt ? formatShortDateTime(card.createdAt) : '—'} />
                  <Kv k="Обновлён" v={card.updatedAt ? formatShortDateTime(card.updatedAt) : '—'} />
                  {card.summary && <Kv k="Описание" v={card.summary} />}
                </div>
              </div>
            </div>

            <div className="grid" style={{ gap: 14 }}>
              <div className="card card-flush">
                <div className="card-hd"><h2 className="t-h3">Действия</h2></div>
                <div className="card-bd">
                  {card.allowedTransitions.length === 0 && (
                    <div className="t-sm muted">
                      Для вашей роли действий на этом шаге нет
                    </div>
                  )}
                  {card.allowedTransitions.length > 0 && (
                    <>
                      <label className="field">
                        <span className="field-label">Комментарий</span>
                        <input
                          className="input"
                          placeholder="необязательно"
                          value={comment}
                          onChange={(event) => setComment(event.target.value)}
                        />
                      </label>
                      <div className="col gap-8 mt-8">
                        {card.allowedTransitions.map((transition) => (
                          <button
                            key={transition.status}
                            className="btn btn-sm btn-primary"
                            type="button"
                            disabled={busy}
                            onClick={() => move(transition.status)}
                          >
                            {transition.title}
                          </button>
                        ))}
                      </div>
                    </>
                  )}
                </div>
              </div>

              <div className="card card-flush">
                <div className="card-hd"><h2 className="t-h3">История</h2></div>
                <div className="card-bd">
                  <HistoryTimeline history={card.history} />
                </div>
              </div>
            </div>
          </div>
        </>
      )}
    </Shell>
  );
}
