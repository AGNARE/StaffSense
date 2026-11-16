import { useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';
import { contentApi } from '../content-api';
import type { QuestionType, SurveyDetail, SurveySaveRequest } from '../content-api';

/**
 * Конструктор опроса (прототип: экран {@code sv-builder}).
 *
 * <p>Экран собирает черновик целиком — параметры и вопросы — и сохраняет его
 * одним запросом: {@code POST /api/v1/surveys} создаёт, {@code PUT} правит.
 * Отдельной кнопки «сохранить вопрос» нет: полусохранённый набор вопросов у
 * активного опроса сломал бы связь ответов с вопросами.
 *
 * <p>Публикация — отдельное действие: {@code POST /api/v1/surveys/{id}/publish}.
 * До неё опрос виден только HR и администратору.
 *
 * <p>Маршруты: {@code #/surveys/builder} — новый опрос,
 * {@code #/surveys/builder/<id>} — правка сохранённого черновика.
 */

const SURVEY_TYPES = [
  { value: 'PULSE', title: 'Пульс' },
  { value: 'ENGAGEMENT', title: 'Вовлечённость' },
  { value: 'ONBOARDING', title: 'Адаптация' },
  { value: 'EXIT', title: 'Выходное интервью' },
  { value: 'WELLBEING', title: 'Благополучие' },
  { value: 'PERFORMANCE', title: 'Оценка' },
  { value: 'MANAGER_360', title: '360°' },
  { value: 'CULTURE', title: 'Культура' },
  { value: 'TRAINING', title: 'Обучение' },
  { value: 'CUSTOM', title: 'Свой' }
];

const SCOPES = [
  { value: 'COMPANY', title: 'Вся компания' },
  { value: 'DEPARTMENT', title: 'Отдел' },
  { value: 'TEAM', title: 'Команда' },
  { value: 'INDIVIDUAL', title: 'Отдельные сотрудники' }
];

const QUESTION_TYPES: Array<{ value: QuestionType; title: string }> = [
  { value: 'LIKERT_5', title: 'Шкала 1–5' },
  { value: 'FREE_TEXT', title: 'Свободный ответ' }
];

const LIKERT_LABELS = ['Совсем нет', 'Скорее нет', 'Нейтрально', 'Скорее да', 'Полностью да'];

/** Готовые наборы вопросов — кнопка «Из шаблона» в прототипе. */
const TEMPLATES: Array<{ key: string; title: string; questions: Array<{ text: string; type: QuestionType }> }> = [
  {
    key: 'pulse',
    title: 'Pulse: удовлетворённость',
    questions: [
      { text: 'Насколько вы удовлетворены работой команды в этом квартале?', type: 'LIKERT_5' },
      { text: 'Успеваете ли вы выполнять задачи в рабочее время?', type: 'LIKERT_5' },
      { text: 'Что мешает вам работать эффективнее?', type: 'FREE_TEXT' }
    ]
  },
  {
    key: 'onboarding',
    title: 'Адаптация новичка',
    questions: [
      { text: 'Понятны ли вам задачи первых недель?', type: 'LIKERT_5' },
      { text: 'Хватает ли вам поддержки наставника?', type: 'LIKERT_5' },
      { text: 'Чего не хватило в первые недели?', type: 'FREE_TEXT' }
    ]
  },
  {
    key: 'exit',
    title: 'Выходное интервью',
    questions: [
      { text: 'Что повлияло на решение уйти?', type: 'FREE_TEXT' },
      { text: 'Порекомендовали бы вы компанию как место работы?', type: 'LIKERT_5' },
      { text: 'Что стоит изменить в процессе выхода?', type: 'FREE_TEXT' }
    ]
  }
];

interface DraftQuestion {
  key: string;
  text: string;
  type: QuestionType;
  required: boolean;
}

let questionSeq = 0;

function createQuestion(type: QuestionType): DraftQuestion {
  questionSeq += 1;
  return { key: 'q' + questionSeq, text: '', type, required: true };
}

/** Идентификатор из хеша: {@code #/surveys/builder/12} — правка черновика. */
function editingIdFromHash(): number | null {
  const match = window.location.hash.match(/#\/surveys\/builder\/(\d+)/);
  return match ? Number(match[1]) : null;
}

export function SurveyBuilderPage() {
  const editingId = editingIdFromHash();

  const [surveyId, setSurveyId] = useState<number | null>(editingId);
  const [status, setStatus] = useState('DRAFT');
  const [statusTitle, setStatusTitle] = useState('Черновик');
  const [title, setTitle] = useState('');
  const [surveyType, setSurveyType] = useState('PULSE');
  const [scope, setScope] = useState('COMPANY');
  const [department, setDepartment] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [anonymous, setAnonymous] = useState(false);
  const [questions, setQuestions] = useState<DraftQuestion[]>([createQuestion('LIKERT_5')]);
  const [previewIndex, setPreviewIndex] = useState(0);
  const [loading, setLoading] = useState(editingId !== null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const readOnly = status !== 'DRAFT';

  /** Перенос серверного ответа в состояние формы. */
  function apply(detail: SurveyDetail) {
    setSurveyId(detail.id);
    setStatus(detail.status);
    setStatusTitle(detail.statusTitle);
    setTitle(detail.title);
    setSurveyType(detail.surveyType);
    setScope(detail.targetScope);
    setDepartment(detail.targetDepartment ?? '');
    setStartDate(detail.startDate ?? '');
    setEndDate(detail.endDate ?? '');
    setAnonymous(detail.anonymous);
    setQuestions(
      detail.questions.length === 0
        ? [createQuestion('LIKERT_5')]
        : detail.questions.map((question) => ({
            key: 'q' + question.id,
            text: question.text,
            type: question.type,
            required: question.required
          }))
    );
    setPreviewIndex(0);
  }

  useEffect(() => {
    if (editingId === null) {
      return;
    }
    contentApi
      .survey(editingId)
      .then(apply)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
    // apply не в зависимостях: он пересобирает состояние из того же ответа.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editingId]);

  /** Заполненные вопросы: пустые строки конструктора на сервер не уходят. */
  const filled = useMemo(
    () => questions.filter((question) => question.text.trim().length > 0),
    [questions]
  );

  const freeTextCount = filled.filter((question) => question.type === 'FREE_TEXT').length;
  const preview = questions[previewIndex] ?? questions[0] ?? null;

  /** Проверки перед запуском — те же, что в прототипе, но по фактическим данным. */
  const checks = useMemo(() => {
    const list: Array<{ ok: boolean; text: string }> = [];
    list.push({
      ok: title.trim().length > 0 && startDate !== '' && endDate !== '',
      text: title.trim().length > 0 && startDate !== '' && endDate !== ''
        ? 'Название и даты заполнены'
        : 'Не заполнены название или даты'
    });
    list.push({
      ok: filled.length > 0,
      text: filled.length > 0 ? `${filled.length} вопросов, у каждого есть тип` : 'Нет ни одного вопроса'
    });
    if (scope === 'DEPARTMENT') {
      list.push({
        ok: department.trim().length > 0,
        text: department.trim().length > 0 ? `Охват — отдел «${department.trim()}»` : 'Не выбран отдел охвата'
      });
    } else {
      list.push({ ok: true, text: 'Охват: ' + (SCOPES.find((s) => s.value === scope)?.title ?? scope) });
    }
    list.push(
      freeTextCount === 0
        ? { ok: true, text: 'Все вопросы закрытые — сводка считается автоматически' }
        : { ok: false, text: `${freeTextCount} открытых вопросов → ответы нужно читать вручную` }
    );
    list.push(
      anonymous
        ? { ok: false, text: 'Анонимный опрос: фильтр по отделу в теплокарте недоступен (FR-024)' }
        : { ok: true, text: 'Неанонимный опрос: доступна аналитика по отделам' }
    );
    return list;
  }, [anonymous, department, filled.length, freeTextCount, scope, startDate, title, endDate]);

  function buildPayload(): SurveySaveRequest {
    return {
      title: title.trim(),
      surveyType,
      targetScope: scope,
      targetDepartment: scope === 'DEPARTMENT' ? department.trim() : null,
      targetManagerId: null,
      targetUserId: null,
      startDate,
      endDate,
      anonymous,
      questions: filled.map((question) => ({
        text: question.text.trim(),
        type: question.type,
        required: question.required
      }))
    };
  }

  /** Сохранение черновика. Возвращает id или null, если сохранить не удалось. */
  async function saveDraft(): Promise<number | null> {
    setError(null);
    setNotice(null);

    if (title.trim().length === 0) {
      setError('Укажите название опроса');
      return null;
    }
    if (startDate === '' || endDate === '') {
      setError('Укажите даты начала и окончания');
      return null;
    }
    if (filled.length === 0) {
      setError('Добавьте хотя бы один вопрос');
      return null;
    }

    const creating = surveyId === null;
    setBusy(true);
    try {
      const saved = creating
        ? await contentApi.createSurvey(buildPayload())
        : await contentApi.updateSurvey(surveyId, buildPayload());
      apply(saved);
      setNotice(creating ? 'Черновик создан: ' + saved.title : 'Черновик сохранён');
      return saved.id;
    } catch (e) {
      setError((e as Error).message);
      return null;
    } finally {
      setBusy(false);
    }
  }

  /** Публикация: сначала сохраняем, затем переводим черновик в активный. */
  async function publish() {
    const id = await saveDraft();
    if (id === null) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const published = await contentApi.publishSurvey(id);
      apply(published);
      setNotice(
        'Опрос опубликован: ' + published.title + ' · вопросов: ' + published.questionCount
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function patchQuestion(key: string, patch: Partial<DraftQuestion>) {
    setQuestions((prev) =>
      prev.map((question) => (question.key === key ? { ...question, ...patch } : question))
    );
  }

  function moveQuestion(key: string, delta: number) {
    setQuestions((prev) => {
      const index = prev.findIndex((question) => question.key === key);
      const target = index + delta;
      if (index < 0 || target < 0 || target >= prev.length) {
        return prev;
      }
      const next = [...prev];
      const [moved] = next.splice(index, 1);
      next.splice(target, 0, moved);
      return next;
    });
  }

  function applyTemplate(key: string) {
    const template = TEMPLATES.find((item) => item.key === key);
    if (!template) {
      return;
    }
    setQuestions((prev) => [
      ...prev.filter((question) => question.text.trim().length > 0),
      ...template.questions.map((question) => ({
        ...createQuestion(question.type),
        text: question.text
      }))
    ]);
    setNotice('Добавлен шаблон: ' + template.title);
  }

  return (
    <Shell
      active="surveyBuilder"
      crumbs={['Опросы', 'Конструктор опроса']}
      actions={
        <>
          <a className="btn btn-sm btn-ghost" href="#/surveys">К списку опросов</a>
          <button className="btn btn-sm btn-outline" type="button" onClick={saveDraft} disabled={busy || readOnly}>
            {busy ? 'Сохранение…' : 'Сохранить черновик'}
          </button>
          <button className="btn btn-sm btn-primary" type="button" onClick={publish} disabled={busy || readOnly}>
            Активировать
          </button>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Опросы · создание</div>
          <h1 className="t-hero">Конструктор опроса</h1>
          <div className="t-sm muted mt-8">
            {readOnly
              ? 'Опубликованный опрос не редактируется — вопросы связаны с ответами'
              : `Черновик · ${filled.length} вопросов`}
            {surveyId !== null && ` · id ${surveyId}`}
            {' · '}
            <span className={'badge ' + (status === 'ACTIVE' ? 'badge-ok' : 'badge-neutral')}>
              {statusTitle}
            </span>
          </div>
        </div>
        <div className="spacer" />
        {anonymous && <span className="badge badge-ok badge-lg">анонимно</span>}
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}
      {loading && <div className="card card-bd muted">Загрузка черновика…</div>}

      {!loading && (
        <div className="grid" style={{ gridTemplateColumns: 'minmax(0, 1.7fr) minmax(0, 1fr)' }}>
          <div className="col gap-16">
            <div className="card">
              <div className="card-hd"><h2 className="t-h3">Параметры</h2></div>
              <div className="card-bd">
                <div className="col gap-14">
                  <div className="field">
                    <span className="field-label">Название <span className="field-req">*</span></span>
                    <input
                      className="input"
                      placeholder="Pulse-опрос: удовлетворённость командой Q4"
                      value={title}
                      disabled={readOnly}
                      onChange={(event) => setTitle(event.target.value)}
                    />
                  </div>

                  <div className="grid g2">
                    <div className="field">
                      <span className="field-label">Тип</span>
                      <select
                        className="select"
                        value={surveyType}
                        disabled={readOnly}
                        onChange={(event) => setSurveyType(event.target.value)}
                      >
                        {SURVEY_TYPES.map((type) => (
                          <option key={type.value} value={type.value}>{type.title} ({type.value})</option>
                        ))}
                      </select>
                    </div>
                    <div className="field">
                      <span className="field-label">Охват</span>
                      <select
                        className="select"
                        value={scope}
                        disabled={readOnly}
                        onChange={(event) => setScope(event.target.value)}
                      >
                        {SCOPES.map((item) => (
                          <option key={item.value} value={item.value}>{item.title} ({item.value})</option>
                        ))}
                      </select>
                    </div>
                    <div className="field">
                      <span className="field-label">Начало <span className="field-req">*</span></span>
                      <input
                        className="input"
                        type="date"
                        value={startDate}
                        disabled={readOnly}
                        onChange={(event) => setStartDate(event.target.value)}
                      />
                    </div>
                    <div className="field">
                      <span className="field-label">Окончание <span className="field-req">*</span></span>
                      <input
                        className="input"
                        type="date"
                        value={endDate}
                        disabled={readOnly}
                        onChange={(event) => setEndDate(event.target.value)}
                      />
                    </div>
                  </div>

                  {scope === 'DEPARTMENT' && (
                    <div className="field">
                      <span className="field-label">Подразделение <span className="field-req">*</span></span>
                      <input
                        className="input"
                        placeholder="Engineering"
                        value={department}
                        disabled={readOnly}
                        onChange={(event) => setDepartment(event.target.value)}
                      />
                      <span className="field-hint">Для охвата «Команда» и «Сотрудник» нужны id руководителя или сотрудника — их подставляет серверная форма.</span>
                    </div>
                  )}

                  <div
                    className="row"
                    style={{ padding: '11px 13px', border: '1px solid var(--line)', borderRadius: 10 }}
                  >
                    <div>
                      <div className="t-sm strong">Анонимный опрос</div>
                      <div className="t-xs muted">ответы сохраняются с user_id = NULL (BR-011)</div>
                    </div>
                    <div className="spacer" />
                    <button
                      className="switch"
                      type="button"
                      role="switch"
                      aria-checked={anonymous}
                      aria-label="Анонимный опрос"
                      disabled={readOnly}
                      onClick={() => setAnonymous((value) => !value)}
                    />
                  </div>

                  <div className="callout callout-warn">
                    <div>
                      При анонимности нельзя будет отфильтровать ответы по отделу в теплокарте —
                      эта аналитика строится только по неанонимным ответам (FR-024).
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="card">
              <div className="card-hd">
                <h2 className="t-h3">Вопросы</h2>
                <div className="spacer" />
                <span className="pill-count">{filled.length}</span>
              </div>
              <div className="card-bd">
                <div className="col gap-10">
                  {questions.length === 0 && (
                    <div className="t-sm muted">Вопросов нет — добавьте хотя бы один</div>
                  )}

                  {questions.map((question, index) => (
                    <div className="q-block" key={question.key}>
                      <div className="q-head">
                        <span className="q-num">{index + 1}</span>
                        <div style={{ flex: 1 }}>
                          <input
                            className="input"
                            placeholder="Текст вопроса"
                            value={question.text}
                            disabled={readOnly}
                            onChange={(event) => patchQuestion(question.key, { text: event.target.value })}
                          />
                          <div className="row gap-6 mt-8" style={{ flexWrap: 'wrap' }}>
                            <select
                              className="select input-sm"
                              value={question.type}
                              disabled={readOnly}
                              onChange={(event) =>
                                patchQuestion(question.key, { type: event.target.value as QuestionType })
                              }
                            >
                              {QUESTION_TYPES.map((type) => (
                                <option key={type.value} value={type.value}>{type.title}</option>
                              ))}
                            </select>
                            <button
                              className={'chip' + (question.required ? ' is-on' : '')}
                              type="button"
                              disabled={readOnly}
                              onClick={() => patchQuestion(question.key, { required: !question.required })}
                            >
                              {question.required ? 'обязательный' : 'необязательный'}
                            </button>
                            <button
                              className="btn btn-sm btn-ghost"
                              type="button"
                              title="Показать в предпросмотре"
                              onClick={() => setPreviewIndex(index)}
                            >
                              предпросмотр
                            </button>
                          </div>
                        </div>
                        <div className="q-tools">
                          <button
                            className="btn btn-sm btn-ghost btn-icon"
                            type="button"
                            title="Выше"
                            disabled={readOnly || index === 0}
                            onClick={() => moveQuestion(question.key, -1)}
                          >
                            ↑
                          </button>
                          <button
                            className="btn btn-sm btn-ghost btn-icon"
                            type="button"
                            title="Ниже"
                            disabled={readOnly || index === questions.length - 1}
                            onClick={() => moveQuestion(question.key, 1)}
                          >
                            ↓
                          </button>
                          <button
                            className="btn btn-sm btn-ghost btn-icon"
                            type="button"
                            title="Удалить"
                            disabled={readOnly || questions.length === 1}
                            onClick={() => setQuestions((prev) => prev.filter((item) => item.key !== question.key))}
                          >
                            ✕
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}

                  <div className="row gap-8" style={{ flexWrap: 'wrap' }}>
                    <button
                      className="btn btn-outline"
                      type="button"
                      disabled={readOnly}
                      onClick={() => setQuestions((prev) => [...prev, createQuestion('LIKERT_5')])}
                    >
                      + Шкала 1–5
                    </button>
                    <button
                      className="btn btn-outline"
                      type="button"
                      disabled={readOnly}
                      onClick={() => setQuestions((prev) => [...prev, createQuestion('FREE_TEXT')])}
                    >
                      + Свободный ответ
                    </button>
                    <div className="spacer" />
                    <select
                      className="select input-sm"
                      value=""
                      disabled={readOnly}
                      onChange={(event) => {
                        applyTemplate(event.target.value);
                        event.target.value = '';
                      }}
                    >
                      <option value="">Из шаблона…</option>
                      {TEMPLATES.map((template) => (
                        <option key={template.key} value={template.key}>{template.title}</option>
                      ))}
                    </select>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="col gap-16">
            <div className="card">
              <div className="card-hd">
                <h2 className="t-h3">Как увидит сотрудник</h2>
                <div className="spacer" />
                <span className="t-xs muted">
                  вопрос {questions.length === 0 ? 0 : previewIndex + 1} из {questions.length}
                </span>
              </div>
              <div className="card-bd">
                {preview === null || preview.text.trim().length === 0 ? (
                  <div className="t-sm muted">Заполните первый вопрос — здесь появится предпросмотр.</div>
                ) : (
                  <div className="q-block" style={{ background: 'var(--surface-2)' }}>
                    <div className="q-text mb-12">{preview.text}</div>
                    {preview.type === 'LIKERT_5' ? (
                      <div className="likert">
                        {LIKERT_LABELS.map((label, index) => (
                          <label key={label} className={index === 3 ? 'is-on' : ''}>
                            <span className="n">{index + 1}</span>
                            {label}
                          </label>
                        ))}
                      </div>
                    ) : (
                      <textarea className="input" rows={3} placeholder="Свободный ответ" readOnly />
                    )}
                    <div className="row gap-8 mt-12">
                      <span className={'badge ' + (anonymous ? 'badge-ok' : 'badge-neutral')}>
                        {anonymous ? 'анонимно' : 'с именем'}
                      </span>
                      <span className="badge badge-neutral">
                        {preview.required ? 'обязательный' : 'можно пропустить'}
                      </span>
                      <div className="spacer" />
                      <button className="btn btn-sm btn-primary" type="button" disabled>Далее</button>
                    </div>
                  </div>
                )}
              </div>
            </div>

            <div className="card">
              <div className="card-hd"><h2 className="t-h3">Проверка перед запуском</h2></div>
              <div className="card-bd">
                <div className="col gap-10">
                  {checks.map((check) => (
                    <div className="row t-sm" key={check.text}>
                      <span style={{ color: check.ok ? 'var(--ok)' : 'var(--warn)', width: 14 }}>
                        {check.ok ? '✓' : '!'}
                      </span>
                      <span>{check.text}</span>
                    </div>
                  ))}
                </div>
                <div className="callout mt-12">
                  <div>
                    Признак <code>satisfaction_norm</code> берётся из среднего LIKERT за 90 дней.
                    Если опрос не запускать, ML подставит значение по умолчанию 0,65 (BR-018) — и
                    прогноз станет менее точным.
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </Shell>
  );
}
