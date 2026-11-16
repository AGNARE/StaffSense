import { useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';
import { contentApi } from '../content-api';
import type { ArticleItem, PortalOverview } from '../content-api';
import { formatShortDateTime } from '../format';

/**
 * Управление порталом (прототип: экран {@code pt-manage}).
 *
 * <p>Экран ведёт статьи внутреннего портала: создать, изменить, опубликовать.
 * Порядок внутри раздела задаётся вручную, черновик не виден сотрудникам —
 * поэтому список открывается только HR и администратору.
 *
 * <p>Маршрут: {@code #/portal/manage}.
 */

const SECTIONS: Array<{ value: string; title: string }> = [
  { value: 'ABOUT', title: 'О компании' },
  { value: 'STRUCTURE', title: 'Структура компании' },
  { value: 'RESPONSIBILITIES', title: 'Должностные инструкции' },
  { value: 'POLICIES', title: 'Регламенты и политики' },
  { value: 'FAQ', title: 'Частые вопросы' },
  { value: 'CHECKLIST', title: 'Чек-лист новичка' }
];

interface EditorState {
  id: number | null;
  section: string;
  title: string;
  content: string;
  orderIndex: string;
}

const EMPTY_EDITOR: EditorState = {
  id: null,
  section: 'ABOUT',
  title: '',
  content: '',
  orderIndex: '0'
};

export function PortalManagePage() {
  const [overview, setOverview] = useState<PortalOverview | null>(null);
  const [section, setSection] = useState('');
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  function load() {
    setLoading(true);
    setError(null);
    contentApi
      .articles()
      .then(setOverview)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  const articles = useMemo(() => {
    const all = overview?.articles ?? [];
    return section === '' ? all : all.filter((article) => article.section === section);
  }, [overview, section]);

  function openCreate() {
    setError(null);
    setNotice(null);
    setEditor({ ...EMPTY_EDITOR, orderIndex: String(nextOrder(EMPTY_EDITOR.section)) });
  }

  function openEdit(article: ArticleItem) {
    setError(null);
    setNotice(null);
    setEditor({
      id: article.id,
      section: article.section,
      title: article.title,
      content: article.content ?? '',
      orderIndex: String(article.orderIndex ?? 0)
    });
  }

  /** Следующий порядковый номер в разделе — «порядок задаётся вручную». */
  function nextOrder(targetSection: string): number {
    const inSection = (overview?.articles ?? []).filter((article) => article.section === targetSection);
    return inSection.reduce((max, article) => Math.max(max, article.orderIndex ?? 0), 0) + 1;
  }

  async function save() {
    if (editor === null) {
      return;
    }
    if (editor.title.trim().length === 0) {
      setError('Укажите заголовок статьи');
      return;
    }

    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const order = editor.orderIndex.trim() === '' ? null : Number(editor.orderIndex);
      const body = {
        section: editor.section,
        title: editor.title.trim(),
        content: editor.content,
        orderIndex: order === null || Number.isNaN(order) ? null : order
      };
      const saved = editor.id === null
        ? await contentApi.createArticle(body)
        : await contentApi.updateArticle(editor.id, body);

      setEditor(null);
      setNotice(
        (editor.id === null ? 'Статья создана: ' : 'Статья сохранена: ') +
          saved.title +
          (saved.published ? ' · опубликована' : ' · черновик')
      );
      load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function publish(article: ArticleItem) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const published = await contentApi.publishArticle(article.id);
      setNotice('Статья опубликована: ' + published.title);
      load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const drafts = overview?.draftArticles ?? 0;

  return (
    <Shell
      active="onboarding"
      crumbs={['Администрирование', 'Управление порталом']}
      actions={
        <>
          <a className="btn btn-sm btn-ghost" href="#/onboarding">Открыть портал</a>
          <button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>
          <button className="btn btn-sm btn-primary" type="button" onClick={openCreate} disabled={busy}>
            + Статья
          </button>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Портал · HR и ADMIN</div>
          <h1 className="t-hero">Управление порталом</h1>
          <div className="t-sm muted mt-8">
            {overview === null
              ? 'Загрузка…'
              : `${overview.totalArticles} статей · ${drafts} черновиков · порядок внутри раздела задаётся вручную`}
          </div>
        </div>
        <div className="spacer" />
        <div className="row gap-8">
          <span className="badge badge-neutral">{overview?.totalArticles ?? 0} всего</span>
          {drafts > 0 && <span className="badge badge-warn">{drafts} черновиков</span>}
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}

      {editor !== null && (
        <div className="card mt-16">
          <div className="card-hd">
            <h2 className="t-h3">{editor.id === null ? 'Новая статья' : 'Правка статьи'}</h2>
            <div className="spacer" />
            <button className="btn btn-sm btn-ghost" type="button" onClick={() => setEditor(null)}>Отмена</button>
          </div>
          <div className="card-bd">
            <div className="grid g2 gap-12">
              <div className="field">
                <span className="field-label">Раздел <span className="field-req">*</span></span>
                <select
                  className="select"
                  value={editor.section}
                  onChange={(event) => setEditor({ ...editor, section: event.target.value })}
                >
                  {SECTIONS.map((item) => (
                    <option key={item.value} value={item.value}>{item.title} ({item.value})</option>
                  ))}
                </select>
              </div>
              <div className="field">
                <span className="field-label">Порядок в разделе</span>
                <input
                  className="input"
                  type="number"
                  min={0}
                  value={editor.orderIndex}
                  onChange={(event) => setEditor({ ...editor, orderIndex: event.target.value })}
                />
              </div>
            </div>
            <div className="field mt-12">
              <span className="field-label">Заголовок <span className="field-req">*</span></span>
              <input
                className="input"
                placeholder="Как проходит код-ревью"
                value={editor.title}
                onChange={(event) => setEditor({ ...editor, title: event.target.value })}
              />
            </div>
            <div className="field mt-12">
              <span className="field-label">Текст статьи</span>
              <textarea
                className="input"
                rows={6}
                placeholder="Текст, который увидит сотрудник"
                value={editor.content}
                onChange={(event) => setEditor({ ...editor, content: event.target.value })}
              />
              <span className="field-hint">
                Публикация — отдельное действие: сохраните черновик, проверьте текст и опубликуйте.
              </span>
            </div>
            <div className="row gap-8 mt-12">
              <button className="btn btn-primary" type="button" onClick={save} disabled={busy}>
                {busy ? 'Сохранение…' : 'Сохранить'}
              </button>
              <button className="btn btn-ghost" type="button" onClick={() => setEditor(null)}>Отмена</button>
            </div>
          </div>
        </div>
      )}

      <div className="grid g-3-2 mt-16">
        <div className="card card-flush">
          <div className="table-toolbar">
            <div className="seg">
              <button type="button" className={section === '' ? 'is-on' : ''} onClick={() => setSection('')}>
                Все <span className="muted">({overview?.totalArticles ?? 0})</span>
              </button>
              {SECTIONS.map((item) => (
                <button
                  key={item.value}
                  type="button"
                  className={section === item.value ? 'is-on' : ''}
                  onClick={() => setSection(item.value)}
                >
                  {item.title}
                </button>
              ))}
            </div>
          </div>

          <div className="table-wrap">
            <table className="table table-wide">
              <thead>
                <tr>
                  <th>Заголовок</th>
                  <th>Раздел</th>
                  <th>Порядок</th>
                  <th>Статус</th>
                  <th>Обновлена</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {loading && <tr><td colSpan={6} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>}
                {!loading && articles.length === 0 && (
                  <tr><td colSpan={6} className="muted" style={{ padding: 24 }}>Статей нет</td></tr>
                )}
                {articles.map((article) => (
                  <tr key={article.id}>
                    <td><span className="t-main">{article.title}</span></td>
                    <td className="t-xs">{article.sectionTitle}</td>
                    <td className="t-num num">{article.orderIndex ?? 0}</td>
                    <td>
                      <span className={'badge ' + (article.published ? 'badge-ok' : 'badge-warn')}>
                        {article.statusTitle}
                      </span>
                    </td>
                    <td className="t-xs muted num">
                      {formatShortDateTime(article.updatedAt)}
                      {article.updatedBy && ' · ' + article.updatedBy}
                    </td>
                    <td className="t-actions">
                      <button className="btn btn-sm btn-ghost" type="button" onClick={() => openEdit(article)}>
                        Изменить
                      </button>
                      {!article.published && (
                        <button
                          className="btn btn-sm btn-outline"
                          type="button"
                          disabled={busy}
                          onClick={() => publish(article)}
                        >
                          Опубликовать
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <span>{articles.length} из {overview?.totalArticles ?? 0}</span>
            <div className="spacer" />
            <span className="muted">Черновики не видны сотрудникам</span>
          </div>
        </div>

        <div className="col gap-16">
          <div className="card">
            <div className="card-hd"><h2 className="t-h3">Разделы</h2></div>
            <div className="card-bd">
              <div className="col gap-10">
                {(overview?.sections ?? []).map((item) => (
                  <div className="row t-sm" key={item.code}>
                    <span>{item.title}</span>
                    <div className="spacer" />
                    {item.drafts > 0 && (
                      <span className="badge badge-warn">{item.drafts} черн.</span>
                    )}
                    <b className="num">{item.total}</b>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-hd"><h2 className="t-h3">Чего не хватает</h2></div>
            <div className="card-bd">
              <div className="col gap-10">
                {[
                  'История версий статьи (кто и что изменил)',
                  'Согласование публикации — статья уходит в эфир сразу',
                  'Вложения и изображения в статье',
                  'Отметка «устарела» и напоминание об актуализации'
                ].map((text) => (
                  <div className="row t-sm" key={text}>
                    <span className="muted" style={{ width: 14 }}>×</span>
                    <span className="muted">{text}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="callout">
            <div>
              Прогресс чек-листа считается по факту прочтения статьи: отметка «прочитано» —
              это и есть выполнение пункта. Отдельного «чек-листа задач» в модели нет.
            </div>
          </div>
        </div>
      </div>
    </Shell>
  );
}
