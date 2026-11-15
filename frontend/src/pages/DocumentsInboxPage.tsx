import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import type { DocumentItem } from '../types';
import { documentsApi } from '../documents-api';
import type { DocumentCard, DocumentStatus } from '../documents-api';
import { formatDate } from '../format';
import {
  DocIcon, HistoryTimeline, Kv, RouteStepper, STATUS_BADGE, actionTitle
} from './documents-shared';

/**
 * «Входящие и маршруты»: документы, ожидающие решения текущего пользователя.
 *
 * <p>Состав списка считает сервер: HR и ADMIN видят документы «на
 * согласовании», PM — только по своей команде, сотруднику раздел закрыт.
 * Клиент не угадывает права и не фильтрует чужие документы у себя.
 *
 * <p>Справа — выбранный документ: шаг маршрута, реквизиты и действия,
 * которые сервер разрешил именно этому пользователю.
 */
export function DocumentsInboxPage() {
  const [items, setItems] = useState<DocumentItem[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [card, setCard] = useState<DocumentCard | null>(null);
  const [loading, setLoading] = useState(true);
  const [cardLoading, setCardLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadInbox = () => {
    setLoading(true);
    setError(null);
    documentsApi.inbox()
      .then((list) => {
        setItems(list);
        setSelectedId((previous) => {
          if (previous !== null && list.some((item) => item.id === previous)) {
            return previous;
          }
          return list.length > 0 ? list[0].id : null;
        });
      })
      .catch((e: Error) => {
        setError(e.message);
        setItems([]);
        setSelectedId(null);
      })
      .finally(() => setLoading(false));
  };

  useEffect(loadInbox, []);

  // Карточку подтягиваем по выбранной строке: в списке нет истории и действий.
  useEffect(() => {
    if (selectedId === null) {
      setCard(null);
      return;
    }
    setCardLoading(true);
    documentsApi.card(selectedId)
      .then(setCard)
      .catch((e: Error) => setError(e.message))
      .finally(() => setCardLoading(false));
  }, [selectedId]);

  const move = (target: DocumentStatus) => {
    if (!card) {
      return;
    }
    setBusy(true);
    setError(null);
    setNotice(null);
    documentsApi.setStatus(card.id, target, comment)
      .then((updated) => {
        setCard(updated);
        setComment('');
        setNotice('Документ № ' + updated.regNumber + ': ' + updated.statusTitle);
        loadInbox();
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false));
  };

  return (
    <Shell
      active="documents"
      crumbs={['Работа', 'Документы']}
      actions={
        <>
          <button className="btn btn-sm btn-outline" type="button" onClick={loadInbox}>Обновить</button>
          <a className="btn btn-sm btn-ghost" href="#/documents">Весь реестр</a>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Работа · документооборот</div>
          <h1 className="t-hero">Документы</h1>
          <div className="t-sm muted mt-8">
            Входящие и маршруты: документы, которые ждут вашего решения
          </div>
        </div>
        <div className="spacer" />
        <div className="row gap-8">
          <span className="badge badge-warn">{items.length} на подписи</span>
          <span className="badge badge-neutral">{card ? card.statusTitle : 'документ не выбран'}</span>
        </div>
      </div>

      {error && <div className="card card-bd mb-8" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd mb-8" style={{ color: 'var(--ok)' }}>{notice}</div>}

      <div className="split">
        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Требуют меня</h2>
            <span className="badge badge-danger">{items.length}</span>
            <div className="spacer" />
            <button className="btn btn-sm btn-ghost btn-icon" type="button"
                    onClick={loadInbox} title="Обновить список">↻</button>
          </div>

          <div className="doc-list">
            {loading && <div className="t-sm muted" style={{ padding: 18 }}>Загрузка…</div>}
            {!loading && items.length === 0 && (
              <div className="empty">
                <div className="empty-ico">✓</div>
                <div className="t-sm strong">Входящих нет</div>
                <div className="t-xs muted">
                  На согласовании нет документов, доступных вашей роли
                </div>
              </div>
            )}
            {!loading && items.map((item) => (
              <button
                key={item.id}
                type="button"
                className={'doc-row' + (item.id === selectedId ? ' is-on' : '')}
                onClick={() => setSelectedId(item.id)}
                style={{
                  width: '100%', textAlign: 'left', border: 0, background: 'none',
                  font: 'inherit', color: 'inherit', cursor: 'pointer'
                }}
              >
                <DocIcon type={item.type} />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div className="t-sm strong truncate">{item.title}</div>
                  <div className="t-xs muted">
                    {(item.employeeName ?? 'Без сотрудника') + ' · ' + formatDate(item.documentDate)}
                  </div>
                </div>
                <span className={'badge ' + (STATUS_BADGE[item.status as DocumentStatus] ?? 'badge-neutral')}>
                  {item.statusTitle}
                </span>
              </button>
            ))}
          </div>

          <div className="table-foot">
            <span>{items.length} в очереди</span>
            <div className="spacer" />
            <a className="btn btn-sm btn-ghost" href="#/documents">Все документы →</a>
          </div>
        </div>

        <div className="col gap-16">
          {cardLoading && <div className="card card-bd muted t-sm">Загрузка документа…</div>}

          {!cardLoading && !card && (
            <div className="card card-bd">
              <div className="empty">
                <div className="empty-ico">—</div>
                <div className="t-sm strong">Выберите документ</div>
                <div className="t-xs muted">
                  Слева — документы, которые ждут решения. В карточке справа будет маршрут и действия.
                </div>
              </div>
            </div>
          )}

          {!cardLoading && card && (
            <>
              <div className="card">
                <div className="card-hd">
                  <DocIcon type={card.type} />
                  <div style={{ minWidth: 0 }}>
                    <h2 className="t-h3 truncate">{card.title}</h2>
                    <div className="t-xs muted">
                      № {card.regNumber} · {card.typeTitle} · автор {card.authorName ?? '—'}
                    </div>
                  </div>
                  <div className="spacer" />
                  <a className="btn btn-sm btn-outline" href={'#/documents/' + card.id}>
                    Открыть карточку
                  </a>
                </div>

                <div className="card-bd">
                  <RouteStepper status={card.status} history={card.history} />

                  <div className="grid g3 gap-12 mt-16">
                    <Kv k="Сотрудник" v={card.employeeName ?? '—'} />
                    <Kv k="Подразделение" v={card.employeeDepartment ?? '—'} />
                    <Kv k="Должность" v={card.employeePosition ?? '—'} />
                    <Kv k="Дата документа" v={formatDate(card.documentDate)} mono />
                    <Kv k="Хранить до"
                        v={card.permanentRetention ? 'постоянно' : formatDate(card.retentionUntil)} mono />
                    <Kv k="Статус" v={card.statusTitle} />
                  </div>

                  <div className="mt-16">
                    <div className="micro mb-8">Комментарий к переходу (необязательно)</div>
                    <input
                      className="input"
                      placeholder="Например: оклад соответствует грейду"
                      value={comment}
                      onChange={(event) => setComment(event.target.value)}
                    />
                  </div>

                  <div className="row gap-8 mt-16" style={{ flexWrap: 'wrap' }}>
                    {card.allowedTransitions.length === 0 && (
                      <span className="t-sm muted">
                        Действий по маршруту нет: документ уже на финальном шаге или роль только читает.
                      </span>
                    )}
                    {card.allowedTransitions.map((transition) => (
                      <button
                        key={transition.status}
                        type="button"
                        className={'btn ' + (transition.status === 'DRAFT' ? 'btn-outline' : 'btn-primary')}
                        disabled={busy}
                        onClick={() => move(transition.status)}
                      >
                        {actionTitle(card.status, transition.status)}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <div className="card">
                <div className="card-hd">
                  <h2 className="t-h3">Журнал маршрута</h2>
                  <div className="spacer" />
                  <span className="t-xs muted">все переходы фиксируются</span>
                </div>
                <div className="card-bd">
                  <HistoryTimeline history={card.history} />
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </Shell>
  );
}
