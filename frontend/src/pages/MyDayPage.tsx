import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import type { Screen } from '../components/Shell';
import { api } from '../api';
import type { Me } from '../api';
import type { Memo, SurveyItem } from '../types';
import { formatDate, formatNumber } from '../format';

/**
 * Shell ещё не знает раздела «Мой день»: ключ в `Screen` добавляет интегратор
 * вместе с маршрутом `#/my-day` (`App.tsx`/`Shell.tsx` — чужие файлы, их не
 * правим). Приведение через `unknown` собирается и до, и после этой правки;
 * до неё в меню просто нет активного пункта. Приём тот же, что у
 * `ANALYTICS_SCREEN` и `HR_DESK_SCREEN`.
 */
export const MY_DAY_SCREEN = 'myDay' as unknown as Screen;

/** Встреча 1:1 глазами участника — контракт `GET /api/v1/oneonone/my`. */
interface OneOnOneMeeting {
  id: number;
  employeeId: number;
  employeeName: string;
  managerId: number;
  managerName: string;
  scheduledAt: string | null;
  status: string;
  statusTitle: string;
  missed: boolean;
  notes: string | null;
  actionItems: string | null;
  upcoming: boolean;
  canManage: boolean;
  myRole: 'EMPLOYEE' | 'MANAGER';
}

/**
 * Баланс отпуска — контракт `GET /api/v1/hr/vacations/me`.
 * `entitledKnown = false` означает «право не начислено»: остаток в этом случае
 * не считается, и подставлять ноль нельзя.
 */
interface VacationBalance {
  userId: number;
  fullName: string;
  department: string | null;
  position: string | null;
  year: number;
  entitledDays: number | null;
  carriedOverDays: number | null;
  usedDays: number;
  remainingDays: number | null;
  entitledKnown: boolean;
}

type Scope = 'today' | 'week';

/**
 * «Мой день» (экран `ov-day` прототипа) — личная сводка на сегодня.
 *
 * Собирается из уже существующих ручек и **не заводит своего эндпоинта**:
 * встречи — `GET /api/v1/oneonone/my`, записки — `GET /api/v1/memos`, опросы —
 * `GET /api/v1/surveys`, отпуск — `GET /api/v1/hr/vacations/me`, имя — `/api/v1/me`.
 * Сервер уже отдаёт каждому только его данные, клиент не изобретает выборки.
 *
 * Главное правило экрана: **чего нет — того нет**. Каждый блок грузится
 * независимо (`Promise.all` с отдельными `catch`), и если ручка не ответила,
 * блок честно пишет «нет данных», а не показывает ноль. Так же и с отпуском:
 * при `entitledKnown = false` выводится «не начислено», потому что «ноль дней»
 * и «право не начислено» — разные утверждения.
 *
 * Записки фильтруются по `userId` из профиля: `/api/v1/memos` для HR и
 * руководителя отдаёт и чужие записки, а «мои записки в работе» — это мои.
 */
export function MyDayPage() {
  const [me, setMe] = useState<Me | null>(null);
  const [meetings, setMeetings] = useState<OneOnOneMeeting[] | null>(null);
  const [memos, setMemos] = useState<Memo[] | null>(null);
  const [surveys, setSurveys] = useState<SurveyItem[] | null>(null);
  const [balance, setBalance] = useState<VacationBalance | null>(null);
  const [failures, setFailures] = useState<string[]>([]);
  const [scope, setScope] = useState<Scope>('today');
  /** Счётчик перезагрузки: «Обновить» перечитывает сводку, не перезагружая приложение. */
  const [reloadKey, setReloadKey] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const problems: string[] = [];
    // Перед перечитыванием сбрасываем блоки в «нет данных»: иначе при
    // неудачном обновлении на экране остались бы старые числа под видом свежих.
    setMe(null);
    setMeetings(null);
    setMemos(null);
    setSurveys(null);
    setBalance(null);
    setFailures([]);
    setLoading(true);

    Promise.all([
      api.me()
        .then(setMe)
        .catch((e: Error) => { problems.push('профиль: ' + e.message); }),
      getJson<OneOnOneMeeting[]>('/api/v1/oneonone/my?limit=50')
        .then(setMeetings)
        .catch((e: Error) => { problems.push('встречи: ' + e.message); }),
      api.memos()
        .then(setMemos)
        .catch((e: Error) => { problems.push('записки: ' + e.message); }),
      api.surveys()
        .then(setSurveys)
        .catch((e: Error) => { problems.push('опросы: ' + e.message); }),
      getJson<VacationBalance>('/api/v1/hr/vacations/me')
        .then(setBalance)
        .catch((e: Error) => { problems.push('отпуск: ' + e.message); })
    ]).finally(() => {
      setFailures([...problems]);
      setLoading(false);
    });
    // reloadKey в зависимостях: это и есть повторная загрузка.
  }, [reloadKey]);

  const now = new Date();
  const dayStart = startOfDay(now);
  const weekEnd = addDays(dayStart, 7);

  const todaysMeetings = meetings === null ? null : meetings.filter(
    (meeting) => meeting.scheduledAt && sameDay(new Date(meeting.scheduledAt), now));
  const weekMeetings = meetings === null ? null : meetings.filter((meeting) => {
    if (!meeting.scheduledAt) return false;
    const at = new Date(meeting.scheduledAt);
    return at >= dayStart && at < weekEnd;
  });

  // Записки «в работе» — те, по которым ещё не принято финальное решение.
  const myMemos = me === null || memos === null
    ? null
    : memos.filter((memo) => memo.userId === me.id);
  const memosInWork = myMemos === null ? null : myMemos.filter(
    (memo) => memo.status === 'DRAFT' || memo.status === 'PENDING_PM' || memo.status === 'PENDING_HR');

  const pendingSurveys = surveys === null ? null : surveys.filter(
    (survey) => survey.status === 'ACTIVE' && survey.canTake && !survey.alreadyTaken);

  const shownMeetings = scope === 'today' ? todaysMeetings : weekMeetings;

  return (
    <Shell
      active={MY_DAY_SCREEN}
      crumbs={['Люди', 'Мой день']}
      actions={
        <>
          <a className="btn btn-sm btn-ghost" href="#/me">Мой кабинет</a>
          <button
            className="btn btn-sm btn-outline"
            type="button"
            disabled={loading}
            onClick={() => setReloadKey(reloadKey + 1)}
          >
            Обновить
          </button>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">{longToday(now)}</div>
          <h1 className="t-hero">{greeting(now, me?.fullName ?? null)}</h1>
          <div className="t-sm muted mt-8">{summaryLine(todaysMeetings, memosInWork, pendingSurveys)}</div>
        </div>
      </div>

      {failures.length > 0 && (
        <div className="callout callout-warn mb-16">
          <div>
            Часть данных не загрузилась — экран показывает только то, что пришло:
            <div className="t-xs muted mt-4">{failures.join('; ')}</div>
          </div>
        </div>
      )}

      <div className="grid g4">
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Встречи 1:1 сегодня</span></div>
          <div className="kpi-val">{todaysMeetings === null ? '—' : todaysMeetings.length}</div>
          <div className="kpi-foot"><span>{todaysMeetings === null ? 'нет данных' : 'по расписанию'}</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Записки в работе</span></div>
          <div className="kpi-val">{memosInWork === null ? '—' : memosInWork.length}</div>
          <div className="kpi-foot"><span>{memosInWork === null ? 'нет данных' : 'ждут решения'}</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Опросы к прохождению</span></div>
          <div className="kpi-val">{pendingSurveys === null ? '—' : pendingSurveys.length}</div>
          <div className="kpi-foot"><span>{pendingSurveys === null ? 'нет данных' : 'активные, не пройдены'}</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Остаток отпуска</span></div>
          <div className="kpi-val">
            {balance === null
              ? '—'
              : balance.entitledKnown
                ? <>{formatNumber(balance.remainingDays, 1)} <span className="unit">дн.</span></>
                : <span style={{ fontSize: 15, fontWeight: 560 }}>не начислено</span>}
          </div>
          <div className="kpi-foot">
            <span>{balance === null ? 'нет данных' : 'год ' + balance.year}</span>
          </div>
        </div>
      </div>

      <div className="grid g-2-1 mt-16" style={{ alignItems: 'start' }}>
        <div className="grid" style={{ gap: 14 }}>
          <div className="card">
            <div className="card-hd">
              <h2 className="t-h3">Расписание</h2>
              <div className="spacer" />
              <div className="seg">
                <button
                  type="button"
                  className={scope === 'today' ? 'active' : ''}
                  onClick={() => setScope('today')}
                >
                  Сегодня
                </button>
                <button
                  type="button"
                  className={scope === 'week' ? 'active' : ''}
                  onClick={() => setScope('week')}
                >
                  Неделя
                </button>
              </div>
            </div>
            <div className="card-bd">
              {shownMeetings === null && <div className="t-sm muted">нет данных</div>}
              {shownMeetings?.length === 0 && (
                <div className="empty">
                  <div className="empty-ico">—</div>
                  <div className="t-sm muted">
                    {scope === 'today'
                      ? 'Встреч 1:1 на сегодня нет'
                      : 'На ближайшую неделю встреч нет'}
                  </div>
                </div>
              )}
              {shownMeetings && shownMeetings.length > 0 && (
                <div className="timeline">
                  {shownMeetings.map((meeting) => (
                    <div
                      className={'tl-item' + (meeting.missed ? ' is-warn' : meeting.upcoming ? ' is-now' : '')}
                      key={meeting.id}
                    >
                      <div className="tl-dot" />
                      <div className="row">
                        <span className="tl-when">
                          {meeting.scheduledAt ? timeOf(meeting.scheduledAt) : 'время не задано'}
                        </span>
                        {meeting.upcoming && <span className="badge badge-info">впереди</span>}
                        {meeting.missed && <span className="badge badge-warn">пропущена</span>}
                      </div>
                      <div className="t-sm strong mt-4">
                        1:1 {meeting.myRole === 'MANAGER' ? 'с ' + meeting.employeeName : 'с ' + meeting.managerName}
                      </div>
                      <div className="t-xs muted">
                        {meeting.statusTitle}
                        {meeting.actionItems ? ' · есть договорённости' : ''}
                        {meeting.notes ? ' · есть заметки' : ''}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div className="card card-flush">
            <div className="card-hd">
              <h2 className="t-h3">Мои записки в работе</h2>
              <div className="spacer" />
              <span className="badge badge-neutral">{memosInWork === null ? '—' : memosInWork.length}</span>
            </div>
            <div className="table-wrap">
              <table className="table table-wide">
                <thead>
                  <tr>
                    <th>Записка</th>
                    <th>Период</th>
                    <th>Дней</th>
                    <th>Статус</th>
                  </tr>
                </thead>
                <tbody>
                  {memosInWork === null && (
                    <tr><td colSpan={4} className="muted" style={{ padding: 20 }}>нет данных</td></tr>
                  )}
                  {memosInWork?.length === 0 && (
                    <tr><td colSpan={4} className="muted" style={{ padding: 20 }}>
                      Записок в работе нет — все решения по ним уже приняты
                    </td></tr>
                  )}
                  {memosInWork?.map((memo) => (
                    <tr key={memo.id}>
                      <td>
                        <div className="t-main">{memo.typeTitle}</div>
                        <div className="sub">{memo.reason ?? 'без причины'}</div>
                      </td>
                      <td className="muted t-xs">
                        {formatDate(memo.startDate)} — {formatDate(memo.endDate)}
                      </td>
                      <td className="num">{memo.days}</td>
                      <td><span className="badge badge-warn">{memo.statusTitle}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="table-foot">
              <span className="muted t-xs">
                Только мои записки: для HR и руководителя <code>/api/v1/memos</code> отдаёт
                и чужие — они сюда не попадают
              </span>
            </div>
          </div>
        </div>

        <div className="grid" style={{ gap: 14 }}>
          <div className="card">
            <div className="card-hd">
              <h2 className="t-h3">Опросы ко мне</h2>
              <div className="spacer" />
              <span className="badge badge-neutral">{pendingSurveys === null ? '—' : pendingSurveys.length}</span>
            </div>
            <div className="card-bd">
              {pendingSurveys === null && <div className="t-sm muted">нет данных</div>}
              {pendingSurveys?.length === 0 && (
                <div className="t-sm muted">Активных опросов, которые вы ещё не прошли, нет</div>
              )}
              {pendingSurveys && pendingSurveys.length > 0 && (
                <div className="col gap-10">
                  {pendingSurveys.map((survey) => (
                    <a key={survey.id} href="#/surveys" className="col" style={{ gap: 2 }}>
                      <span className="t-sm strong">{survey.title}</span>
                      <span className="t-xs muted">
                        {survey.endDate ? 'до ' + formatDate(survey.endDate) : 'без срока'}
                        {survey.anonymous ? ' · анонимный' : ''}
                      </span>
                    </a>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div className="card">
            <div className="card-hd">
              <h2 className="t-h3">Отпуск</h2>
              <div className="spacer" />
              <span className="t-xs muted">{balance === null ? 'нет данных' : balance.year}</span>
            </div>
            <div className="card-bd">
              {balance === null && <div className="t-sm muted">нет данных</div>}
              {balance && !balance.entitledKnown && (
                <div className="t-sm muted" style={{ lineHeight: 1.65 }}>
                  Право на отпуск на {balance.year} год не начислено — остаток не считается.
                  Это не «ноль дней»: пока HR не внёс право, показывать остаток не из чего.
                </div>
              )}
              {balance && balance.entitledKnown && (
                <>
                  <div className="kv">
                    <span className="k">Положено</span>
                    <span className="v">{formatNumber(balance.entitledDays, 1)} дн.</span>
                  </div>
                  <div className="kv mt-8">
                    <span className="k">Перенос</span>
                    <span className="v">{formatNumber(balance.carriedOverDays, 1)} дн.</span>
                  </div>
                  <div className="kv mt-8">
                    <span className="k">Использовано</span>
                    <span className="v">{formatNumber(balance.usedDays, 1)} дн.</span>
                  </div>
                  <div className="hairline" />
                  <div className="row">
                    <span className="t-sm muted">Остаток</span>
                    <div className="spacer" />
                    <span className="num" style={{ fontWeight: 650, fontSize: 18 }}>
                      {formatNumber(balance.remainingDays, 1)} дн.
                    </span>
                  </div>
                  <div className="t-xs muted mt-8">
                    Использовано считается по утверждённым запискам, а не по отметкам в табеле
                  </div>
                </>
              )}
              {balance && balance.usedDays !== undefined && !balance.entitledKnown && (
                <div className="t-xs muted mt-8">
                  Использовано в этом году: {formatNumber(balance.usedDays, 1)} дн.
                </div>
              )}
            </div>
          </div>

          <div className="card">
            <div className="card-hd"><h2 className="t-h3">Быстрые действия</h2></div>
            <div className="card-bd">
              <div className="grid g2 gap-8">
                {/* Ссылки фильтруются по доступным экранам из /api/v1/me:
                    не показываем сотруднику пункт, который откроет 403. */}
                {quickActions(me).map((action) => (
                  <a
                    className="btn"
                    style={{ justifyContent: 'flex-start', height: 38 }}
                    href={action.route}
                    key={action.route}
                  >
                    {action.title}
                  </a>
                ))}
                {quickActions(me).length === 0 && (
                  <div className="t-sm muted">нет данных о доступных разделах</div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </Shell>
  );
}

// ── даты и подписи ───────────────────────────────────────────────────────

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function addDays(date: Date, days: number): Date {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate();
}

/** Время встречи: «11:00». */
function timeOf(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

/** «Среда · 16 ноября» — без часов: экран не тикает, показывать «время» было бы враньём. */
function longToday(date: Date): string {
  const weekday = date.toLocaleDateString('ru-RU', { weekday: 'long' });
  const day = date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' });
  return capitalize(weekday) + ' · ' + day;
}

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

/** Приветствие по времени суток; имя — из профиля, иначе нейтрально. */
function greeting(now: Date, fullName: string | null): string {
  const hour = now.getHours();
  const hello = hour < 5 ? 'Доброй ночи' : hour < 12 ? 'Доброе утро' : hour < 18 ? 'Добрый день' : 'Добрый вечер';
  if (!fullName) return hello;
  const firstName = fullName.trim().split(/\s+/)[0];
  return hello + ', ' + firstName;
}

/** Склонение: 1 встреча, 2 встречи, 5 встреч. */
function plural(count: number, forms: [string, string, string]): string {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) return forms[0];
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return forms[1];
  return forms[2];
}

/** Подзаголовок: только факты, которые реально пришли. */
function summaryLine(
  meetings: OneOnOneMeeting[] | null,
  memos: Memo[] | null,
  surveys: SurveyItem[] | null
): string {
  const parts: string[] = [];
  if (meetings !== null) {
    parts.push(meetings.length === 0
      ? 'встреч 1:1 на сегодня нет'
      : meetings.length + ' ' + plural(meetings.length, ['встреча', 'встречи', 'встреч']) + ' 1:1 сегодня');
  }
  if (memos !== null) {
    parts.push(memos.length === 0
      ? 'записок в работе нет'
      : memos.length + ' ' + plural(memos.length, ['записка', 'записки', 'записок']) + ' в работе');
  }
  if (surveys !== null) {
    parts.push(surveys.length === 0
      ? 'опросов к прохождению нет'
      : surveys.length + ' ' + plural(surveys.length, ['опрос', 'опроса', 'опросов']) + ' ждут ответа');
  }
  return parts.length > 0 ? parts.join(' · ') : 'данные не загрузились — сводку собрать не из чего';
}

/** Разделы, к которым у пользователя действительно есть доступ. */
function quickActions(me: Me | null): Array<{ title: string; route: string }> {
  const wanted = [
    { title: 'Мои записки', route: '#/memos' },
    { title: 'Опросы', route: '#/surveys' },
    { title: 'Отпуска', route: '#/vacations' },
    { title: 'Портал онбординга', route: '#/onboarding' },
    { title: 'ИИ-скиллы', route: '#/skills' },
    { title: 'Каталог сотрудников', route: '#/directory' }
  ];
  if (!me?.screens) {
    // Без списка экранов показываем нейтральный минимум: он доступен всем.
    return wanted.slice(0, 3);
  }
  const allowed = new Set(me.screens.map((screen) => screen.route));
  return wanted.filter((action) => allowed.has(action.route));
}

// ── запрос ───────────────────────────────────────────────────────────────

/**
 * Чтение JSON с понятной ошибкой. Свой маленький помощник, а не `api.ts`:
 * `api.ts` — чужой файл, а `/api/v1/oneonone/my` и `/api/v1/hr/vacations/me`
 * в нём не описаны. Поведение то же, что в `api.ts`/`hr-api.ts`: сессия
 * истекла — человеческое сообщение, а не HTTP 302.
 */
async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  });

  if (response.redirected || response.status === 401 || response.status === 403) {
    throw new Error('нет доступа (сессия истекла или раздел закрыт)');
  }
  if (!response.ok) {
    throw new Error('HTTP ' + response.status);
  }
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    throw new Error('сервер вернул не JSON');
  }
  return response.json() as Promise<T>;
}
