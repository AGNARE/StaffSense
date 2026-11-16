import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { Modal } from '../components/Modal';
import { api } from '../api';
import type {
  Checklist, OnboardingArticle, OnboardingArticleItem, OnboardingSection
} from '../types';
import { formatShortDateTime } from '../format';

/**
 * Портал онбординга: разделы, статьи и чек-лист новичка.
 *
 * Черновики статей не видны рядовому сотруднику — сервер отвечает 404 на
 * прямую ссылку, поэтому отдельной проверки в клиенте нет.
 */
export function OnboardingPage() {
  const [sections, setSections] = useState<OnboardingSection[]>([]);
  const [articles, setArticles] = useState<OnboardingArticleItem[]>([]);
  const [article, setArticle] = useState<OnboardingArticle | null>(null);
  const [checklist, setChecklist] = useState<Checklist | null>(null);
  const [section, setSection] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([api.onboardingSections(), api.onboardingChecklist()])
      .then(([sectionList, progress]) => {
        setSections(sectionList);
        setChecklist(progress);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    setLoading(true);
    api.onboardingArticles(section || undefined)
      .then(setArticles)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [section]);

  const openArticle = (id: number) => {
    setError(null);
    api.onboardingArticle(id).then(setArticle).catch((e: Error) => setError(e.message));
  };

  const toggle = (articleId: number) => {
    api.toggleChecklist(articleId)
      .then(setChecklist)
      .catch((e: Error) => setError(e.message));
  };

  return (
    <Shell
      active="onboarding"
      crumbs={['Люди', 'Портал онбординга']}
      actions={<button className="btn btn-sm btn-outline" type="button" onClick={() => setSection(section)}>Обновить</button>}
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Модуль B · портал новичка</div>
          <h1 className="t-hero">Онбординг</h1>
          <div className="t-sm muted mt-8">
            Справочник компании и чек-лист первых недель
          </div>
        </div>
        <div className="spacer" />
        {checklist && (
          <div style={{ minWidth: 200 }}>
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <span className="t-xs muted">чек-лист</span>
              <strong>{checklist.completed} / {checklist.total}</strong>
            </div>
            <div style={{ height: 6, borderRadius: 4, background: 'var(--surface-inset)', overflow: 'hidden', marginTop: 4 }}>
              <div style={{ width: checklist.percent + '%', height: '100%', background: 'var(--ok)' }} />
            </div>
          </div>
        )}
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}

      <div className="grid g-2-1 mt-16">
        <div className="card card-flush">
          <div className="table-toolbar">
            <div className="seg">
              <button type="button" className={!section ? 'active' : ''} onClick={() => setSection('')}>
                Все
              </button>
              {sections.map((item) => (
                <button
                  key={item.code}
                  type="button"
                  className={section === item.code ? 'active' : ''}
                  onClick={() => setSection(item.code)}
                >
                  {item.title} <span className="muted">({item.articleCount})</span>
                </button>
              ))}
            </div>
          </div>

          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Статья</th>
                  <th>Раздел</th>
                  <th>Обновлена</th>
                </tr>
              </thead>
              <tbody>
                {loading && <tr><td colSpan={3} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>}
                {!loading && articles.length === 0 && (
                  <tr><td colSpan={3} className="muted" style={{ padding: 24 }}>Статей нет</td></tr>
                )}
                {articles.map((item) => (
                  <tr
                    key={item.id}
                    className="table-click"
                    onClick={() => openArticle(item.id)}
                  >
                    <td><span className="t-main">{item.title}</span></td>
                    <td className="muted t-xs">{item.sectionTitle}</td>
                    <td className="muted t-xs">{formatShortDateTime(item.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="card card-flush">
          <div className="card-hd"><h2 className="t-h3">Чек-лист новичка</h2></div>
          <div className="card-bd" style={{ display: 'grid', gap: 8 }}>
            {(checklist?.items ?? []).length === 0 && <span className="muted">Пунктов нет</span>}
            {checklist?.items.map((item) => (
              <label key={item.articleId} className="row" style={{ gap: 8, alignItems: 'center', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={item.completed}
                  onChange={() => toggle(item.articleId)}
                />
                <span style={{ textDecoration: item.completed ? 'line-through' : 'none' }}>
                  {item.title}
                </span>
              </label>
            ))}
          </div>
        </div>
      </div>

      {article && (
        <Modal
          title={article.title}
          subtitle={
            <>
              <div>{article.sectionTitle}</div>
              <div className="mt-4">
                обновлено {formatShortDateTime(article.updatedAt)}
                {article.updatedBy ? ' · ' + article.updatedBy : ''}
              </div>
            </>
          }
          onClose={() => setArticle(null)}
        >
          <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>{article.content}</div>
        </Modal>
      )}
    </Shell>
  );
}
