package com.staffsense.demo.model;

public enum TaskStatus {
    TODO, IN_PROGRESS, REVIEW, DONE;

    /**
     * Допустим ли переход в этот статус (FR-010).
     *
     * <p>Маршрут задачи: TODO → IN_PROGRESS → REVIEW → DONE. Возврат возможен
     * на шаг назад: задачу можно вернуть в работу из проверки или из TODO.
     * Выход из DONE — это переоткрытие (BR-014): оно разрешено только в
     * IN_PROGRESS и учитывается в счётчике reopenedCount.
     *
     * <p>Повторная установка того же статуса разрешена: перетаскивание карточки
     * в ту же колонку не является переходом и не должно считаться ошибкой.
     *
     * @param target желаемый статус
     * @return true, если переход допустим
     */
    public boolean canTransitionTo(TaskStatus target) {
        if (target == null || target == this) {
            return true;
        }
        return switch (this) {
            case TODO -> target == IN_PROGRESS;
            case IN_PROGRESS -> target == TODO || target == REVIEW;
            case REVIEW -> target == IN_PROGRESS || target == DONE;
            case DONE -> target == IN_PROGRESS;
        };
    }
}
