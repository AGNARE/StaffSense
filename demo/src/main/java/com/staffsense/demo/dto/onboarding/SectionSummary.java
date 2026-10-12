package com.staffsense.demo.dto.onboarding;

import com.staffsense.demo.model.OnboardingSection;

/** Сводка по разделу портала: сколько статей опубликовано. */
public record SectionSummary(OnboardingSection section, String title, long articleCount) {
}
