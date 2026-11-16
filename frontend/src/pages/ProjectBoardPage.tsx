import { useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { BoardTask, EmployeeItem, ProjectBoard, TaskStatus } from '../types';
import { formatDate, formatNumber } from '../format';

const COLUMNS: Array<{ status: TaskStatus; title: string }> = [
  { status: 'TODO', title: 'К выполнению' },
  { status: 'IN_PROGRESS', title: 'В работе' },
  { status: 'REVIEW', title: 'На проверке' },
  { status: 'DONE', title: 'Готово' }
];

const TRANSITION_LABEL: Record<TaskStatus, string> = {
  TODO: 'В очередь',
  IN_PROGRESS: 'В работу',
  REVIEW: 'На проверку',
  DONE: 'Готово'
};

const PRIORITY_BADGE: Record<string, string> = {
  LOW: 'badge-outline', MEDIUM: 'badge-info', HIGH: 'badge-warn', CRITICAL: 'badge-danger'
};

/**
 * Доска проекта: четыре колонки по статусам BR-014.
 *
 * Переходы не дублируются в клиенте — сервер отдаёт allowedTransitions для
 * каждой задачи, интерфейс рисует ровно их. Если правило изменится в
 * TaskStatus, кнопки поменяются сами.
 */
export function ProjectBoardPage({ projectId }: { projectId: number }) {
  const [board, setBoard] = useState<ProjectBoard | null>(null);
  const [employees, setEmployees] = useState<EmployeeItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busyTask, setBusyTask] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [assignee, setAssignee] = useState('');
  const [priority, setPriority] = useState('');
  const [query, setQuery] = useState('');

  const load = () => {
    setError(null);
    api.project(projectId).then(setBoard).catch((e: Error) => setError(e.message));
  };

  useEffect(load, [projectId]);

  useEffect(() => {
    api.employees({ page: 0, size: 200, search: '', department: '' })
      .then((page) => setEmployees(page.content))
      .catch(() => setEmployees([]));
  }, []);

  /**
   * Динамическая фильтрация (FR-035): по исполнителю, приоритету и названию.
   * Фильтр применяется к уже загруженной доске, поэтому работает мгновенно,
   * без обращения к серверу, и счётчики колонок пересчитываются тут же.
   */
  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return (board?.tasks ?? []).filter((task) => {
      if (assignee && String(task.assigneeId ?? '') !== assignee) return false;
      if (priority && task.priority !== priority) return false;
      if (needle && !task.title.toLowerCase().includes(needle)) return false;
      return true;
    });
  }, [board, assignee, priority, query]);

  const byStatus = useMemo(() => {
    const map: Record<TaskStatus, BoardTask[]> = {
      TODO: [], IN_PROGRESS: [], REVIEW: [], DONE: []
    };
    filtered.forEach((task) => map[task.status].push(task));
    return map;
  }, [filtered]);

  const assignees = useMemo(() => {
    const seen = new Map<number, string>();
    (board?.tasks ?? []).forEach((task) => {
      if (task.assigneeId && task.assigneeName) seen.set(task.assigneeId, task.assigneeName);
    });
    return [...seen.entries()];
  }, [board]);

  const isFiltered = Boolean(assignee || priority || query);

  const move = (task: BoardTask, status: TaskStatus) => {
    setBusyTask(task.id);
    setError(null);
    setNotice(null);
    api.setTaskStatus(task.id, status)
      .then(() => {
        setNotice(`«${task.title}» → ${TRANSITION_LABEL[status]}`);
        load();
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusyTask(null));
  };

  const create = (payload: {
    title: string; assigneeId: number | null; priority: string;
    estimateHours: number | null; dueDate: string | null; startDate: string | null;
  }) => {
    setCreating(true);
    setError(null);
    api.createTask(projectId, {
      title: payload.title,
      assigneeId: payload.assigneeId,
      priority: payload.priority,
      estimateHours: payload.estimateHours,
      dueDate: payload.dueDate || null,
      // Без этой строки форма спрашивала плановую дату, но не отправляла её:
      // задача сохранялась без плана, и план-график показывал «план не задан».
      startDate: payload.startDate || null
    })
      .then(() => { setNotice('Задача создана'); load(); })
      .catch((e: Error) => setError(e.message))
      .finally(() => setCreating(false));
  };

  return (
    <Shell
      active="projects"
      crumbs={['Работа', board ? board.name : 'Доска']}
      actions={
        <>
          <a className="btn btn-sm btn-outline" href={'#/projects/' + projectId + '/plan'}>План-график</a>
          <button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>
          <a className="btn btn-sm btn-ghost" href="#/projects">К списку</a>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 16 }}>
        <div>
          <div className="micro mb-8">Доска проекта</div>
          <h1 className="t-hero">{board?.name ?? 'Загрузка…'}</h1>
          <div className="t-sm muted mt-8">
            {board?.ownerName ? 'PM: ' + board.ownerName + ' · ' : ''}
            {board ? board.counts.total + ' задач · готово ' + board.counts.progressPercent + '%' : ''}
          </div>
        </div>
        <div className="spacer" />
        {board && <TaskForm employees={employees} busy={creating} onSubmit={create} />}
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}

      <div className="card card-flush mt-16">
        <div className="table-toolbar">
          <div className="searchbox">
            <input
              className="input input-sm"
              placeholder="Название задачи"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </div>
          <div className="spacer" />
          <select
            className="input input-sm"
            style={{ width: 190 }}
            value={assignee}
            onChange={(event) => setAssignee(event.target.value)}
          >
            <option value="">Все исполнители</option>
            {assignees.map(([id, name]) => (
              <option key={id} value={id}>{name}</option>
            ))}
          </select>
          <select
            className="input input-sm"
            style={{ width: 160 }}
            value={priority}
            onChange={(event) => setPriority(event.target.value)}
          >
            <option value="">Любой приоритет</option>
            <option value="CRITICAL">Критичный</option>
            <option value="HIGH">Высокий</option>
            <option value="MEDIUM">Средний</option>
            <option value="LOW">Низкий</option>
          </select>
          {isFiltered && (
            <button
              className="btn btn-sm btn-ghost"
              type="button"
              onClick={() => { setAssignee(''); setPriority(''); setQuery(''); }}
            >
              Сбросить
            </button>
          )}
          <span className="muted t-xs">
            {filtered.length} из {board?.tasks.length ?? 0}
          </span>
        </div>
      </div>

      <div className="grid g4 mt-16">
        {COLUMNS.map((column) => (
          <div key={column.status} className="card card-flush">
            <div className="card-hd">
              <h2 className="t-h3">{column.title}</h2>
              <div className="spacer" />
              <span className="badge badge-neutral">{byStatus[column.status].length}</span>
            </div>
            <div className="card-bd" style={{ display: 'grid', gap: 10 }}>
              {byStatus[column.status].length === 0 && (
                <div className="t-xs muted">Пусто</div>
              )}
              {byStatus[column.status].map((task) => (
                <div key={task.id} className="card card-bd" style={{ boxShadow: 'var(--sh-xs)' }}>
                  <div style={{ fontWeight: 600, fontSize: 13 }}>{task.title}</div>
                  <div className="row t-xs muted mt-4" style={{ gap: 8, flexWrap: 'wrap' }}>
                    {task.priority && (
                      <span className={'badge ' + (PRIORITY_BADGE[task.priority] ?? 'badge-outline')}>
                        {task.priority}
                      </span>
                    )}
                    {task.assigneeName && <span>{task.assigneeName}</span>}
                    {task.dueDate && <span>до {formatDate(task.dueDate)}</span>}
                    {task.estimateHours != null && <span>{formatNumber(task.estimateHours, 0)} ч</span>}
                  </div>

                  <div className="row mt-8" style={{ gap: 6, flexWrap: 'wrap' }}>
                    {task.allowedTransitions.map((target) => (
                      <button
                        key={target}
                        className="btn btn-sm btn-outline"
                        type="button"
                        disabled={busyTask === task.id}
                        onClick={() => move(task, target)}
                      >
                        {TRANSITION_LABEL[target]}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </Shell>
  );
}

/** Форма создания задачи: раскрывается по кнопке, чтобы не занимать экран. */
function TaskForm(props: {
  employees: EmployeeItem[];
  busy: boolean;
  onSubmit: (payload: {
    title: string; assigneeId: number | null; priority: string;
    estimateHours: number | null; dueDate: string | null; startDate: string | null;
  }) => void;
}) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [assignee, setAssignee] = useState('');
  const [priority, setPriority] = useState('MEDIUM');
  const [estimate, setEstimate] = useState('');
  const [startAt, setStartAt] = useState('');
  const [due, setDue] = useState('');

  if (!open) {
    return (
      <button className="btn btn-sm btn-primary" type="button" onClick={() => setOpen(true)}>
        Новая задача
      </button>
    );
  }

  return (
    <div className="card card-bd" style={{ minWidth: 320 }}>
      <div className="t-h3 mb-8">Новая задача</div>
      <div style={{ display: 'grid', gap: 8 }}>
        <input
          className="input"
          placeholder="Название"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
        />
        <select className="input" value={assignee} onChange={(event) => setAssignee(event.target.value)}>
          <option value="">Без исполнителя</option>
          {props.employees.map((employee) => (
            <option key={employee.userId} value={employee.userId}>{employee.fullName}</option>
          ))}
        </select>
        <div className="row gap-8">
          <select className="input" value={priority} onChange={(event) => setPriority(event.target.value)}>
            <option value="LOW">Низкий</option>
            <option value="MEDIUM">Средний</option>
            <option value="HIGH">Высокий</option>
            <option value="CRITICAL">Критичный</option>
          </select>
          <input
            className="input"
            type="number"
            placeholder="Часы"
            value={estimate}
            onChange={(event) => setEstimate(event.target.value)}
          />
        </div>
        <input
          className="input"
          type="date"
          value={due}
          onChange={(event) => setDue(event.target.value)}
        />
        <label className="t-xs muted">Плановая дата старта</label>
        <input
          className="input"
          type="date"
          value={startAt}
          onChange={(event) => setStartAt(event.target.value)}
        />
        <div className="row gap-8">
          <button
            className="btn btn-sm btn-primary"
            type="button"
            disabled={props.busy || !title.trim()}
            onClick={() => {
              props.onSubmit({
                title: title.trim(),
                assigneeId: assignee ? Number(assignee) : null,
                priority,
                estimateHours: estimate ? Number(estimate) : null,
                startDate: startAt || null,
                dueDate: due || null
              });
              setTitle('');
              setEstimate('');
              setDue('');
              setOpen(false);
            }}
          >
            {props.busy ? 'Создание…' : 'Создать'}
          </button>
          <button className="btn btn-sm btn-ghost" type="button" onClick={() => setOpen(false)}>
            Отмена
          </button>
        </div>
      </div>
    </div>
  );
}
