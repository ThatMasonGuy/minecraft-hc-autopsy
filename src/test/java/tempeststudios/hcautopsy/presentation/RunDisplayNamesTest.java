package tempeststudios.hcautopsy.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunDisplayNamesTest {
    @Test
    void trimsLeadingWorldsFolderFromPlayerFacingWorldNames() {
        assertEquals("Shared_Health_Hardcore_45", RunDisplayNames.world("worlds/Shared_Health_Hardcore_45"));
        assertEquals("Shared_Health_Hardcore_45", RunDisplayNames.world("worlds\\Shared_Health_Hardcore_45"));
        assertEquals("Shared_Health_Hardcore_45", RunDisplayNames.world("./worlds/Shared_Health_Hardcore_45"));
    }

    @Test
    void keepsStoredNamesWhenNoWorldsPrefixIsPresent() {
        assertEquals("world", RunDisplayNames.world("world"));
        assertEquals("Shared_Health_Hardcore_45", RunDisplayNames.world("Shared_Health_Hardcore_45"));
        assertEquals("Unknown", RunDisplayNames.world(" "));
    }

    @Test
    void trimsSanitizedWorldsPrefixFromDisplayedRunIdsOnly() {
        assertEquals(
                "Shared_Health_Hardcore_45__20260612-231116",
                RunDisplayNames.runId("worlds_Shared_Health_Hardcore_45__20260612-231116")
        );
        assertEquals("world__20260612-231116", RunDisplayNames.runId("world__20260612-231116"));
        assertEquals("Unknown", RunDisplayNames.runId(null));
    }
}
