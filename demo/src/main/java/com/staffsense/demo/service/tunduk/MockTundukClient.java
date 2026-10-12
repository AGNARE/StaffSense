package com.staffsense.demo.service.tunduk;

import com.staffsense.demo.model.Gender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Эмуляция госсистемы «Тундук / Кызмат».
 *
 * <p>Справочник <b>детерминированный</b> и содержит <b>только вымышленных
 * граждан</b>: реальные ИНН и паспортные данные в репозиторий не попадают.
 *
 * <p>Служебные ИНН для демонстрации обработки ошибок:
 * <ul>
 *   <li>{@code 99999999999999} — сервис недоступен (ERR-014);</li>
 *   <li>любой другой неизвестный ИНН — гражданин не найден (ERR-014);</li>
 *   <li>не 14 цифр — ERR-013, обрабатывается до вызова клиента.</li>
 * </ul>
 */
@Component
public class MockTundukClient implements TundukClient {

    private static final Logger log = LoggerFactory.getLogger(MockTundukClient.class);

    /** Тестовый ИНН, по которому эмулируется недоступность сервиса. */
    public static final String UNAVAILABLE_INN = "99999999999999";

    /** Заглушка фотографии: реальное хранилище появится при интеграции. */
    public static final String PHOTO_PLACEHOLDER = "/img/tunduk-photo.svg";

    private final Map<String, TundukPersonData> citizens = buildDirectory();

    @Override
    public TundukLookupResult findByInn(String inn) {
        String normalized = inn == null ? "" : inn.trim();

        if (UNAVAILABLE_INN.equals(normalized)) {
            log.warn("Эмуляция «Тундук»: сервис недоступен (тестовый ИНН {})", normalized);
            return TundukLookupResult.unavailable("эмуляция сбоя по тестовому ИНН");
        }

        TundukPersonData person = citizens.get(normalized);
        if (person == null) {
            log.info("Эмуляция «Тундук»: гражданин с ИНН {} не найден", normalized);
            return TundukLookupResult.notFound(normalized);
        }

        log.info("Эмуляция «Тундук»: данные гражданина по ИНН {} получены", normalized);
        return TundukLookupResult.found(person);
    }

    /** ИНН из справочника — используется для подсказки на демонстрационной форме. */
    public Set<String> getDemoInns() {
        return citizens.keySet();
    }

    private static Map<String, TundukPersonData> buildDirectory() {
        Map<String, TundukPersonData> map = new LinkedHashMap<>();
        map.put("12345678901234", new TundukPersonData(
                "12345678901234", "11001990051234", "Абдиев Нурлан Маратович",
                LocalDate.of(1990, 5, 12), Gender.MALE,
                "ID12", "345678", "МВД КР, г. Бишкек", LocalDate.of(2018, 6, 20),
                "г. Бишкек, ул. Киевская 95, кв. 12", PHOTO_PLACEHOLDER));

        map.put("12345678901235", new TundukPersonData(
                "12345678901235", "11001993110335", "Асанова Гульмира Тилековна",
                LocalDate.of(1993, 11, 3), Gender.FEMALE,
                "ID13", "456789", "МВД КР, г. Ош", LocalDate.of(2019, 2, 14),
                "г. Ош, ул. Ленина 210, кв. 5", PHOTO_PLACEHOLDER));

        map.put("12345678901236", new TundukPersonData(
                "12345678901236", "11001987022736", "Бекмуратов Азамат Эркинович",
                LocalDate.of(1987, 2, 27), Gender.MALE,
                "ID14", "567890", "МВД КР, г. Бишкек", LocalDate.of(2016, 9, 8),
                "г. Бишкек, ул. Токтогула 125", PHOTO_PLACEHOLDER));

        map.put("12345678901237", new TundukPersonData(
                "12345678901237", "11001995071937", "Джумагулова Айнура Бакытовна",
                LocalDate.of(1995, 7, 19), Gender.FEMALE,
                "ID15", "678901", "МВД КР, г. Каракол", LocalDate.of(2020, 4, 30),
                "г. Каракол, ул. Токтогула 8", PHOTO_PLACEHOLDER));

        map.put("12345678901238", new TundukPersonData(
                "12345678901238", "11001988120138", "Иванов Сергей Петрович",
                LocalDate.of(1988, 12, 1), Gender.MALE,
                "ID16", "789012", "МВД КР, г. Бишкек", LocalDate.of(2017, 11, 11),
                "г. Бишкек, ул. Манаса 40, кв. 77", PHOTO_PLACEHOLDER));

        map.put("12345678901239", new TundukPersonData(
                "12345678901239", "11001991031539", "Кадырова Жамиля Асановна",
                LocalDate.of(1991, 3, 15), Gender.FEMALE,
                "ID17", "890123", "МВД КР, г. Джалал-Абад", LocalDate.of(2019, 8, 22),
                "г. Джалал-Абад, ул. Ленина 3", PHOTO_PLACEHOLDER));

        map.put("12345678901240", new TundukPersonData(
                "12345678901240", "11001985090940", "Маматов Эрмек Абдулаевич",
                LocalDate.of(1985, 9, 9), Gender.MALE,
                "ID18", "901234", "МВД КР, г. Бишкек", LocalDate.of(2015, 5, 18),
                "г. Бишкек, ул. Жибек-Жолу 400", PHOTO_PLACEHOLDER));

        map.put("12345678901241", new TundukPersonData(
                "12345678901241", "11001997012541", "Осмонова Аида Муратовна",
                LocalDate.of(1997, 1, 25), Gender.FEMALE,
                "ID19", "012345", "МВД КР, г. Нарын", LocalDate.of(2021, 7, 7),
                "г. Нарын, ул. Ленина 15", PHOTO_PLACEHOLDER));

        map.put("12345678901242", new TundukPersonData(
                "12345678901242", "11001992063042", "Токтогулов Данияр Болотович",
                LocalDate.of(1992, 6, 30), Gender.MALE,
                "ID20", "123450", "МВД КР, г. Бишкек", LocalDate.of(2018, 10, 3),
                "г. Бишкек, ул. Абдрахманова 150", PHOTO_PLACEHOLDER));

        return map;
    }
}
