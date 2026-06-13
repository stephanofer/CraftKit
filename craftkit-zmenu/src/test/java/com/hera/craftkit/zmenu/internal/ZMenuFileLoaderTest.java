package com.hera.craftkit.zmenu.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ZMenuFileLoaderTest {

    @Test
    void defaultResourceIsCoveredByMatchingScannedDirectory() {
        assertTrue(ZMenuFileLoader.isCoveredByDirectory("inventories/language.yml", "inventories"));
        assertTrue(ZMenuFileLoader.isCoveredByDirectory("inventories\\language.yml", "inventories"));
        assertTrue(ZMenuFileLoader.isCoveredByDirectory("/inventories/language.yml", "inventories/"));
        assertTrue(ZMenuFileLoader.isCoveredByDirectory("patterns/common/base.yml", "patterns/common"));
    }

    @Test
    void defaultResourceIsNotCoveredByDifferentOrBlankDirectory() {
        assertFalse(ZMenuFileLoader.isCoveredByDirectory("inventories/language.yml", "patterns"));
        assertFalse(ZMenuFileLoader.isCoveredByDirectory("inventory-files/language.yml", "inventories"));
        assertFalse(ZMenuFileLoader.isCoveredByDirectory("inventories.yml", "inventories"));
        assertFalse(ZMenuFileLoader.isCoveredByDirectory("inventories/language.yml", null));
        assertFalse(ZMenuFileLoader.isCoveredByDirectory("inventories/language.yml", " "));
    }
}
