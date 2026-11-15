import { useCallback, useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { formatDate, initials } from '../format';
import { HR_DESK_SCREEN, hrApi } from '../hr-api';
import type { AbsenceType, HrAbsences, HrDesk } from '../hr-api';

/** Подписи и цвет бейджа для типов отсутствия: те же, что в прототипе. */
const ABSENCE_BADGE: Record<AbsenceType, string> = {
  VACATION: 'badge-info',
  SICK_LEAVE: 'badge-danger',
  OTHER: 'badge-ai'
};

const SOURCE_TITLE: Record<string, string> = {
  MEMO: 'записка',
  TIMESHEET: 'табель'
};

/** Сегодня в формате `yyyy-mm-dd` — значение по умолчанию для поля «на дату». */
function todayIso(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

/**
 * «Пульт HR»: сводка по кадрам на дату.
 *
 * Данные приходят из `GET /api/v1/hr/desk`. Клиент ничего не досчитывает:
 * срок испытательного срока, доля отсутствующих и список «без прогноза»
 * считаются на сервере по правилам, которых в схеме БД нет (поля
 * «испытательный срок» не существует — он выводится из даты приёма).
 * Поэтому блок «Испытательный срок» подписан правилом, пришедшим в ответе,
 * а не цифрой из вёрстки.
 *
 * Пустой массив = «данных нет»: экран пишет это словами, а не рисует ноль.
 */
export function HrDeskPage() {
  const [date, setDate] = useState<string>(todayIso());
  const [desk, setDesk] = useState<HrDesk | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    hrApi.desk(date)
      .then(setDesk)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [date]);

  useEffect(load, [load]);

  const headcount = desk?.headcount;
  const probation = desk?.probation;
  const forecast = desk?.forecast;
  const absences = desk?.absences;

  return (
    <Shell
      active={HR_DESK_SCREEN}
      crumbs={['Кадры', 'Пульт HR']}
      actions={
        <>
          <input
            className="input input-sm"
            type="date"
            aria-label="Дата сводки"
            value={date}
            max={todayIso()}
            onChange={(event) => setDate(event.target.value)}
          />
          <button className="btn btn-sm btn-outline" type="button" onClick={load} disabled={loading}>
            Обновить
          </button>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Модуль 1 · кадровый процесс</div>
          <h1 className="t-hero">Люди и процессы</h1>
          <div className="t-sm muted mt-8">
            {loading && !desk
              ? 'Загрузка…'
              : headcount
                ? `${headcount.total} в штате · ${headcount.employees} сотрудников · ${probation?.total ?? 0} на испытательном сроке`
                : '—'}
          </div>
        </div>
        <div className="spacer" />
        <div className="row gap-8">
          <a className="btn" href="#/documents">Документ</a>
          <a className="btn" href="#/employees">Сотрудник</a>
          <a className="btn btn-primary" href="#/timesheet">Табель</a>
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}

      <div className="grid g4">
        <Metric
          label="В штате"
          value={headcount ? headcount.total : '—'}
          unit="чел."
          foot={headcount
            ? `сотрудников ${headcount.employees} · прочие ${headcount.other}`
            : 'нет данных'}
        />
        <Metric
          label="На испытательном"
          value={probation ? probation.total : '—'}
          unit="чел."
          foot={probation ? `правило: дата приёма + ${probation.months} мес.` : 'нет данных'}
        />
        <Metric
          label="Без прогноза увольнения"
          value={forecast ? forecast.withoutForecast : '—'}
          color={forecast && forecast.withoutForecast > 0 ? 'var(--warn)' : undefined}
          foot={forecast ? `прогноз есть у ${forecast.withForecast} из ${forecast.total}` : 'нет данных'}
        />
        <Metric
          label="Отсутствуют сейчас"
          value={absences ? absences.todayCount : '—'}
          color={absences && absences.todayCount > 0 ? 'var(--danger)' : undefined}
          foot={absences
            ? `на неделе ${absences.weekCount} · ближайших ${absences.upcomingCount}`
            : 'нет данных'}
        />
      </div>

      <div className="grid g-3-2 mt-16 g-start">
        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Испытательный срок</h2>
            <div className="spacer" />
            <span className="t-xs muted">
              {probation ? `${probation.months} мес. с даты приёма` : '—'}
            </span>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Сотрудник</th>
                  <th>Принят</th>
                  <th>Окончание</th>
                  <th>Осталось</th>
                  <th>Чек-лист</th>
                </tr>
              </thead>
              <tbody>
                {probation && probation.items.length > 0 ? (
                  probation.items.map((item) => {
                    const percent = item.checklistTotal > 0
                      ? Math.round((item.checklistDone * 100) / item.checklistTotal)
                      : 0;
                    return (
                      <tr key={item.userId}>
                        <td>
                          <div className="row gap-8">
                            <div className="ava ava-sm ava-1">{initials(item.fullName)}</div>
                            <div>
                              <span className="t-main">{item.fullName}</span>
                              <span className="sub">
                                {item.position ?? '—'}{item.department ? ' · ' + item.department : ''}
                              </span>
                            </div>
                          </div>
                        </td>
                        <td className="num muted t-xs">{formatDate(item.hireDate)}</td>
                        <td className="num muted t-xs">{formatDate(item.probationEnd)}</td>
                        <td>
                          <span className={item.daysLeft <= 7 ? 'badge badge-warn' : 'badge badge-neutral'}>
                            {item.daysLeft === 0 ? 'последний день' : `${item.daysLeft} дн.`}
                          </span>
                        </td>
                        <td>
                          {item.checklistTotal > 0 ? (
                            <div className="row gap-8">
                              <div className="bar" style={{ flex: 1 }}>
                                <span style={{ width: `${percent}%` }} />
                              </div>
                              <span className="t-xs num">
                                {item.checklistDone}/{item.checklistTotal}
                              </span>
                            </div>
                          ) : (
                            <span className="muted t-xs">—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td colSpan={5} className="muted" style={{ padding: 24 }}>
                      На испытательном сроке никого: срок выводится из даты приёма,
                      и у всех сотрудников он уже прошёл.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <span>
              {probation ? `${probation.total} чел. · основание: ${probation.basis}` : '—'}
            </span>
            <div className="spacer" />
            <a className="btn btn-sm btn-ghost" href="#/onboarding">Онбординг</a>
          </div>
        </div>

        <div className="card">
          <div className="card-hd">
            <h2 className="t-h3">Покрытие</h2>
            <div className="spacer" />
            <span className="t-xs muted">отсутствия на {formatDate(desk?.date)}</span>
          </div>
          <div className="card-bd">
            {absences && absences.coverage.length > 0 ? (
              <div className="col gap-10">
                {absences.coverage.map((row) => (
                  <div className="hbar" key={row.department}>
                    <span className="nm">{row.department}</span>
                    <span className="track">
                      <i
                        style={{
                          width: `${row.sharePercent ?? 0}%`,
                          background: row.absent > 0 ? 'var(--danger)' : undefined
                        }}
                      />
                    </span>
                    <span className="v">
                      {row.absent}/{row.headcount}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="t-sm muted">Нет данных по подразделениям.</div>
            )}
            <div className="t-xs muted mt-16">
              Отсутствия — утверждённые записки; где записки нет, учитываются
              отметки табеля. Доля меньше 100% не значит, что все остальные работают:
              табель заполняют не все.
            </div>
          </div>
        </div>
      </div>

      <AbsencesCard absences={absences} loading={loading && !desk} />

      <div className="grid g-2-1 mt-16 g-start">
        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Без прогноза увольнения</h2>
            <div className="spacer" />
            <span className="badge badge-neutral">{forecast ? forecast.withoutForecast : '—'}</span>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Сотрудник</th>
                  <th>Отдел</th>
                  <th>Принят</th>
                </tr>
              </thead>
              <tbody>
                {forecast && forecast.items.length > 0 ? (
                  forecast.items.map((item) => (
                    <tr key={item.userId}>
                      <td>
                        <span className="t-main">{item.fullName}</span>
                        <span className="sub">{item.position ?? '—'}</span>
                      </td>
                      <td className="muted t-xs">{item.department ?? '—'}</td>
                      <td className="num muted t-xs">{formatDate(item.hireDate)}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={3} className="muted" style={{ padding: 24 }}>
                      У всех сотрудников есть сохранённый прогноз увольнения.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <span>
              {forecast
                ? `прогноз есть у ${forecast.withForecast} из ${forecast.total} сотрудников`
                : '—'}
            </span>
            <div className="spacer" />
            <a className="btn btn-sm btn-ghost" href="#/risk">Риски увольнений</a>
          </div>
        </div>

        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Документы на согласовании</h2>
            <div className="spacer" />
            <span className="badge badge-neutral">{desk?.documents.onApproval ?? '—'}</span>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Номер</th>
                  <th>Документ</th>
                  <th>Дата</th>
                </tr>
              </thead>
              <tbody>
                {desk && desk.documents.items.length > 0 ? (
                  desk.documents.items.map((item) => (
                    <tr key={item.id}>
                      <td className="num t-xs">{item.regNumber}</td>
                      <td>
                        <span className="t-main">{item.title}</span>
                        <span className="sub">{item.employeeName ?? '—'}</span>
                      </td>
                      <td className="num muted t-xs">{formatDate(item.documentDate)}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={3} className="muted" style={{ padding: 24 }}>
                      Документов на согласовании нет.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <span>статус «на согласовании» в реестре документов</span>
            <div className="spacer" />
            <a className="btn btn-sm btn-ghost" href="#/documents">Реестр</a>
          </div>
        </div>
      </div>
    </Shell>
  );
}

type AbsenceTab = 'today' | 'week' | 'upcoming';

/**
 * Отсутствия: сегодня / неделя / ближайшие.
 *
 * Три среза в одной карточке, потому что это один и тот же список с разными
 * границами периода — отдельные карточки дублировали бы разметку.
 */
function AbsencesCard(props: { absences: HrAbsences | undefined; loading: boolean }) {
  const { absences, loading } = props;
  const [tab, setTab] = useState<AbsenceTab>('today');

  const rows = tab === 'today'
    ? absences?.today ?? []
    : tab === 'week'
      ? absences?.week ?? []
      : absences?.upcoming ?? [];

  return (
    <div className="card card-flush mt-16">
      <div className="card-hd">
        <h2 className="t-h3">Отсутствия</h2>
        <div className="spacer" />
        <div className="seg">
          <button
            type="button"
            className={tab === 'today' ? 'is-on' : ''}
            aria-pressed={tab === 'today'}
            onClick={() => setTab('today')}
          >
            Сегодня ({absences?.todayCount ?? 0})
          </button>
          <button
            type="button"
            className={tab === 'week' ? 'is-on' : ''}
            aria-pressed={tab === 'week'}
            onClick={() => setTab('week')}
          >
            Неделя ({absences?.weekCount ?? 0})
          </button>
          <button
            type="button"
            className={tab === 'upcoming' ? 'is-on' : ''}
            aria-pressed={tab === 'upcoming'}
            onClick={() => setTab('upcoming')}
          >
            Ближайшие ({absences?.upcomingCount ?? 0})
          </button>
        </div>
      </div>
      <div className="table-wrap">
        <table className="table table-wide">
          <thead>
            <tr>
              <th>Сотрудник</th>
              <th>Тип</th>
              <th>Период</th>
              <th>Дней</th>
              <th>Источник</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={5} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={5} className="muted" style={{ padding: 24 }}>
                  {tab === 'today'
                    ? 'На эту дату отсутствующих нет.'
                    : tab === 'week'
                      ? 'На этой неделе отсутствий нет.'
                      : 'Ближайших отсутствий нет.'}
                </td>
              </tr>
            ) : (
              rows.map((item) => (
                <tr key={`${item.userId}-${item.from}-${item.type}`}>
                  <td>
                    <div className="row gap-8">
                      <div className="ava ava-xs ava-2">{initials(item.fullName)}</div>
                      <div>
                        <span className="t-main">{item.fullName}</span>
                        <span className="sub">
                          {item.position ?? '—'}{item.department ? ' · ' + item.department : ''}
                        </span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span className={`badge ${ABSENCE_BADGE[item.type]}`}>{item.typeTitle}</span>
                  </td>
                  <td className="num t-xs">
                    {formatDate(item.from)} — {formatDate(item.to)}
                  </td>
                  <td className="num">{item.days}</td>
                  <td className="muted t-xs">{SOURCE_TITLE[item.source] ?? item.source}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <div className="table-foot">
        <span>записка — утверждённая служебная записка · табель — только отметки часов</span>
        <div className="spacer" />
        <a className="btn btn-sm btn-ghost" href="#/timesheet">Открыть табель</a>
      </div>
    </div>
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
