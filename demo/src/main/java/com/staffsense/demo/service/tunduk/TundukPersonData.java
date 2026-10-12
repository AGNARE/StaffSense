package com.staffsense.demo.service.tunduk;

import com.staffsense.demo.model.Gender;

import java.time.LocalDate;

/**
 * Персональные данные гражданина, полученные из госсистемы «Тундук / Кызмат».
 *
 * <p>Все данные в демонстрационной версии — вымышленные.
 */
public record TundukPersonData(
        String inn,
        String pin,
        String fullName,
        LocalDate birthDate,
        Gender gender,
        String passportSeries,
        String passportNumber,
        String passportIssuedBy,
        LocalDate passportIssueDate,
        String registrationAddress,
        String photoUrl
) {
}
