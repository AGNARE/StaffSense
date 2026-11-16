package com.staffsense.demo.model;

/**
 * Этап кандидата в воронке найма.
 *
 * <p>Переходы описаны здесь, а не в контроллере: правило одно для API,
 * для интерфейса и для тестов. Назад из финальных состояний не ходим —
 * нанятого или отклонённого кандидата возвращают новой записью, иначе
 * ломается история воронки.
 */
public enum CandidateStage {

    APPLIED("Отклик"),
    SCREENING("Скрининг"),
    INTERVIEW("Интервью"),
    OFFER("Оффер"),
    HIRED("Нанят"),
    REJECTED("Отклонён");

    private final String title;

    CandidateStage(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public boolean isFinal() {
        return this == HIRED || this == REJECTED;
    }

    /** Допустим ли переход на этот этап. */
    public boolean canMoveTo(CandidateStage target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case APPLIED -> target == SCREENING || target == REJECTED;
            case SCREENING -> target == INTERVIEW || target == REJECTED;
            case INTERVIEW -> target == OFFER || target == REJECTED;
            case OFFER -> target == HIRED || target == REJECTED;
            case HIRED, REJECTED -> false;
        };
    }
}
