package com.staffsense.demo.service.tunduk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка эмуляции «Тундук»: три исхода — найден, не найден, сервис недоступен.
 */
class MockTundukClientTest {

    private final MockTundukClient client = new MockTundukClient();

    @Test
    void returnsPersonDataForKnownInn() {
        TundukLookupResult result = client.findByInn("12345678901234");

        assertEquals(TundukStatus.FOUND, result.status());
        assertTrue(result.isFound());

        TundukPersonData person = result.data();
        assertNotNull(person);
        assertEquals("12345678901234", person.inn());
        assertNotNull(person.fullName());
        assertNotNull(person.passportSeries());
        assertNotNull(person.passportNumber());
        assertNotNull(person.birthDate());
        assertNotNull(person.gender());
        assertNotNull(person.photoUrl());
    }

    @Test
    void returnsNotFoundForUnknownInn() {
        TundukLookupResult result = client.findByInn("11111111111111");

        assertEquals(TundukStatus.NOT_FOUND, result.status());
        assertNull(result.data());
    }

    @Test
    void returnsUnavailableForFailureTestInn() {
        TundukLookupResult result = client.findByInn(MockTundukClient.UNAVAILABLE_INN);

        assertEquals(TundukStatus.UNAVAILABLE, result.status());
        assertNull(result.data());
    }

    @Test
    void trimsInnBeforeLookup() {
        assertEquals(TundukStatus.FOUND, client.findByInn("  12345678901234  ").status());
    }

    @Test
    void directoryContainsOnlyFictionalCitizens() {
        // Справочник детерминированный и непустой; все ИНН — по 14 цифр.
        assertTrue(client.getDemoInns().size() >= 5);
        for (String inn : client.getDemoInns()) {
            assertTrue(inn.matches("\\d{14}"), "ИНН должен состоять из 14 цифр: " + inn);
            assertTrue(client.findByInn(inn).isFound());
        }
    }
}
