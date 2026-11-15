import { useCallback, useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';
import { formatDate, formatNumber } from '../format';
import { TIMESHEET_SCREEN, hrApi } from '../hr-api';
import type { AbsenceType, Timesheet, TimesheetEmployee } from '../hr-api';

/** Бейджи статусов служебной записки — те же, что в разделе «Записки». */
const STATUS_BADGE: Record<string, string> = {
  DRAFT: 'badge-neutral',
  PENDING_PM: 'badge-warn',
  PENDING_HR: 'badge-warn',
  APPROVED: 'badge-ok',
  REJECTED: 'badge-danger',
  CANCELLED: 'badge-outline'
};

const ABSENCE_BADGE: Record<AbsenceType, string> = {
  VACATION: 'badge-info',
  SICK_LEAVE: 'badge-danger',
  OTHER: 'badge-ai'
};

const SOURCE_TITLE: Record<string, string> = {
  MEMO: 'записка',
  TIMESHEET: 'табель'
};

function todayIso(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

function monthStartIso(): string {
  return todayIso().slice(0, 8) + '01';
}

interface EntryForm {
  userId: string;
  date: string;
  hours: string;
  overtime: string;
  remote: boolean;
  sickLeave: boolean;
  vacation: boolean;
}

const EMPTY_FORM: EntryForm = {
  userId: '',
  date: todayIso(),
  hours: '8',
  overtime: '0',
  remote: false,
  sickLeave: false,
  vacation: false
};

/**
 * «Отсутствия и табель»: табель по сотрудникам, отсутствия и переработки.
 *
 * Данные приходят из `GET /api/v1/hr/timesheet`. Агрегаты (часы, переработки,
 * дни отсутствий) считает `TimesheetService` — тот же сервис, что и HTML-отчёт,
 * поэтому экран не повторяет формулы BR-027/028/030. Клиент отвечает только за
 * показ: если записи за период нет, строка помечена `hasData = false` и вместо
 * часов стоит прочерк, а не ноль.
 *
 * Отметка часов уходит в `POST /api/v1/hr/timesheet` (HR/ADMIN). Закрытый
 * период (BR-028) сервер возвращает как 409, и форма показывает текст правила,
 * а не «ошибка 500».
 */
export function TimesheetPage() {
  const [from, setFrom] = useState(monthStartIso());
  const [to, setTo] = useState(todayIso());
  const [department, setDepartment] = useState('');
  const [data, setData] = useState<Timesheet | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [form, setForm] = useState<EntryForm>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    hrApi.timesheet({ from, to, department: department || undefined })
      .then(setData)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [from, to, department]);

  useEffect(load, [load]);

  const totals = data?.totals;
  const rows = data?.rows ?? [];
  const employees = useMemo(() => rows.map((row) => ({ id: row.userId, name: row.fullName })), [rows]);

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setNotice(null);

    const userId = Number(form.userId);
    if (!userId) {
      setError('Выберите сотрудника');
      return;
    }
    if (!form.date) {
      setError('Укажите дату');
      return;
    }

    setSaving(true);
    hrApi.saveEntry({
      userId,
      date: form.date,
      hours: form.hours === '' ? null : Number(form.hours),
      remote: form.remote,
      sickLeave: form.sickLeave,
      vacation: form.vacation,
      overtimeHours: form.overtime === '' ? null : Number(form.overtime)
    })
      .then((entry) => {
        setNotice(
          `Записано: ${formatDate(entry.date)} · ${entry.hours != null ? formatNumber(entry.hours, 1) : '—'} ч`
        );
        load();
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setSaving(false));
  };

  return (
    <Shell
      active={TIMESHEET_SCREEN}
      crumbs={['Процессы', 'Отсутствия и табель']}
      actions={
        <>
          <button className="btn btn-sm btn-outline" type="button" onClick={load} disabled={loading}>
            Обновить
          </button>
          <a className="btn btn-sm btn-ghost" href="#/hr-desk">Пульт HR</a>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Модуль 1 · процесс</div>
          <h1 className="t-hero">Отсутствия и табель</h1>
          <div className="t-sm muted mt-8">
            {loading && !data
              ? 'Загрузка…'
              : data
                ? `${data.periodLabel} · ${data.totals.employees} сотрудников · ${data.totals.employeesWithData} с записями`
                : '—'}
          </div>
        </div>
        <div className="spacer" />
        <div className="row gap-8">
          {data?.closed && <span className="badge badge-warn">период закрыт</span>}
          {department && <span className="badge badge-neutral">{department}</span>}
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}

      <div className="card card-bd mt-16">
        <div className="row gap-12" style={{ flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <label className="field">
            <span className="field-label">С</span>
            <input
              className="input input-sm"
              type="date"
              value={from}
              max={to}
              onChange={(event) => setFrom(event.target.value)}
            />
          </label>
          <label className="field">
            <span className="field-label">По</span>
            <input
              className="input input-sm"
              type="date"
              value={to}
              min={from}
              onChange={(event) => setTo(event.target.value)}
            />
          </label>
          <label className="field">
            <span className="field-label">Подразделение</span>
            <select
              className="select input-sm"
              value={department}
              onChange={(event) => setDepartment(event.target.value)}
            >
              <option value="">Все подразделения</option>
              {(data?.departments ?? []).map((name) => (
                <option key={name} value={name}>{name}</option>
              ))}
            </select>
          </label>
          <div className="spacer" />
          <span className="t-xs muted">
            период не длиннее 366 дней · агрегаты считает сервер
          </span>
        </div>
      </div>

      <div className="grid g4 mt-16">
        <Metric
          label="Часов в периоде"
          value={totals ? formatNumber(totals.totalHours, 1) : '—'}
          unit="ч"
          foot={totals ? `по ${totals.employeesWithData} сотрудникам с записями` : 'нет данных'}
        />
        <Metric
          label="Переработки"
          value={totals ? formatNumber(totals.overtimeHours, 1) : '—'}
          unit="ч"
          color={totals && totals.overtimeHours > 0 ? 'var(--warn)' : undefined}
          foot="сумма сверх нормы по записям табеля"
        />
        <Metric
          label="Дней отсутствия"
          value={totals ? totals.sickDays + totals.vacationDays : '—'}
          unit="дн."
          foot={totals ? `отпуск ${totals.vacationDays} · больничный ${totals.sickDays}` : 'нет данных'}
        />
        <Metric
          label="Заполнено дней"
          value={totals ? totals.workingDays : '—'}
          unit="дн."
          foot={totals ? `удалённо ${totals.remoteDays} · ${data?.closed ? 'период закрыт' : 'период открыт'}` : 'нет данных'}
        />
      </div>

      <div className="card card-flush mt-16">
        <div className="card-hd">
          <h2 className="t-h3">Табель по сотрудникам</h2>
          <div className="spacer" />
          <span className="t-xs muted">
            {data ? `${formatDate(data.from)} — ${formatDate(data.to)}` : '—'}
          </span>
        </div>
        <div className="table-wrap">
          <table className="table table-wide table-click">
            <thead>
              <tr>
                <th>Сотрудник</th>
                <th>Дней</th>
                <th>Часы</th>
                <th>Переработки</th>
                <th>Удалённо</th>
                <th>Отпуск</th>
                <th>Больничный</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {loading && !data ? (
                <tr><td colSpan={8} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>
              ) : rows.length === 0 ? (
                <tr>
                  <td colSpan={8} className="muted" style={{ padding: 24 }}>
                    Сотрудников по фильтру нет.
                  </td>
                </tr>
              ) : (
                rows.map((row) => (
                  <EmployeeRows
                    key={row.userId}
                    row={row}
                    expanded={expanded === row.userId}
                    onToggle={() => setExpanded(expanded === row.userId ? null : row.userId)}
                  />
                ))
              )}
            </tbody>
          </table>
        </div>
        <div className="table-foot">
          <span>
            {totals
              ? `${totals.employees} сотрудников · ${totals.employeesWithData} с записями`
              : '—'}
          </span>
          <div className="spacer" />
          <span className="muted">часы вносит HR или руководитель (BR-027), закрытый период не правится (BR-028)</span>
        </div>
      </div>

      <div className="grid g-2-1 mt-16 g-start">
        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Заявки на отсутствие</h2>
            <div className="spacer" />
            <span className="badge badge-neutral">{data?.absenceRequests.length ?? '—'}</span>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Сотрудник</th>
                  <th>Тип</th>
                  <th>Период</th>
                  <th>Дней</th>
                  <th>Статус</th>
                </tr>
              </thead>
              <tbody>
                {data && data.absenceRequests.length > 0 ? (
                  data.absenceRequests.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <span className="t-main">{item.fullName}</span>
                        <span className="sub">{item.department ?? '—'}</span>
                      </td>
                      <td className="t-xs">{item.typeTitle}</td>
                      <td className="num t-xs">
                        {formatDate(item.startDate)} — {formatDate(item.endDate)}
                      </td>
                      <td className="num">{item.days}</td>
                      <td>
                        <span className={`badge ${STATUS_BADGE[item.status] ?? 'badge-neutral'}`}>
                          {item.statusTitle}
                        </span>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={5} className="muted" style={{ padding: 24 }}>
                      Заявок, пересекающихся с периодом, нет.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <span>маршрут согласования: PM → HR</span>
            <div className="spacer" />
            <a className="btn btn-sm btn-ghost" href="#/memos">Все записки</a>
          </div>
        </div>

        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Отсутствия в периоде</h2>
            <div className="spacer" />
            <span className="badge badge-neutral">{data?.absences.length ?? '—'}</span>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Сотрудник</th>
                  <th>Тип</th>
                  <th>Период</th>
                  <th>Дней</th>
                </tr>
              </thead>
              <tbody>
                {data && data.absences.length > 0 ? (
                  data.absences.map((item) => (
                    <tr key={`${item.userId}-${item.from}-${item.type}`}>
                      <td>
                        <span className="t-main">{item.fullName}</span>
                        <span className="sub">{SOURCE_TITLE[item.source] ?? item.source}</span>
                      </td>
                      <td>
                        <span className={`badge ${ABSENCE_BADGE[item.type]}`}>{item.typeTitle}</span>
                      </td>
                      <td className="num t-xs">
                        {formatDate(item.from)} — {formatDate(item.to)}
                      </td>
                      <td className="num">{item.days}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={4} className="muted" style={{ padding: 24 }}>
                      Отсутствий за период нет.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div className="card mt-16">
        <div className="card-hd">
          <h2 className="t-h3">Отметка часов</h2>
          <div className="spacer" />
          <span className="t-xs muted">HR/ADMIN · правка закрытого месяца запрещена</span>
        </div>
        <div className="card-bd">
          {data?.closed ? (
            <div className="callout callout-warn">
              <div className="t-sm">
                Период закрыт: часы за него не правятся (BR-028). Откройте другой период
                или снимите закрытие в табеле руководителя.
              </div>
            </div>
          ) : (
            <form onSubmit={submit}>
              <div className="row gap-12" style={{ flexWrap: 'wrap', alignItems: 'flex-end' }}>
                <label className="field" style={{ minWidth: 220 }}>
                  <span className="field-label">Сотрудник <span className="field-req">*</span></span>
                  <select
                    className="select input-sm"
                    value={form.userId}
                    onChange={(event) => setForm({ ...form, userId: event.target.value })}
                  >
                    <option value="">— выберите —</option>
                    {employees.map((employee) => (
                      <option key={employee.id} value={employee.id}>{employee.name}</option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span className="field-label">Дата <span className="field-req">*</span></span>
                  <input
                    className="input input-sm"
                    type="date"
                    value={form.date}
                    onChange={(event) => setForm({ ...form, date: event.target.value })}
                  />
                </label>
                <label className="field" style={{ width: 110 }}>
                  <span className="field-label">Часы</span>
                  <input
                    className="input input-sm"
                    type="number"
                    min="0"
                    max="24"
                    step="0.5"
                    value={form.hours}
                    onChange={(event) => setForm({ ...form, hours: event.target.value })}
                  />
                </label>
                <label className="field" style={{ width: 130 }}>
                  <span className="field-label">Переработка</span>
                  <input
                    className="input input-sm"
                    type="number"
                    min="0"
                    max="24"
                    step="0.5"
                    value={form.overtime}
                    onChange={(event) => setForm({ ...form, overtime: event.target.value })}
                  />
                </label>
                <div className="row gap-12" style={{ paddingBottom: 6 }}>
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={form.remote}
                      onChange={(event) => setForm({ ...form, remote: event.target.checked })}
                    />
                    Удалённо
                  </label>
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={form.sickLeave}
                      onChange={(event) => setForm({ ...form, sickLeave: event.target.checked })}
                    />
                    Больничный
                  </label>
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={form.vacation}
                      onChange={(event) => setForm({ ...form, vacation: event.target.checked })}
                    />
                    Отпуск
                  </label>
                </div>
                <div className="spacer" />
                <button className="btn btn-primary" type="submit" disabled={saving || employees.length === 0}>
                  {saving ? 'Сохранение…' : 'Записать'}
                </button>
              </div>
              <div className="t-xs muted mt-16">
                День отсутствия может быть без часов: пустое поле — «день без работы»,
                а не ноль. Часы и переработка — от 0 до 24 в сутки.
              </div>
            </form>
          )}
        </div>
      </div>
    </Shell>
  );
}

/** Строка табеля; по клику раскрывается детализация по дням. */
function EmployeeRows(props: { row: TimesheetEmployee; expanded: boolean; onToggle: () => void }) {
  const { row, expanded, onToggle } = props;
  const dash = !row.hasData;

  return (
    <>
      <tr onClick={onToggle}>
        <td>
          <span className="t-main">{row.fullName}</span>
          <span className="sub">
            {row.department ?? '—'}{row.position ? ' · ' + row.position : ''}
          </span>
        </td>
        <td className="num">{dash ? '—' : row.workingDays}</td>
        <td className="num">{dash ? '—' : `${formatNumber(row.totalHours, 1)} ч`}</td>
        <td className="num">
          {dash
            ? '—'
            : row.overtimeHours > 0
              ? <span style={{ color: 'var(--warn)' }}>{formatNumber(row.overtimeHours, 1)} ч</span>
              : '—'}
        </td>
        <td className="num">{dash ? '—' : row.remoteDays}</td>
        <td className="num">{dash ? '—' : row.vacationDays}</td>
        <td className="num">{dash ? '—' : row.sickDays}</td>
        <td className="muted t-xs">{expanded ? 'скрыть' : row.days.length > 0 ? 'дни' : ''}</td>
      </tr>
      {expanded && (
        <tr>
          <td colSpan={8} style={{ background: 'var(--surface-2)', padding: 12 }}>
            {row.days.length === 0 ? (
              <span className="t-sm muted">Записей за период нет.</span>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>Дата</th>
                    <th>Часы</th>
                    <th>Переработка</th>
                    <th>Отметки</th>
                  </tr>
                </thead>
                <tbody>
                  {row.days.map((day) => (
                    <tr key={day.date}>
                      <td className="num t-xs">{formatDate(day.date)}</td>
                      <td className="num">{day.hours != null ? formatNumber(day.hours, 1) : '—'}</td>
                      <td className="num">
                        {day.overtimeHours != null && day.overtimeHours > 0
                          ? formatNumber(day.overtimeHours, 1)
                          : '—'}
                      </td>
                      <td className="t-xs">
                        {[
                          day.remote ? 'удалённо' : null,
                          day.sickLeave ? 'больничный' : null,
                          day.vacation ? 'отпуск' : null
                        ].filter(Boolean).join(' · ') || '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </td>
        </tr>
      )}
    </>
  );
}

/** Карточка показателя — те же классы `.kpi`, что на обзоре и аналитике. */
function Metric(props: { label: string; value: number | string; unit?: string; foot: string; color?: string }) {
  return (
    <div className="kpi">
      <div className="kpi-top"><span className="kpi-label">{props.label}</span></div>
      <div className="kpi-val" style={props.color ? { color: props.color } : undefined}>
        {props.value}
        {props.unit && <span className="unit">{props.unit}</span>}
      </div>
      <div className="kpi-foot"><span>{props.foot}</span></div>
    </div>
  );
}
