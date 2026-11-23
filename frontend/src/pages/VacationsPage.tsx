import { useCallback, useEffect, useState } from 'react';
import { Modal } from '../components/Modal';
import { Shell } from '../components/Shell';
import { loadMe } from '../access';
import { formatNumber } from '../format';
import { hrApi } from '../hr-api';

interface Balance {
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

/**
 * Дни из поля ввода.
 *
 * <p>Пустое поле — «не задано» ({@code null}), нечисло — {@code NaN}, чтобы
 * вызывающий отличал одно от другого. Запятая принимается как разделитель:
 * «0,5 дня» люди набирают именно так.
 */
function parseDays(raw: string): number | null {
  const value = raw.trim().replace(',', '.');
  if (value === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : NaN;
}

/** Дни хранятся с одним знаком после запятой (numeric(5,1)) — не длиннее. */
function roundDays(value: number): number {
  return Math.round(value * 10) / 10;
}

/**
 * Проверка одного поля «дней».
 *
 * <p>Возвращает либо значение, либо текст ошибки — но не оба сразу. Ошибки
 * показываются под полем, поэтому текст короткий и обращён к человеку:
 * «Значение не может быть отрицательным», а не «entitledDays must be >= 0».
 */
function checkDays(raw: string, options: { required: boolean }): {
  value: number | null;
  error: string | null;
} {
  const parsed = parseDays(raw);

  if (parsed === null) {
    return options.required
      ? { value: null, error: 'Укажите, сколько дней начислить — пустое поле не отправляется' }
      : { value: 0, error: null };
  }
  if (Number.isNaN(parsed)) {
    return { value: null, error: 'Дни указываются числом, например 2 или 0,5' };
  }
  if (parsed < 0) {
    return { value: null, error: 'Значение не может быть отрицательным' };
  }
  return { value: parsed, error: null };
}

/**
 * Отпуска: право, использование и остаток.
 *
 * <p>Право — кадровое решение, поэтому у части сотрудников его может не быть
 * («не начислено»). Остаток в этом случае не считается: показывать ноль
 * было бы утверждением, которого мы не проверяли.
 *
 * <p>Сотрудник видит только свой баланс: управленческий список закрыт ролью,
 * а персональная ручка {@code /api/v1/hr/vacations/me} отдаёт одну строку.
 *
 * <p>Начисление — действие HR/ADMIN через {@code POST /api/v1/hr/vacations/{id}}.
 * Форма открывается в модальном окне по центру (общий {@link Modal}), а не в
 * строке таблицы: полей три, и в строке они не читаются.
 */
export function VacationsPage() {
  const [items, setItems] = useState<Balance[]>([]);
  const [role, setRole] = useState<string | null>(null);
  const [roleLoaded, setRoleLoaded] = useState(false);
  const [year, setYear] = useState(new Date().getFullYear());
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [granting, setGranting] = useState<Balance | null>(null);

  /** Роль берём из того же профиля, что и проверка доступа: второй список не нужен. */
  useEffect(() => {
    let alive = true;
    loadMe()
      .then((profile) => {
        if (alive) setRole(profile.role);
      })
      .catch(() => {
        if (alive) setRole(null);
      })
      .finally(() => {
        if (alive) setRoleLoaded(true);
      });
    return () => {
      alive = false;
    };
  }, []);

  const isEmployee = role === 'EMPLOYEE';
  /** Начислять право может кадровая служба; EXEC видит список, но не правит его. */
  const canGrant = role === 'HR' || role === 'ADMIN';

  const load = useCallback(() => {
    if (!roleLoaded) {
      return;
    }
    setLoading(true);
    setError(null);
    // Сотруднику — только его строка; HR/ADMIN/EXEC — общий список.
    const path = isEmployee
      ? '/api/v1/hr/vacations/me?year=' + year
      : '/api/v1/hr/vacations?year=' + year;

    fetch(path, { credentials: 'same-origin' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Не удалось загрузить отпуска: HTTP ' + response.status);
        return response.json() as Promise<Balance | Balance[]>;
      })
      .then((data) => setItems(Array.isArray(data) ? data : [data]))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [roleLoaded, isEmployee, year]);

  useEffect(() => {
    load();
  }, [load]);

  const known = items.filter((item) => item.entitledKnown);
  const totalRemaining = known.reduce((sum, item) => sum + (item.remainingDays ?? 0), 0);
  const own = isEmployee ? items[0] : undefined;
  const columns = canGrant ? 7 : 6;

  return (
    <Shell active="vacations" crumbs={['Люди', 'Отпуска']} actions={
      roleLoaded && !isEmployee ? (
        <div className="row gap-8">
          <button className="btn btn-sm btn-outline" type="button" onClick={() => setYear(year - 1)}>← {year - 1}</button>
          <span className="badge badge-neutral">{year}</span>
          <button className="btn btn-sm btn-outline" type="button" onClick={() => setYear(year + 1)}>{year + 1} →</button>
        </div>
      ) : undefined
    }>
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Кадры · отпуска</div>
          <h1 className="t-hero">Отпуска</h1>
          <div className="t-sm muted mt-8">
            {isEmployee
              ? 'Ваш баланс отпуска: право, перенос, использование и остаток за ' + year + ' год'
              : 'Право на отпуск приходит из кадрового решения, использование считается по утверждённым запискам'}
          </div>
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}

      {isEmployee ? (
        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Мой баланс</h2>
            <div className="spacer" />
            <span className="badge badge-neutral">{year}</span>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th className="num">Положено</th>
                  <th className="num">Перенос</th>
                  <th className="num">Использовано</th>
                  <th className="num">Остаток</th>
                </tr>
              </thead>
              <tbody>
                {loading && <tr><td colSpan={4} className="muted" style={{ padding: 20 }}>Загрузка…</td></tr>}
                {!loading && !own && (
                  <tr><td colSpan={4} className="muted" style={{ padding: 20 }}>Нет данных</td></tr>
                )}
                {!loading && own && (
                  <tr>
                    <td className="num">{own.entitledKnown ? formatNumber(own.entitledDays ?? 0, 1) : '—'}</td>
                    <td className="num">{own.entitledKnown ? formatNumber(own.carriedOverDays ?? 0, 1) : '—'}</td>
                    <td className="num">{formatNumber(own.usedDays, 1)}</td>
                    <td className="num">
                      {own.entitledKnown ? (
                        <strong style={{ color: (own.remainingDays ?? 0) < 0 ? 'var(--danger)' : undefined }}>
                          {formatNumber(own.remainingDays ?? 0, 1)}
                        </strong>
                      ) : '—'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <span className="muted t-xs">
              Использовано — только утверждённые записки. Прочерк — право не начислено.
            </span>
          </div>
        </div>
      ) : (
        <>
          <div className="grid g4">
            <div className="kpi">
              <div className="kpi-top"><span className="kpi-label">Сотрудников</span></div>
              <div className="kpi-val">{items.length}</div>
              <div className="kpi-foot"><span>активные</span></div>
            </div>
            <div className="kpi">
              <div className="kpi-top"><span className="kpi-label">Право начислено</span></div>
              <div className="kpi-val">{known.length} <span className="unit">из {items.length}</span></div>
              <div className="kpi-foot"><span>остальным остаток не считается</span></div>
            </div>
            <div className="kpi">
              <div className="kpi-top"><span className="kpi-label">Остаток всего</span></div>
              <div className="kpi-val">{formatNumber(totalRemaining, 1)} <span className="unit">дн.</span></div>
              <div className="kpi-foot"><span>по начисленным</span></div>
            </div>
            <div className="kpi">
              <div className="kpi-top"><span className="kpi-label">Год</span></div>
              <div className="kpi-val">{year}</div>
              <div className="kpi-foot"><span>календарный</span></div>
            </div>
          </div>

          <div className="card card-flush mt-16">
            <div className="card-hd">
              <h2 className="t-h3">Баланс по сотрудникам</h2>
              <div className="spacer" />
              <span className="muted t-xs">
                {canGrant ? 'начисление — кнопкой в строке' : 'использовано — только утверждённые записки'}
              </span>
            </div>
            <div className="table-wrap">
              <table className="table table-wide">
                <thead>
                  <tr>
                    <th>Сотрудник</th>
                    <th>Отдел</th>
                    <th className="num">Положено</th>
                    <th className="num">Перенос</th>
                    <th className="num">Использовано</th>
                    <th className="num">Остаток</th>
                    {canGrant && <th />}
                  </tr>
                </thead>
                <tbody>
                  {loading && <tr><td colSpan={columns} className="muted" style={{ padding: 20 }}>Загрузка…</td></tr>}
                  {!loading && items.length === 0 && (
                    <tr><td colSpan={columns} className="muted" style={{ padding: 20 }}>Нет данных</td></tr>
                  )}
                  {items.map((item) => (
                    <tr key={item.userId}>
                      <td>
                        <div className="t-main">{item.fullName}</div>
                        <div className="sub">{item.position ?? '—'}</div>
                      </td>
                      <td className="t-xs">{item.department ?? '—'}</td>
                      <td className="num">{item.entitledKnown ? formatNumber(item.entitledDays ?? 0, 1) : '—'}</td>
                      <td className="num">{item.entitledKnown ? formatNumber(item.carriedOverDays ?? 0, 1) : '—'}</td>
                      <td className="num">{formatNumber(item.usedDays, 1)}</td>
                      <td className="num">
                        {item.entitledKnown ? (
                          <strong style={{ color: (item.remainingDays ?? 0) < 0 ? 'var(--danger)' : undefined }}>
                            {formatNumber(item.remainingDays ?? 0, 1)}
                          </strong>
                        ) : '—'}
                      </td>
                      {canGrant && (
                        <td className="num">
                          <button
                            className="btn btn-sm btn-outline"
                            type="button"
                            title={'Начислить право на отпуск: ' + item.fullName}
                            onClick={() => {
                              setNotice(null);
                              setGranting(item);
                            }}
                          >
                            Начислить
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="table-foot">
              <span className="muted t-xs">
                Прочерк — право не начислено. Ноль дней и «не начислено» — разные утверждения,
                поэтому остаток не подставляется автоматически.
              </span>
            </div>
          </div>
        </>
      )}

      {granting && (
        <GrantVacationModal
          item={granting}
          year={year}
          onClose={() => setGranting(null)}
          onDone={() => {
            setGranting(null);
            setError(null);
            setNotice('Право на отпуск начислено');
            load();
          }}
        />
      )}
    </Shell>
  );
}

/**
 * Форма начисления права на отпуск.
 *
 * <p>Поле означает <b>прибавку</b>, а не новое значение: «начислить 2 дня при
 * праве 28» — это 28 + 2 = 30. Сервер хранит одно число за год и понимает тело
 * запроса как итог: {@code VacationBalanceService#grant} перезаписывает
 * {@code entitled_days}. Поэтому сумму считает клиент и отправляет уже её —
 * иначе 2 дня превращали право 30 в 2 (дефект проверки).
 *
 * <p>Проверки на клиенте повторяют серверные (BR: отрицательных дней не
 * бывает), но не заменяют их: сервер остаётся источником правды, а клиент не
 * гоняет заведомо неверный запрос. Отрицательное или пустое значение блокирует
 * кнопку, а под полем появляется объяснение. Дробные дни разрешены — 0,5 бывает,
 * поэтому поле текстовое с {@code inputMode="decimal"}: в {@code type="number"}
 * Chrome не принимает запятую, а «0,5 дня» набирают именно так.
 */
function GrantVacationModal(props: {
  item: Balance;
  year: number;
  onClose: () => void;
  onDone: () => void;
}) {
  const { item, year } = props;
  /** Текущее право; {@code null} — «не начислено», а не ноль. */
  const current = item.entitledKnown ? item.entitledDays ?? 0 : null;

  // Поле добавки пустое: «сколько прибавить» — всегда осознанный ввод, а не
  // подставленное значение, которое легко принять за новое право.
  const [added, setAdded] = useState('');
  // Перенос — не добавка, а факт: показываем текущий и сохраняем, если не правят.
  const [carried, setCarried] = useState(
    item.entitledKnown ? String(item.carriedOverDays ?? 0) : ''
  );
  const [note, setNote] = useState('');
  /** Ошибка сервера: проверки полей показываются под самими полями. */
  const [serverError, setServerError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const delta = checkDays(added, { required: true });
  const carry = checkDays(carried, { required: false });

  /** Итог, который уйдёт на сервер: текущее право плюс добавка. */
  const total =
    delta.value === null
      ? null
      : current === null
        ? roundDays(delta.value)
        : roundDays(current + delta.value);

  const canSubmit = !busy && delta.error === null && carry.error === null && total !== null;

  const submit = () => {
    setServerError(null);
    if (!canSubmit || total === null) {
      return;
    }

    setBusy(true);
    hrApi.grantVacation(item.userId, {
      year,
      entitledDays: total,
      carriedOverDays: carry.value ?? 0,
      note: note.trim() === '' ? null : note.trim()
    })
      .then(() => props.onDone())
      .catch((e: Error) => setServerError(e.message))
      .finally(() => setBusy(false));
  };

  return (
    <Modal
      title="Начислить право на отпуск"
      subtitle={`${item.fullName} · ${year} год`}
      onClose={props.onClose}
      footer={
        <>
          <button
            className="btn btn-sm btn-primary"
            type="button"
            disabled={!canSubmit}
            title={canSubmit ? 'Начислить указанные дни' : 'Исправьте значение в поле'}
            onClick={submit}
          >
            {busy ? 'Сохранение…' : 'Начислить'}
          </button>
          <button className="btn btn-sm btn-ghost" type="button" onClick={props.onClose}>Отмена</button>
        </>
      }
    >
      {serverError && (
        <div className="callout callout-danger" style={{ marginBottom: 16 }}>
          <div className="t-sm">{serverError}</div>
        </div>
      )}

      <div className="grid g2">
        <label className="field">
          <span className="field-label">Начислить, дней <span className="field-req">*</span></span>
          <input
            className="input"
            type="text"
            inputMode="decimal"
            placeholder="2"
            aria-invalid={delta.error !== null}
            style={delta.error !== null ? { borderColor: 'var(--danger)' } : undefined}
            value={added}
            onChange={(event) => setAdded(event.target.value)}
          />
          {delta.error !== null ? (
            <span className="field-hint" role="alert" style={{ color: 'var(--danger)' }}>
              {delta.error}
            </span>
          ) : delta.value !== null && total !== null ? (
            <span className="field-hint">
              {current === null
                ? `Сейчас не начислено. Будет: ${formatNumber(total, 1)} дн. — это станет правом`
                : `Сейчас начислено: ${formatNumber(current, 1)} дн. Будет: ${formatNumber(current, 1)} + ${formatNumber(delta.value, 1)} = ${formatNumber(total, 1)} дн.`}
            </span>
          ) : (
            <span className="field-hint">
              Сколько дней прибавить к праву за {year} год
            </span>
          )}
        </label>
        <label className="field">
          <span className="field-label">Перенос, дней</span>
          <input
            className="input"
            type="text"
            inputMode="decimal"
            placeholder="0"
            aria-invalid={carry.error !== null}
            style={carry.error !== null ? { borderColor: 'var(--danger)' } : undefined}
            value={carried}
            onChange={(event) => setCarried(event.target.value)}
          />
          {carry.error !== null ? (
            <span className="field-hint" role="alert" style={{ color: 'var(--danger)' }}>
              {carry.error}
            </span>
          ) : (
            <span className="field-hint">Остаток с прошлого года; пусто — ноль</span>
          )}
        </label>
      </div>

      <label className="field mt-16">
        <span className="field-label">Примечание</span>
        <input
          className="input"
          type="text"
          maxLength={300}
          placeholder="Например: ежегодный отпуск по графику"
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
      </label>

      <div className="t-xs muted mt-16">
        Дни прибавляются к текущему праву, а серверу уходит готовый итог: за год
        хранится одно значение, а не история начислений.
      </div>
    </Modal>
  );
}
