package com.budget.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class UpdateServiceTest {

    @ParameterizedTest
    @CsvSource({
        "1.1.0, 1.0.0, true",
        "2.0.0, 1.9.9, true",
        "1.0.1, 1.0.0, true",
        "1.0.0, 1.0.0, false",
        "1.0.0, 1.0.1, false",
        "1.0.0, 2.0.0, false",
        "0.9.0, 1.0.0, false",
    })
    void isNewerVersion(String remote, String local, boolean expected) {
        assertEquals(expected, UpdateService.isNewerVersion(remote, local));
    }

    @Test
    void isNewerVersion_formatsInvalides() {
        assertFalse(UpdateService.isNewerVersion("abc", "1.0.0"));
        assertFalse(UpdateService.isNewerVersion("", "1.0.0"));
        assertTrue(UpdateService.isNewerVersion("2.0", "1.0"));
        assertFalse(UpdateService.isNewerVersion("1", "1"));
        assertTrue(UpdateService.isNewerVersion("2", "1"));
    }

    @Test
    void isNewerVersion_versionsCourtes() {
        assertTrue(UpdateService.isNewerVersion("1.1", "1.0.0"));
        assertTrue(UpdateService.isNewerVersion("2", "1.0.0"));
        assertFalse(UpdateService.isNewerVersion("1", "1.0.0"));
    }
}
