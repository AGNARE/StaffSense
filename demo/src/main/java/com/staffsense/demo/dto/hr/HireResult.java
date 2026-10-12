package com.staffsense.demo.dto.hr;

import com.staffsense.demo.model.User;

/**
 * Результат приёма сотрудника.
 *
 * @param user              созданная или обновлённая учётная запись
 * @param alreadyExisted    true, если ИНН уже был в системе (дубль не создавался, BR-023)
 * @param reactivated       true, если существующая запись была неактивна и включена обратно
 * @param generatedPassword сгенерированный пароль (null, если HR задал его сам)
 */
public record HireResult(User user, boolean alreadyExisted, boolean reactivated, String generatedPassword) {
}
