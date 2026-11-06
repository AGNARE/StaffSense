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

  const byStatus = useMemo(() => {
    const map: Record<TaskStatus, BoardTask[]> = {
      TODO: [], IN_PROGRESS: [], REVIEW: [], DONE: []
    };
    board?.tasks.forEach((task) => map[task.status].push(task));
    return map;
  }, [board]);

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
    estimateHours: number | null; dueDate: string | null;
  }) => {
    setCreating(true);
    setError(null);
    api.createTask(projectId, {
      title: payload.title,
      assigneeId: payload.assigneeId,
      priority: payload.priority,
      estimateHours: payload.estimateHours,
      dueDate: payload.dueDate || null
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
    estimateHours: number | null; dueDate: string | null;
  }) => void;
}) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [assignee, setAssignee] = useState('');
  const [priority, setPriority] = useState('MEDIUM');
  const [estimate, setEstimate] = useState('');
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
