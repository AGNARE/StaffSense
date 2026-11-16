import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { journeyApi } from '../journey-api';
import type { Me, MyMeeting } from '../journey-api';
import type { Checklist, Memo, SurveyItem } from '../types';
import { formatDate, formatShortDateTime } from '../format';

const MEETING_BADGE: Record<string, string> = {
  PLANNED: 'badge-warn',
  COMPLETED: 'badge-ok',
  CANCELLED: 'badge-neutral',
  RESCHEDULED: 'badge-info'
};

const MEMO_BADGE: Record<string, string> = {
  DRAFT: 'badge-neutral',
  PENDING_PM: 'badge-warn',
  PENDING_HR: 'badge-info',
  APPROVED: 'badge-ok',
  REJECTED: 'badge-danger',
  CANCELLED: 'badge-neutral'
};

/**
 * «Мой путь» — один экран на все цели сотрудника.
 *
 * Профиль, опросы, служебные записки, онбординг и встречи 1:1 уже есть
 * отдельными разделами; здесь они собраны как сценарий: что я за человек,
 * что мне нужно пройти, что я подал и когда у меня следующая встреча.
 * Своих правил доступа экран не заводит — только показывает то, что вернул
 * сервер (у EMPLOYEE это его собственные данные).
 */
export function MyJourneyPage() {
  const [me, setMe] = useState<Me | null>(null);
  const [meetings, setMeetings] = useState<MyMeeting[]>([]);
  const [surveys, setSurveys] = useState<SurveyItem[]>([]);
  const [memos, setMemos] = useState<Memo[]>([]);
  const [checklist, setChecklist] = useState<Checklist | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);

    Promise.allSettled([
      journeyApi.me(),
      journeyApi.myMeetings(),
      journeyApi.surveys(),
      journeyApi.memos(),
      journeyApi.checklist()
    ]).then(([meResult, meetingsResult, surveysResult, memosResult, checklistResult]) => {
      if (!alive) return;

      if (meResult.status === 'fulfilled') setMe(meResult.value);
      if (meetingsResult.status === 'fulfilled') setMeetings(meetingsResult.value);
      if (surveysResult.status === 'fulfilled') setSurveys(surveysResult.value);
      if (memosResult.status === 'fulfilled') setMemos(memosResult.value);
      if (checklistResult.status === 'fulfilled') setChecklist(checklistResult.value);

      // Профиль — обязательная часть экрана: без него дальше показывать нечего.
      if (meResult.status === 'rejected') {
        setError(meResult.reason instanceof Error ? meResult.reason.message : 'Не удалось загрузить профиль');
      }
      setLoading(false);
    });

    return () => { alive = false; };
  }, [refreshKey]);

  const pendingSurveys = surveys.filter((survey) => survey.canTake);
  const upcomingMeetings = meetings.filter((meeting) => meeting.upcoming);
  const latestMemos = memos.slice(0, 5);

  return (
    <Shell
      active="me"
      crumbs={['Люди', 'Мой путь']}
      actions={<button className="btn btn-sm btn-outline" type="button" onClick={() => setRefreshKey((key) => key + 1)}>Обновить</button>}
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Личный кабинет · сотрудник</div>
          <h1 className="t-hero">{me ? me.fullName : 'Мой путь'}</h1>
          <div className="t-sm muted mt-8">
            {me ? [me.position, me.department].filter(Boolean).join(' · ') : 'загрузка…'}
          </div>
        </div>
        <div className="spacer" />
        {me && <span className="badge badge-outline">{me.role}</span>}
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)', marginBottom: 16 }}>{error}</div>}

      <div className="grid g4">
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Опросы к прохождению</span></div>
          <div className="kpi-val">{pendingSurveys.length}</div>
          <div className="kpi-foot"><span>активные и ещё не пройдены</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Мои записки</span></div>
          <div className="kpi-val">{memos.length}</div>
          <div className="kpi-foot"><span>за всё время</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Онбординг</span></div>
          <div className="kpi-val">{checklist ? checklist.completed + '/' + checklist.total : '—'}</div>
          <div className="kpi-foot"><span>шагов пройдено</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Ближайшие 1:1</span></div>
          <div className="kpi-val">{upcomingMeetings.length}</div>
          <div className="kpi-foot"><span>запланированные встречи</span></div>
        </div>
      </div>

      <div className="grid g-2-1 mt-16" style={{ alignItems: 'start' }}>
        <div className="grid" style={{ gap: 16 }}>
          <div className="card card-flush">
            <div className="card-hd">
              <h2 className="t-h3">Мои встречи 1:1</h2>
              <div className="spacer" />
              <span className="badge badge-neutral">{meetings.length}</span>
            </div>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Когда</th>
                    <th>С кем</th>
                    <th>Моя роль</th>
                    <th>Статус</th>
                  </tr>
                </thead>
                <tbody>
                  {loading && <tr><td colSpan={4} className="muted" style={{ padding: 20 }}>Загрузка…</td></tr>}
                  {!loading && meetings.length === 0 && (
                    <tr>
                      <td colSpan={4} className="muted" style={{ padding: 20 }}>
                        Встреч пока нет — руководитель назначит их при планировании.
                      </td>
                    </tr>
                  )}
                  {meetings.map((meeting) => (
                    <MeetingRow key={meeting.id} meeting={meeting} />
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="card card-flush">
            <div className="card-hd">
              <h2 className="t-h3">Мои последние записки</h2>
              <div className="spacer" />
              <a className="btn btn-sm btn-ghost" href="#/memos">Открыть раздел →</a>
            </div>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th>Тип</th><th>Период</th><th>Статус</th></tr>
                </thead>
                <tbody>
                  {loading && <tr><td colSpan={3} className="muted" style={{ padding: 20 }}>Загрузка…</td></tr>}
                  {!loading && latestMemos.length === 0 && (
                    <tr><td colSpan={3} className="muted" style={{ padding: 20 }}>Записок нет — можно создать в разделе «Записки».</td></tr>
                  )}
                  {latestMemos.map((memo) => (
                    <tr key={memo.id}>
                      <td><span className="t-main">{memo.typeTitle}</span></td>
                      <td className="muted t-xs">{formatDate(memo.startDate)} — {formatDate(memo.endDate)}</td>
                      <td>
                        <span className={'badge ' + (MEMO_BADGE[memo.status] ?? 'badge-neutral')}>
                          {memo.statusTitle}
                        </span>
                        {memo.rejectionReason && (
                          <div className="t-xs muted mt-4">Причина: {memo.rejectionReason}</div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div className="grid" style={{ gap: 16 }}>
          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Профиль</h2></div>
            <div className="card-bd">
              <div className="t-main">{me?.fullName ?? '—'}</div>
              <div className="t-sm muted mt-4">{me?.position ?? 'должность не указана'}</div>
              <div className="t-sm muted">{me?.department ?? 'отдел не указан'}</div>
              <div className="t-xs muted mt-8" style={{ wordBreak: 'break-all' }}>{me?.email ?? '—'}</div>
            </div>
          </div>

          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Пройти дальше</h2></div>
            <div className="card-bd" style={{ display: 'grid', gap: 8 }}>
              <a className="btn btn-sm btn-outline" href="#/surveys">
                Опросы{pendingSurveys.length > 0 ? ' · ' + pendingSurveys.length : ''}
              </a>
              <a className="btn btn-sm btn-outline" href="#/memos">Служебные записки</a>
              <a className="btn btn-sm btn-outline" href="#/onboarding">Портал онбординга</a>
            </div>
          </div>

          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Онбординг</h2></div>
            <div className="card-bd">
              {checklist ? (
                <>
                  <div className="row" style={{ justifyContent: 'space-between' }}>
                    <span className="t-sm">пройдено</span>
                    <strong>{checklist.completed} из {checklist.total}</strong>
                  </div>
                  <div style={{ height: 6, borderRadius: 4, background: 'var(--surface-inset)', overflow: 'hidden', marginTop: 6 }}>
                    <div style={{ width: checklist.percent + '%', height: '100%', background: 'var(--ok)' }} />
                  </div>
                </>
              ) : <span className="t-sm muted">нет данных</span>}
              <a className="btn btn-sm btn-outline mt-8" href="#/onboarding">Продолжить →</a>
            </div>
          </div>
        </div>
      </div>
    </Shell>
  );
}

function MeetingRow(props: { meeting: MyMeeting }) {
  const { meeting } = props;
  const withWhom = meeting.myRole === 'MANAGER' ? meeting.employeeName : meeting.managerName;

  return (
    <tr>
      <td>
        <div className="t-main">{formatShortDateTime(meeting.scheduledAt)}</div>
        <div className="sub">{meeting.upcoming ? 'впереди' : 'в прошлом'}</div>
      </td>
      <td>
        <div className="t-main">{withWhom}</div>
        {meeting.notes && <div className="sub truncate" style={{ maxWidth: 260 }}>{meeting.notes}</div>}
      </td>
      <td className="muted t-xs">{meeting.myRole === 'MANAGER' ? 'руководитель' : 'сотрудник'}</td>
      <td>
        <span className={'badge ' + (MEETING_BADGE[meeting.status] ?? 'badge-neutral')}>
          {meeting.statusTitle}
        </span>
        {meeting.missed && <span className="badge badge-danger" style={{ marginLeft: 6 }}>пропущена</span>}
      </td>
    </tr>
  );
}
