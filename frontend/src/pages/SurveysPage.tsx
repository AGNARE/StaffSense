import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { SurveyForm, SurveyItem, SurveyResults } from '../types';
import { formatDate, formatNumber } from '../format';

const STATUS_BADGE: Record<string, string> = {
  DRAFT: 'badge-neutral',
  ACTIVE: 'badge-ok',
  CLOSED: 'badge-outline'
};

/**
 * Опросы: список доступных, прохождение и агрегированные результаты.
 *
 * Анонимность — часть контракта: результаты приходят только сводкой
 * (среднее и распределение), персональных ответов в API нет.
 */
export function SurveysPage() {
  const [items, setItems] = useState<SurveyItem[]>([]);
  const [form, setForm] = useState<SurveyForm | null>(null);
  const [results, setResults] = useState<SurveyResults | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    setError(null);
    api.surveys()
      .then(setItems)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const openForm = (id: number) => {
    setNotice(null);
    setError(null);
    api.survey(id).then(setForm).catch((e: Error) => setError(e.message));
  };

  const openResults = (id: number) => {
    setNotice(null);
    setError(null);
    api.surveyResults(id).then(setResults).catch((e: Error) => setError(e.message));
  };

  return (
    <Shell
      active="surveys"
      crumbs={['Люди', 'Опросы']}
      actions={<button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>}
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Люди · вовлечённость</div>
          <h1 className="t-hero">Опросы</h1>
          <div className="t-sm muted mt-8">
            Прохождение — по адресации, результаты — только сводкой
          </div>
        </div>
        <div className="spacer" />
        <span className="badge badge-neutral">{items.length} опросов</span>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}

      <div className="card card-flush mt-16">
        <div className="table-wrap">
          <table className="table table-wide">
            <thead>
              <tr>
                <th>Опрос</th>
                <th>Период</th>
                <th>Вопросов</th>
                <th>Ответов</th>
                <th>Статус</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={6} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>}
              {!loading && items.length === 0 && (
                <tr><td colSpan={6} className="muted" style={{ padding: 24 }}>Опросов нет</td></tr>
              )}
              {items.map((item) => (
                <tr key={item.id}>
                  <td>
                    <div className="t-main">{item.title}</div>
                    <div className="sub">
                      {item.anonymous ? 'анонимный' : 'именной'}
                      {item.alreadyTaken ? ' · вы уже прошли' : ''}
                    </div>
                  </td>
                  <td className="muted t-xs">
                    {formatDate(item.startDate)} — {formatDate(item.endDate)}
                  </td>
                  <td className="num">{item.questionCount}</td>
                  <td className="num">{item.responseCount}</td>
                  <td>
                    <span className={'badge ' + (STATUS_BADGE[item.status] ?? 'badge-neutral')}>
                      {item.status}
                    </span>
                  </td>
                  <td className="t-actions">
                    <div className="row" style={{ gap: 6, justifyContent: 'flex-end' }}>
                      <button
                        className="btn btn-sm btn-outline"
                        type="button"
                        disabled={!item.canTake}
                        title={item.alreadyTaken ? 'Вы уже прошли этот опрос' : undefined}
                        onClick={() => openForm(item.id)}
                      >
                        {item.alreadyTaken ? 'Пройден' : 'Пройти'}
                      </button>
                      <button className="btn btn-sm btn-ghost" type="button" onClick={() => openResults(item.id)}>
                        Результаты
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div
        className={'overlay' + (form || results ? ' is-open' : '')}
        onClick={() => { setForm(null); setResults(null); }}
      />
      <aside className={'drawer' + (form || results ? ' is-open' : '')} aria-hidden={!(form || results)}>
        {form && (
          <SurveyFormDrawer
            form={form}
            onClose={() => setForm(null)}
            onDone={() => { setForm(null); setNotice('Спасибо, ответы отправлены'); load(); }}
            onError={setError}
          />
        )}
        {results && <SurveyResultsDrawer results={results} onClose={() => setResults(null)} />}
      </aside>
    </Shell>
  );
}

function SurveyFormDrawer(props: {
  form: SurveyForm;
  onClose: () => void;
  onDone: () => void;
  onError: (message: string) => void;
}) {
  const { form } = props;
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState(false);

  const set = (questionId: number, value: string) =>
    setAnswers((prev) => ({ ...prev, [questionId]: value }));

  return (
    <>
      <div className="drawer-hd">
        <div>
          <h2 className="t-h2">{form.title}</h2>
          <div className="t-xs muted mt-4">
            {form.anonymous ? 'Анонимный опрос' : 'Именной опрос'}
            {form.alreadyTaken ? ' · уже пройден' : ''}
          </div>
        </div>
        <div className="spacer" />
        <button className="btn btn-sm btn-ghost btn-icon" type="button" onClick={props.onClose}>✕</button>
      </div>

      <div className="drawer-bd">
        {form.questions.map((question, index) => (
          <div key={question.id} style={{ marginBottom: 18 }}>
            <div style={{ fontWeight: 600, marginBottom: 6 }}>
              {index + 1}. {question.text}
            </div>
            {question.type === 'LIKERT_5' ? (
              <div className="row" style={{ gap: 6 }}>
                {[1, 2, 3, 4, 5].map((value) => (
                  <button
                    key={value}
                    type="button"
                    className={'btn btn-sm ' + (answers[question.id] === String(value) ? 'btn-primary' : 'btn-outline')}
                    onClick={() => set(question.id, String(value))}
                  >
                    {value}
                  </button>
                ))}
              </div>
            ) : (
              <textarea
                className="input"
                rows={3}
                style={{ width: '100%' }}
                value={answers[question.id] ?? ''}
                onChange={(event) => set(question.id, event.target.value)}
              />
            )}
          </div>
        ))}
      </div>

      <div className="drawer-ft">
        <button
          className="btn btn-sm btn-primary"
          type="button"
          disabled={busy || !form.canTake}
          onClick={() => {
            setBusy(true);
            api.submitSurvey(form.id, answers)
              .then(props.onDone)
              .catch((e: Error) => props.onError(e.message))
              .finally(() => setBusy(false));
          }}
        >
          {busy ? 'Отправка…' : 'Отправить ответы'}
        </button>
        <button className="btn btn-sm btn-ghost" type="button" onClick={props.onClose}>Отмена</button>
      </div>
    </>
  );
}

function SurveyResultsDrawer(props: { results: SurveyResults; onClose: () => void }) {
  const { results } = props;
  return (
    <>
      <div className="drawer-hd">
        <div>
          <h2 className="t-h2">{results.title}</h2>
          <div className="t-xs muted mt-4">
            {results.totalResponses} ответов · {results.anonymous ? 'анонимный' : 'именной'}
          </div>
        </div>
        <div className="spacer" />
        <button className="btn btn-sm btn-ghost btn-icon" type="button" onClick={props.onClose}>✕</button>
      </div>

      <div className="drawer-bd">
        {results.questions.length === 0 && <div className="muted">Ответов пока нет</div>}

        {results.questions.map((question) => (
          <div key={question.questionId} style={{ marginBottom: 18 }}>
            <div style={{ fontWeight: 600 }}>{question.text}</div>
            <div className="t-xs muted mt-4">
              ответов {question.responseCount} · среднее {formatNumber(question.average, 2)}
              {' · '}от {question.min} до {question.max}
            </div>
            <div className="row" style={{ gap: 6, marginTop: 6 }}>
              {(question.distribution ?? []).map((count, index) => {
                const max = Math.max(1, ...question.distribution);
                return (
                  <div key={index} style={{ flex: 1, textAlign: 'center' }}>
                    <div
                      style={{
                        height: 48, display: 'flex', alignItems: 'flex-end', justifyContent: 'center'
                      }}
                    >
                      <div
                        style={{
                          width: '70%',
                          height: Math.max(2, (count / max) * 48) + 'px',
                          background: 'var(--brand)', borderRadius: 3
                        }}
                      />
                    </div>
                    <div className="t-xs muted">{count}</div>
                    <div className="t-xs muted">{index + 1}</div>
                  </div>
                );
              })}
            </div>
          </div>
        ))}

        {results.freeTexts.length > 0 && (
          <>
            <h3 className="t-h3 mt-16">Свободные ответы</h3>
            {results.freeTexts.map((item, index) => (
              <div key={index} className="card card-bd" style={{ marginBottom: 8 }}>
                <div className="t-xs muted">{item.question}</div>
                <div>{item.answer}</div>
              </div>
            ))}
          </>
        )}
      </div>
    </>
  );
}
