import { useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { DocumentItem, EmployeeItem } from '../types';
import { formatDate } from '../format';

const TYPES: Array<{ value: string; title: string }> = [
  { value: '', title: 'Все типы' },
  { value: 'ORDER', title: 'Приказы' },
  { value: 'CONTRACT', title: 'Договоры' },
  { value: 'CERTIFICATE', title: 'Справки' }
];

const STATUS_BADGE: Record<string, string> = {
  DRAFT: 'badge-neutral',
  ON_APPROVAL: 'badge-warn',
  APPROVED: 'badge-ok',
  ARCHIVED: 'badge-outline'
};

const NEXT_STATUS: Record<string, { value: string; title: string } | undefined> = {
  DRAFT: { value: 'ON_APPROVAL', title: 'На согласование' },
  ON_APPROVAL: { value: 'APPROVED', title: 'Утвердить' },
  APPROVED: { value: 'ARCHIVED', title: 'В архив' },
  ARCHIVED: { value: 'APPROVED', title: 'Вернуть из архива' }
};

/**
 * Реестр документов: номер, тип, сотрудник, дата и срок хранения.
 *
 * Срок хранения считает сервер по типу документа — интерфейс его только
 * показывает. Договоры хранятся постоянно, поэтому вместо даты стоит «постоянно».
 */
export function DocumentsPage() {
  const [items, setItems] = useState<DocumentItem[]>([]);
  const [employees, setEmployees] = useState<EmployeeItem[]>([]);
  const [type, setType] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    setError(null);
    api.documents(type ? { type } : undefined)
      .then(setItems)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(load, [type]);

  useEffect(() => {
    api.employees({ page: 0, size: 200, search: '', department: '' })
      .then((page) => setEmployees(page.content))
      .catch(() => setEmployees([]));
  }, []);

  const move = (item: DocumentItem) => {
    const next = NEXT_STATUS[item.status];
    if (!next) return;
    setError(null);
    setNotice(null);
    api.setDocumentStatus(item.id, next.value)
      .then((updated) => {
        setNotice(updated.regNumber + ' → ' + updated.statusTitle);
        load();
      })
      .catch((e: Error) => setError(e.message));
  };

  const expired = useMemo(
    () => items.filter((d) => d.retentionUntil && new Date(d.retentionUntil) < new Date()).length,
    [items]
  );

  return (
    <Shell
      active="documents"
      crumbs={['Работа', 'Документы']}
      actions={
        <>
          <button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>
          <a className="btn btn-sm btn-ghost" href="/documents">Старый интерфейс</a>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Работа · документооборот</div>
          <h1 className="t-hero">Документы</h1>
          <div className="t-sm muted mt-8">
            Реестр и архив · срок хранения считается по типу документа
          </div>
        </div>
        <div className="spacer" />
        <div className="row gap-8">
          <span className="badge badge-neutral">{items.length} документов</span>
          {expired > 0 && <span className="badge badge-danger">{expired} просрочено</span>}
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}

      <div className="card card-flush mt-16">
        <div className="table-toolbar">
          <div className="seg">
            {TYPES.map((item) => (
              <button
                key={item.value || 'all'}
                type="button"
                className={type === item.value ? 'active' : ''}
                onClick={() => setType(item.value)}
              >
                {item.title}
              </button>
            ))}
          </div>
          <div className="spacer" />
          <DocumentForm
            employees={employees}
            onCreated={(message) => { setNotice(message); load(); }}
            onError={setError}
          />
        </div>

        <div className="table-wrap">
          <table className="table table-wide">
            <thead>
              <tr>
                <th>Документ</th>
                <th>Тип</th>
                <th>Сотрудник</th>
                <th>Дата</th>
                <th>Хранить до</th>
                <th>Статус</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={7} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>}
              {!loading && items.length === 0 && (
                <tr><td colSpan={7} className="muted" style={{ padding: 24 }}>Документов нет</td></tr>
              )}
              {items.map((item) => {
                const overdue = item.retentionUntil && new Date(item.retentionUntil) < new Date();
                return (
                  <tr key={item.id}>
                    <td>
                      <div className="t-main">{item.title}</div>
                      <div className="sub">№ {item.regNumber}</div>
                    </td>
                    <td className="t-xs">{item.typeTitle}</td>
                    <td className="t-xs">{item.employeeName ?? '—'}</td>
                    <td className="num t-xs">{formatDate(item.documentDate)}</td>
                    <td className={'num t-xs' + (overdue ? '' : ' muted')}>
                      {item.permanentRetention ? 'постоянно' : (item.retentionUntil ? formatDate(item.retentionUntil) : '—')}
                    </td>
                    <td>
                      <span className={'badge ' + (STATUS_BADGE[item.status] ?? 'badge-neutral')}>
                        {item.statusTitle}
                      </span>
                      {overdue && <span className="badge badge-danger" style={{ marginLeft: 6 }}>срок истёк</span>}
                    </td>
                    <td className="t-actions">
                      {NEXT_STATUS[item.status] && (
                        <button className="btn btn-sm btn-outline" type="button" onClick={() => move(item)}>
                          {NEXT_STATUS[item.status]!.title}
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </Shell>
  );
}

/** Форма регистрации документа: номер и срок хранения придут с сервера. */
function DocumentForm(props: {
  employees: EmployeeItem[];
  onCreated: (message: string) => void;
  onError: (message: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [type, setType] = useState('ORDER');
  const [title, setTitle] = useState('');
  const [employeeId, setEmployeeId] = useState('');
  const [date, setDate] = useState('');
  const [busy, setBusy] = useState(false);

  if (!open) {
    return (
      <button className="btn btn-sm btn-primary" type="button" onClick={() => setOpen(true)}>
        Зарегистрировать
      </button>
    );
  }

  return (
    <div className="card card-bd" style={{ minWidth: 320 }}>
      <div className="t-h3 mb-8">Новый документ</div>
      <div style={{ display: 'grid', gap: 8 }}>
        <select className="input" value={type} onChange={(event) => setType(event.target.value)}>
          <option value="ORDER">Приказ</option>
          <option value="CONTRACT">Трудовой договор</option>
          <option value="CERTIFICATE">Справка</option>
        </select>
        <input className="input" placeholder="Название" value={title}
               onChange={(event) => setTitle(event.target.value)} />
        <select className="input" value={employeeId} onChange={(event) => setEmployeeId(event.target.value)}>
          <option value="">Без сотрудника</option>
          {props.employees.map((employee) => (
            <option key={employee.userId} value={employee.userId}>{employee.fullName}</option>
          ))}
        </select>
        <input className="input" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        <div className="row gap-8">
          <button
            className="btn btn-sm btn-primary"
            type="button"
            disabled={busy || !title.trim()}
            onClick={() => {
              setBusy(true);
              api.createDocument({
                type,
                title: title.trim(),
                employeeId: employeeId ? Number(employeeId) : null,
                documentDate: date || null
              })
                .then((created) => {
                  props.onCreated('Зарегистрирован № ' + created.regNumber);
                  setOpen(false);
                  setTitle(''); setDate('');
                })
                .catch((e: Error) => props.onError(e.message))
                .finally(() => setBusy(false));
            }}
          >
            {busy ? 'Регистрация…' : 'Зарегистрировать'}
          </button>
          <button className="btn btn-sm btn-ghost" type="button" onClick={() => setOpen(false)}>Отмена</button>
        </div>
      </div>
    </div>
  );
}
