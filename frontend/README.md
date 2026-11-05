# StaffSense Frontend

Клиент на React + TypeScript, собирается Vite и кладётся в статику Spring-приложения.

## Команды

```bash
npm install       # один раз
npm run dev       # разработка: http://localhost:5173, запросы проксируются на :9091
npm run build     # tsc --noEmit + сборка в ../demo/src/main/resources/static/app
```

## Как это связано с бэкендом

- Сборка идёт в `demo/src/main/resources/static/app` и коммитится: приложение
  запускается без Node (`mvnw spring-boot:run`).
- Адрес приложения в Spring — `/app` (редирект на `index.html` в
  `ClientRoutesConfig`).
- Дизайн-система редизайна (`proto.css`, шрифты) лежит в
  `demo/src/main/resources/static/design` и подключается из `index.html`
  по абсолютным путям — не бандлится, чтобы не дублировать 67 КБ стилей.
- Аутентификация — сессионная cookie, поэтому клиент и бэк должны быть на
  одном origin (`credentials: 'same-origin'` в `src/api.ts`).

## Структура

```
src/
├── api.ts          запросы к /api/v1 + человеческие ошибки
├── types.ts        типы контракта (Page, RiskListItem, EmployeeDetail…)
├── format.ts       числа и даты
├── icons.ts        иконки меню, сгенерированные из прототипа
├── App.tsx         хеш-роутинг: #/risk, #/employees
├── components/
│   └── Shell.tsx   сайдбар + топбар + профиль текущего пользователя
└── pages/
    ├── RiskPage.tsx
    └── EmployeesPage.tsx
```

## Что ещё не перенесено

Разделы «Обзор», «Портал», «Опросы», «Записки», «Проекты», «Документы»,
«ИИ-скиллы», «Администрирование», «Дизайн-система» пока живут в Thymeleaf;
в меню они помечены неактивными (`is-todo`).
