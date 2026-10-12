package com.staffsense.demo.dto.onboarding;

/** Прогресс прохождения чек-листа новичка. */
public record ChecklistProgress(long total, long completed, int percent) {

    public static ChecklistProgress of(long total, long completed) {
        int percent = total > 0 ? (int) Math.round(completed * 100.0 / total) : 0;
        return new ChecklistProgress(total, completed, percent);
    }
}
