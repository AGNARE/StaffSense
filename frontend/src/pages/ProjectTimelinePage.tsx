import { useCallback, useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { BoardTask, ProjectBoard, TaskStatus } from '../types';
import { formatNumber } from '../format';

/**
 * План-график проекта (прототип `pr-plan`).
 *
 * <p><b>Откуда даты.</b> `GET /api/v1/projects/{id}` отдаёт задачу с тремя
 * датами: `createdAt` (когда задачу завели), `dueDate` (дедлайн) и
 * `completedAt` (когда закрыли). Плановой даты старта в API нет, поэтому
 * полоса рисуется по факту: от создания до дедлайна — это не «план из
 * прототипа», а реальный интервал задачи. Ничего не досчитываем и не
 * подставляем: у задачи без `dueDate` в строке честно написано «нет срока»,
 * а не выдуманный отрезок.
 *
 * <p><b>Шкала.</b> Диапазон графика — от понедельника недели самой ранней даты
 * до воскресенья недели самой поздней. Ровно поэтому колонки недель в шапке
 * совпадают с полосами: обе считаются от одного и того же дня, без «плавающих»
 * процентов. Сегодняшний день — вертикальная линия, тоже по реальной дате.
 *
 * <p><b>Стили.</b> Классы `.gantt*` уже есть в дизайн-системе (`proto.css`) —
 * новых CSS-файлов и библиотек не нужно. Положение полос задаётся инлайново,
 * как в прототипе это делал `proto.js`.
 */
export function ProjectTimelinePage({ projectId }: { projectId: number }) {
  const [board, setBoard] = useState<ProjectBoard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    setNotFound(false);
    api.project(projectId)
      .then((data) => setBoard(data))
      .catch((e: Error) => {
        // 404 — это «проекта нет», а не «сервер сломался»: разные сообщения.
        if (/HTTP 404/.test(e.message)) {
          setNotFound(true);
        } else {
          setError(e.message);
        }
      })
      .finally(() => setLoading(false));
  }, [projectId]);

  useEffect(load, [load]);

  // Один «сегодня» на рендер: иначе useMemo ниже пересчитывался бы на каждое
  // обновление состояния из-за новой ссылки на Date.
  const today = useMemo(() => startOfDay(new Date()), []);

  const timeline = useMemo(() => {
    if (!board) return null;

    const rows = buildRows(board.tasks, today);
    const dated = rows.filter((row) => row.barStart !== null && row.barEnd !== null);

    if (dated.length === 0) {
      return { rows, dated: 0, undated: rows.length, weeks: 0, from: null, to: null, totalDays: 0 };
    }

    const times = dated.flatMap((row) => [
      (row.barStart as Date).getTime(),
      (row.barEnd as Date).getTime()
    ]);
    const from = mondayOf(new Date(Math.min(...times)));
    const to = addDays(mondayOf(new Date(Math.max(...times))), 6);
    const totalDays = diffDays(to, from) + 1;

    return {
      rows,
      dated: dated.length,
      undated: rows.length - dated.length,
      weeks: totalDays / 7,
      from,
      to,
      totalDays
    };
  }, [board, today]);

  const overdue = useMemo(
    () => (timeline?.rows ?? [])
      .filter((row) => row.overdueDays > 0)
      .sort((a, b) => b.overdueDays - a.overdueDays),
    [timeline]
  );

  /** Доля сегодняшнего дня на шкале: линия рисуется только если сегодня в диапазоне. */
  const todayFrac = timeline?.from ? todayFraction(today, timeline.from, timeline.totalDays) : null;

  if (notFound) {
    return (
      <Shell
        active="projects"
        crumbs={['Работа', 'План-график']}
        actions={<a className="btn btn-sm btn-outline" href="#/projects">К проектам</a>}
      >
        <div className="card card-bd">
          <div className="t-h3 mb-8">Проект #{projectId} не найден</div>
          <div className="t-sm muted">
            Такого проекта нет: возможно, он удалён или ссылка содержит неверный
            идентификатор. Откройте список проектов и выберите нужный — план-график
            строится по данным конкретного проекта.
          </div>
          <div className="row mt-16">
            <a className="btn btn-sm btn-primary" href="#/projects">Список проектов</a>
          </div>
        </div>
      </Shell>
    );
  }

  const counts = board?.counts;
  const rows = timeline?.rows ?? [];
  // Сумма по непустым оценкам: если оценок нет вовсе, «0 ч» было бы выдумкой.
  const hours = rows.reduce((sum, row) => sum + (row.task.estimateHours ?? 0), 0);
  const estimated = rows.filter((row) => row.task.estimateHours !== null).length;

  return (
    <Shell
      active="projects"
      crumbs={['Работа', board ? board.name : 'План-график']}
      actions={
        <>
          <button className="btn btn-sm btn-outline" type="button" onClick={load} disabled={loading}>
            Обновить
          </button>
          <a className="btn btn-sm btn-ghost" href={'#/projects/' + projectId}>Доска</a>
          <a className="btn btn-sm btn-ghost" href="#/projects">К списку</a>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Проект · план-график</div>
          <h1 className="t-hero">{board ? board.name : loading ? 'Загрузка…' : 'План-график'}</h1>
          <div className="t-sm muted mt-8">
            {board?.ownerName ? 'PM: ' + board.ownerName + ' · ' : ''}
            {timeline?.from && timeline.to
              ? `${dayLabel(timeline.from)} — ${dayLabel(timeline.to)} · ${timeline.weeks} нед.`
              : timeline
                ? 'ни у одной задачи нет срока — шкалу строить не из чего'
                : ''}
          </div>
        </div>
        <div className="spacer" />
        {board && estimated > 0 && (
          <span className="badge badge-neutral">
            {formatNumber(hours, 0)} ч в оценках
          </span>
        )}
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {loading && !board && !error && <div className="card card-bd muted">Загрузка…</div>}

      {board && timeline && (
        <>
          <div className="grid g4">
            <Metric
              label="Задач"
              value={counts ? counts.total : '—'}
              foot={counts ? 'готово ' + counts.progressPercent + '%' : '—'}
            />
            <Metric
              label="Со сроком"
              value={timeline.dated}
              foot={
                timeline.undated > 0
                  ? `из ${counts?.total ?? timeline.rows.length} · без срока ${timeline.undated}`
                  : `из ${counts?.total ?? timeline.rows.length}`
              }
            />
            <Metric
              label="Просрочено"
              value={overdue.length}
              color={overdue.length > 0 ? 'var(--danger)' : undefined}
              foot={overdue.length > 0 ? 'срок раньше сегодняшнего дня' : 'сроков не нарушено'}
            />
            <Metric
              label="Недель на шкале"
              value={timeline.weeks > 0 ? timeline.weeks : '—'}
              foot={
                timeline.from && timeline.to
                  ? `${dayLabel(timeline.from)} — ${dayLabel(timeline.to)}`
                  : 'нет датированных задач'
              }
            />
          </div>

          <div className="card card-flush mt-16">
            <div className="card-hd">
              <h2 className="t-h3">Задачи на шкале</h2>
              <div className="spacer" />
              <div className="legend">
                <span className="k"><span className="sw" style={{ background: 'var(--ok)' }} />Готово</span>
                <span className="k"><span className="sw" style={{ background: 'var(--brand-3)' }} />В работе</span>
                <span className="k"><span className="sw" style={{ background: 'var(--warn)' }} />Закрыто позже срока</span>
                <span className="k"><span className="sw" style={{ background: 'var(--danger)' }} />Просрочено</span>
                <span className="k">
                  <span className="sw" style={{ background: 'transparent', boxShadow: 'inset 0 0 0 1.5px var(--line-strong)' }} />
                  План (к выполнению)
                </span>
                {/* Линия «сегодня» есть только когда сегодняшний день попадает
                    в диапазон задач — легенда не обещает того, чего нет. */}
                {todayFrac !== null && (
                  <span className="k">
                    <span className="sw" style={{ width: 2, height: 10, borderRadius: 1, background: 'var(--danger)' }} />
                    Сегодня
                  </span>
                )}
              </div>
            </div>

            {timeline.weeks === 0 ? (
              <div className="card-bd t-sm muted">
                Ни у одной задачи нет дедлайна — полосы рисовать не по чему. Срок
                задаётся при создании задачи на доске проекта.
              </div>
            ) : (
              <div className="table-wrap">
                {/* Ширина недели не меньше 48 px: иначе подписи дат в шапке
                    наезжают друг на друга, а полосы становятся неразличимы. */}
                <div className="gantt" style={{ minWidth: Math.max(760, timeline.weeks * 48) }}>
                  <div className="gantt-hd">
                    <div className="gantt-hd-l">Задача · исполнитель</div>
                    <div
                      className="gantt-scale"
                      style={{ gridTemplateColumns: `repeat(${timeline.weeks}, minmax(0, 1fr))` }}
                    >
                      {weekStarts(timeline.from as Date, timeline.weeks).map((week) => (
                        <span className="wk" key={week.getTime()} title={'Неделя с ' + dayLabel(week)}>
                          {dayLabel(week)}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div className="gantt-body">
                    {todayFrac !== null && (
                      <div
                        className="gantt-today"
                        title={'Сегодня — ' + dayLabel(today)}
                        style={{ left: 'calc(252px + (100% - 252px) * ' + todayFrac + ')' }}
                      />
                    )}

                    {STATUS_ORDER.map((status) => {
                      const group = timeline.rows.filter((row) => row.task.status === status);
                      const groupHours = group.reduce(
                        (sum, row) => sum + (row.task.estimateHours ?? 0),
                        0
                      );
                      const groupEstimated = group.filter(
                        (row) => row.task.estimateHours !== null
                      ).length;
                      return (
                        <GroupRows
                          key={status}
                          status={status}
                          rows={group}
                          hours={groupHours}
                          estimated={groupEstimated}
                          total={timeline.rows.length}
                          weeks={timeline.weeks}
                          from={timeline.from as Date}
                          totalDays={timeline.totalDays}
                        />
                      );
                    })}
                  </div>
                </div>
              </div>
            )}

            <div className="table-foot">
              <span>
                {timeline.rows.length} задач · {timeline.dated} со сроком · {timeline.undated} без срока ·
                полоса = от создания до дедлайна
              </span>
              <div className="spacer" />
              {overdue.length > 0 && (
                <span className="badge badge-danger">{overdue.length} просрочено</span>
              )}
              {timeline.weeks > 0 && <span className="badge badge-neutral">{timeline.weeks} нед.</span>}
            </div>
          </div>

          <div className="grid g-2-1 mt-16">
            <div className="card card-flush">
              <div className="card-hd">
                <h2 className="t-h3">Просроченные задачи</h2>
                <div className="spacer" />
                <span className="badge badge-danger">{overdue.length}</span>
              </div>
              {overdue.length === 0 ? (
                <div className="card-bd t-sm muted">
                  {timeline.dated === 0
                    ? 'Сроков нет — просрочку определять не из чего.'
                    : 'Просроченных нет: у всех незакрытых задач срок ещё не наступил.'}
                </div>
              ) : (
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Задача</th>
                        <th>Исполнитель</th>
                        <th>Срок</th>
                        <th>Просрочка</th>
                      </tr>
                    </thead>
                    <tbody>
                      {overdue.map((row) => (
                        <tr key={row.task.id}>
                          <td>
                            <span className="t-main">{row.task.title}</span>
                            <span className="sub">{STATUS[row.task.status].title}</span>
                          </td>
                          <td className="muted t-xs">{row.task.assigneeName ?? 'не назначен'}</td>
                          <td className="num muted t-xs">{dayLabel(row.barEnd)}</td>
                          <td className="num">
                            <span className="badge badge-danger">{row.overdueDays} дн.</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            <div className="card">
              <div className="card-hd">
                <h2 className="t-h3">По статусам</h2>
                <div className="spacer" />
                <span className="t-xs muted">доля задач</span>
              </div>
              <div className="card-bd">
                <div className="gantt-phases">
                  {STATUS_ORDER.map((status, index) => {
                    const count = timeline.rows.filter((row) => row.task.status === status).length;
                    const share = timeline.rows.length
                      ? Math.round((count * 100) / timeline.rows.length)
                      : 0;
                    return (
                      <div
                        key={status}
                        className={
                          'phase-row' +
                          (status === 'DONE' ? ' is-done' : status === 'IN_PROGRESS' ? ' is-now' : '')
                        }
                      >
                        <span className="phase-num">{index + 1}</span>
                        <span>{STATUS[status].title}</span>
                        <span className={'badge ' + STATUS[status].badge}>
                          {count} · {share}%
                        </span>
                      </div>
                    );
                  })}
                </div>
                <div className="t-xs muted mt-16">
                  Проценты — доля задач в статусе, а не готовность проекта: прогресс
                  считается сервером по закрытым задачам.
                </div>
              </div>
            </div>
          </div>
        </>
      )}
    </Shell>
  );
}

const DAY_MS = 24 * 60 * 60 * 1000;

const STATUS_ORDER: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'];

const STATUS: Record<TaskStatus, { title: string; badge: string }> = {
  TODO: { title: 'К выполнению', badge: 'badge-neutral' },
  IN_PROGRESS: { title: 'В работе', badge: 'badge-info' },
  REVIEW: { title: 'На проверке', badge: 'badge-warn' },
  DONE: { title: 'Готово', badge: 'badge-ok' }
};

/** Цвет точки приоритета: сам текст приоритета в узкой колонке не помещается. */
const PRIORITY_COLOR: Record<string, string> = {
  CRITICAL: 'var(--danger)',
  HIGH: 'var(--warn)',
  MEDIUM: 'var(--brand-3)',
  LOW: 'var(--ink-4)'
};

/** Задача на шкале: реальные даты, просрочка и опоздание закрытия. */
interface TimelineRow {
  task: BoardTask;
  /** Начало полосы: дата создания; если она позже дедлайна — сам дедлайн. */
  barStart: Date | null;
  /** Конец полосы: дедлайн. `null` — у задачи нет срока, полосы не будет. */
  barEnd: Date | null;
  /** Дней сверх срока у незакрытой задачи; 0 — не просрочена. */
  overdueDays: number;
  /** На сколько дней задача закрыта позже срока; 0 — вовремя или не закрыта. */
  lateDays: number;
  planKnown: boolean;
}

/**
 * Календарный день из ISO-строки API.
 *
 * `dueDate` приходит как `2026-11-06`, даты-время — как `2026-11-06T10:12:00`.
 * Оба формата режем до `гггг-мм-дд` и собираем локальную дату: `new Date('2026-11-06')`
 * трактуется как UTC и в отрицательных часовых поясах уехал бы на день назад.
 */
function parseDay(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!match) return null;
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return Number.isNaN(date.getTime()) ? null : date;
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

function diffDays(later: Date, earlier: Date): number {
  return Math.round((startOfDay(later).getTime() - startOfDay(earlier).getTime()) / DAY_MS);
}

/** Понедельник недели, в которую попадает дата: шкала всегда начинается с него. */
function mondayOf(date: Date): Date {
  return addDays(date, -((date.getDay() + 6) % 7));
}

/** Подпись дня без сдвига часового пояса: даты уже локальные. */
function dayLabel(date: Date | null): string {
  return date ? date.toLocaleDateString('ru-RU') : '—';
}

function weekStarts(from: Date, weeks: number): Date[] {
  return Array.from({ length: weeks }, (_, index) => addDays(from, index * 7));
}

/** Доля сегодняшнего дня на шкале (0..1) или `null`, если сегодня вне диапазона. */
function todayFraction(today: Date, from: Date, totalDays: number): number | null {
  const offset = diffDays(today, from);
  if (totalDays <= 0 || offset < 0 || offset >= totalDays) return null;
  return offset / totalDays;
}

function buildRows(tasks: BoardTask[], today: Date): TimelineRow[] {
  return tasks.map((task) => {
    const created = parseDay(task.createdAt);
    const due = parseDay(task.dueDate);
    const completed = parseDay(task.completedAt);
    const plannedStart = parseDay(task.startDate ?? null);
    const closed = task.status === 'DONE';

    // Плановая дата старта (startDate) есть с миграции V20: если она задана —
    // полоса показывает план, иначе честно факт создания. Признак planKnown
    // уходит в подпись строки, чтобы зритель видел разницу.
    const barStart = plannedStart ?? (due && created && diffDays(created, due) > 0 ? created : due);
    const barEnd = due;
    const planKnown = plannedStart != null;

    const overdueDays = due && !closed ? Math.max(diffDays(today, due), 0) : 0;
    const lateDays = due && closed && completed ? Math.max(diffDays(completed, due), 0) : 0;

    return { task, barStart, barEnd, overdueDays, lateDays, planKnown };
  });
}

/** Строки одной группы статуса: заголовок группы и задачи с полосами. */
function GroupRows(props: {
  status: TaskStatus;
  rows: TimelineRow[];
  hours: number;
  estimated: number;
  total: number;
  weeks: number;
  from: Date;
  totalDays: number;
}) {
  const { status, rows, hours, estimated, total, weeks, from, totalDays } = props;
  const gridCells = Array.from({ length: weeks }, (_, index) => <i key={index} />);
  const gridStyle = { gridTemplateColumns: `repeat(${weeks}, minmax(0, 1fr))` };

  return (
    <>
      <div className="gantt-row" style={{ background: 'var(--surface-2)' }}>
        <div className="gantt-label" style={{ color: 'var(--ink)', fontWeight: 620 }}>
          <span className={'badge ' + STATUS[status].badge}>{rows.length}</span>
          <span className="nm">{STATUS[status].title}</span>
        </div>
        <div className="gantt-track">
          <div className="gantt-grid" style={gridStyle}>{gridCells}</div>
          <span
            className="t-xs muted"
            style={{ display: 'flex', alignItems: 'center', height: '100%', paddingLeft: 12 }}
          >
            {rows.length === 0
              ? 'нет задач'
              : `${total > 0 ? Math.round((rows.length * 100) / total) : 0}% задач` +
                (estimated > 0 ? ` · оценка ${formatNumber(hours, 1)} ч` : ' · оценки нет')}
          </span>
        </div>
      </div>

      {rows.map((row) => (
        <TaskRow key={row.task.id} row={row} weeks={weeks} from={from} totalDays={totalDays} />
      ))}
    </>
  );
}

/** Одна задача: подпись слева, полоса (или «нет срока») справа. */
function TaskRow(props: { row: TimelineRow; weeks: number; from: Date; totalDays: number }) {
  const { row, weeks, from, totalDays } = props;
  const { task, barStart, barEnd, overdueDays, lateDays } = row;
  const gridCells = Array.from({ length: weeks }, (_, index) => <i key={index} />);
  const gridStyle = { gridTemplateColumns: `repeat(${weeks}, minmax(0, 1fr))` };

  const left = barStart ? (diffDays(barStart, from) / totalDays) * 100 : 0;
  const width =
    barStart && barEnd
      ? Math.max(((diffDays(barEnd, barStart) + 1) / totalDays) * 100, 0.8)
      : 0;

  const barClass =
    overdueDays > 0
      ? 'gantt-bar is-late'
      : task.status === 'DONE'
        ? lateDays > 0
          ? 'gantt-bar is-warn'
          : 'gantt-bar is-ok'
        : task.status === 'TODO'
          ? 'gantt-bar is-plan'
          : 'gantt-bar';

  const note =
    overdueDays > 0
      ? `просрочено на ${overdueDays} дн.`
      : lateDays > 0
        ? `закрыта позже на ${lateDays} дн.`
        : '';

  // Плана нет — это не ошибка данных, а состояние: полоса построена от
  // даты создания. Подпись говорит об этом прямо, чтобы не выдавать факт
  // за план.
  const planNote = row.planKnown ? '' : ' · план не задан';

  const assignee = task.assigneeName ? task.assigneeName.split(/\s+/)[0] : null;
  const hint =
    `${task.title} · ${STATUS[task.status].title}` +
    (task.priority ? ` · приоритет ${task.priority}` : '') +
    (task.assigneeName ? ` · ${task.assigneeName}` : ` · без исполнителя`) + planNote;

  return (
    <div className="gantt-row">
      <div className="gantt-label" title={hint}>
        <span
          title={task.priority ?? 'приоритет не задан'}
          style={{
            width: 6,
            height: 6,
            borderRadius: 3,
            flex: 'none',
            background: PRIORITY_COLOR[task.priority ?? ''] ?? 'var(--line-strong)'
          }}
        />
        <span className="nm">{task.title}</span>
        {assignee && <span className="t-xs muted" style={{ flex: 'none' }}>{assignee}</span>}
      </div>
      <div className="gantt-track">
        <div className="gantt-grid" style={gridStyle}>{gridCells}</div>
        {barStart && barEnd ? (
          <div
            className={barClass}
            title={`${task.title}: ${dayLabel(barStart)} — ${dayLabel(barEnd)}`}
            style={{ left: left + '%', width: width + '%' }}
          >
            {note && <span className="lbl">{note}</span>}
          </div>
        ) : (
          <span
            className="t-xs muted"
            style={{ display: 'flex', alignItems: 'center', height: '100%', paddingLeft: 12 }}
          >
            нет срока
          </span>
        )}
      </div>
    </div>
  );
}

/** Карточка показателя — те же классы `.kpi`, что на обзоре и аналитике. */
function Metric(props: { label: string; value: number | string; foot: string; color?: string }) {
  return (
    <div className="kpi">
      <div className="kpi-top"><span className="kpi-label">{props.label}</span></div>
      <div className="kpi-val" style={props.color ? { color: props.color } : undefined}>
        {props.value}
      </div>
      <div className="kpi-foot"><span>{props.foot}</span></div>
    </div>
  );
}
