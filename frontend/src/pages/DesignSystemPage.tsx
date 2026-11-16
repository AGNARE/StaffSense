import { Shell } from '../components/Shell';

/**
 * Витрина дизайн-системы.
 *
 * Собирает в одном месте то, что уже используется на экранах: токены, типографику,
 * кнопки, бейджи, карточки, формы и таблицы. Страница отвечает на вопрос
 * «есть ли единая система» без слов — все компоненты показаны живьём,
 * а не описаны.
 *
 * Стили берутся из /design/proto.css — того же файла, что и на рабочих экранах.
 * Поэтому витрина не может разойтись с интерфейсом: это буквально он и есть.
 *
 * <p>Страница остаётся как документация дизайн-системы, но в навигацию и
 * маршруты клиента больше не подключена: витрина — не раздел продукта,
 * а справочник для разработки.
 */
export function DesignSystemPage() {
  return (
    <Shell
      active="overview"
      crumbs={['Система', 'Дизайн-система']}
      actions={<a className="btn btn-sm btn-outline" href="/design/proto.css" target="_blank" rel="noreferrer">proto.css</a>}
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Система · витрина</div>
          <h1 className="t-hero">Дизайн-система</h1>
          <div className="t-sm muted mt-8">
            Источник стилей — proto.css из редизайна. Ниже — то, из чего собраны экраны.
          </div>
        </div>
      </div>

      <Block title="Токены цвета">
        <div className="grid g4">
          {[
            ['surface', 'var(--surface)'],
            ['brand', 'var(--brand)'],
            ['ok', 'var(--ok)'],
            ['warn', 'var(--warn)'],
            ['danger', 'var(--danger)'],
            ['ai', 'var(--ai)'],
            ['ink', 'var(--ink)'],
            ['line', 'var(--line)']
          ].map(([name, value]) => (
            <div key={name} className="card card-bd">
              <div style={{ height: 44, borderRadius: 8, background: value, border: '1px solid var(--line)' }} />
              <div className="t-xs muted mt-4">{name}</div>
              <div className="t-xs muted">{value}</div>
            </div>
          ))}
        </div>
      </Block>

      <Block title="Типографика">
        <div className="t-hero">Заголовок экрана · t-hero</div>
        <div className="t-h1 mt-8">Крупный заголовок · t-h1</div>
        <div className="t-h2 mt-8">Заголовок карточки · t-h2</div>
        <div className="t-h3 mt-8">Подзаголовок · t-h3</div>
        <div className="t-sm mt-8">Основной текст · t-sm. Используется в описаниях и подписях полей.</div>
        <div className="t-xs muted mt-8">Мелкий служебный текст · t-xs muted.</div>
        <div className="micro mt-8">Метка раздела · micro</div>
      </Block>

      <Block title="Кнопки">
        <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          <button className="btn btn-primary" type="button">Основная</button>
          <button className="btn btn-outline" type="button">Контурная</button>
          <button className="btn btn-ghost" type="button">Призрачная</button>
          <button className="btn btn-danger" type="button">Опасная</button>
          <button className="btn btn-primary" type="button" disabled>Недоступна</button>
        </div>
        <div className="row mt-8" style={{ gap: 8, flexWrap: 'wrap' }}>
          <button className="btn btn-sm btn-primary" type="button">btn-sm</button>
          <button className="btn btn-sm btn-outline" type="button">btn-sm outline</button>
          <button className="btn btn-lg btn-primary" type="button">btn-lg</button>
        </div>
      </Block>

      <Block title="Бейджи и метки">
        <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          <span className="badge badge-ok">Утверждена</span>
          <span className="badge badge-warn">Средний риск</span>
          <span className="badge badge-danger">Высокий риск</span>
          <span className="badge badge-info">На проверке</span>
          <span className="badge badge-ai">AI</span>
          <span className="badge badge-outline">Грейд</span>
          <span className="badge badge-neutral">Черновик</span>
        </div>
        <div className="row mt-8" style={{ gap: 12 }}>
          <span className="delta delta-up">+4,2</span>
          <span className="delta delta-down">−2,0</span>
          <span className="delta delta-flat">0,0</span>
        </div>
      </Block>

      <Block title="Карточки и KPI">
        <div className="grid g4">
          <div className="kpi">
            <div className="kpi-top"><span className="kpi-label">KPI-карточка</span></div>
            <div className="kpi-val">14</div>
            <div className="kpi-foot"><span>подпись показателя</span></div>
          </div>
          <div className="kpi">
            <div className="kpi-top"><span className="kpi-label">С акцентом</span></div>
            <div className="kpi-val" style={{ color: 'var(--danger)' }}>4</div>
            <div className="kpi-foot"><span>требует разбора</span></div>
          </div>
          <div className="kpi">
            <div className="kpi-top"><span className="kpi-label">Прогресс</span></div>
            <div className="kpi-val">92<span className="unit">%</span></div>
            <div className="kpi-foot"><span>готово</span></div>
          </div>
          <div className="card card-bd">
            <div className="t-h3">Карточка</div>
            <div className="t-xs muted mt-4">.card + .card-bd</div>
          </div>
        </div>
      </Block>

      <Block title="Формы">
        <div className="row" style={{ gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <label className="field" style={{ width: 220 }}>
            <span className="field-label">Поле ввода</span>
            <input className="input" placeholder="Значение" />
            <span className="field-hint">Подсказка под полем</span>
          </label>
          <label className="field" style={{ width: 180 }}>
            <span className="field-label">Выпадающий список</span>
            <select className="input">
              <option>Первый вариант</option>
              <option>Второй вариант</option>
            </select>
          </label>
          <label className="row" style={{ gap: 8, alignItems: 'center' }}>
            <input type="checkbox" defaultChecked />
            <span className="t-sm">Флажок</span>
          </label>
          <div className="seg">
            <button type="button" className="active">Все</button>
            <button type="button">Активные</button>
            <button type="button">Закрытые</button>
          </div>
        </div>
      </Block>

      <Block title="Таблица и риск-метр">
        <div className="table-wrap">
          <table className="table table-wide">
            <thead>
              <tr><th>Сотрудник</th><th>Риск</th><th>Уровень</th><th>Обновлён</th></tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <div className="row gap-8">
                    <div className="ava ava-sm ava-1">МЛ</div>
                    <div><span className="t-main">Максим Ларин</span><span className="sub">Design</span></div>
                  </div>
                </td>
                <td>
                  <div className="risk-meter">
                    <span className="risk-track"><span className="marker" style={{ left: '83%' }} /></span>
                    <span className="risk-score is-high">83,0%</span>
                  </div>
                </td>
                <td><span className="badge badge-danger">Высокий</span></td>
                <td className="muted t-xs">06.11, 01:49</td>
              </tr>
              <tr>
                <td>
                  <div className="row gap-8">
                    <div className="ava ava-sm ava-2">СМ</div>
                    <div><span className="t-main">София Морозова</span><span className="sub">Design</span></div>
                  </div>
                </td>
                <td>
                  <div className="risk-meter">
                    <span className="risk-track"><span className="marker" style={{ left: '59%' }} /></span>
                    <span className="risk-score is-med">59,3%</span>
                  </div>
                </td>
                <td><span className="badge badge-warn">Средний</span></td>
                <td className="muted t-xs">13.10, 04:19</td>
              </tr>
            </tbody>
          </table>
        </div>
      </Block>

      <Block title="Принципы">
        <ul className="t-sm" style={{ margin: 0, paddingLeft: 18, lineHeight: 1.8 }}>
          <li>Один источник стилей: <code>static/design/proto.css</code>, рабочие экраны и витрина используют его же.</li>
          <li>Цвет несёт смысл: красный — высокий риск, жёлтый — наблюдение, зелёный — норма.</li>
          <li>Плотность и тема — атрибутами на <code>&lt;html&gt;</code>, а не отдельными стилями экранов.</li>
          <li>Компонент описывает поведение, экран — композицию; верстка не дублируется между экранами.</li>
        </ul>
      </Block>
    </Shell>
  );
}

function Block(props: { title: string; children: React.ReactNode }) {
  return (
    <div className="card card-flush mt-16">
      <div className="card-hd"><h2 className="t-h3">{props.title}</h2></div>
      <div className="card-bd">{props.children}</div>
    </div>
  );
}
