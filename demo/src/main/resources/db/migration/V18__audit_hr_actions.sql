-- V18: аудит кадровых действий.
--
-- Журнал (V12) знал только действия администратора: создание учётки, роль,
-- блокировку и сброс пароля. За это время появились другие действия, которые
-- меняют положение сотрудника: начисление отпуска, подпись документа,
-- открытие вакансии и перевод кандидата. Без записи их не разобрать
-- постфактум — а именно они чаще всего спорные.

ALTER TABLE public.admin_audit_log DROP CONSTRAINT IF EXISTS admin_audit_action_check;

ALTER TABLE public.admin_audit_log ADD CONSTRAINT admin_audit_action_check
    CHECK (action IN (
        'USER_CREATED', 'ROLE_CHANGED', 'ACTIVE_CHANGED', 'PASSWORD_RESET',
        'VACATION_GRANTED', 'DOCUMENT_SIGNED', 'DOCUMENT_REJECTED',
        'VACANCY_CREATED', 'VACANCY_CLOSED',
        'CANDIDATE_ADDED', 'CANDIDATE_STAGE_CHANGED'
    ));

COMMENT ON TABLE public.admin_audit_log IS
    'Журнал действий: администратор и кадровые операции (отпуска, документы, найм)';
