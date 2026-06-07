package tempeststudios.hcautopsy.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunMetadataTest {
    @Test
    void continueRunClearsWipeCauseAndKeepsAuditRecord() {
        RunMetadata metadata = new RunMetadata("world__20260607-120000", "world");
        UUID playerUuid = UUID.randomUUID();
        metadata.addParticipant(playerUuid);
        metadata.markWiped(WipeCause.create(
                playerUuid,
                "Mason",
                "Mason hit the ground too hard",
                "fall",
                null,
                null
        ));

        metadata.continueRun("server rollback");

        assertEquals(RunState.ACTIVE, metadata.getState());
        assertNull(metadata.getWipeCause());
        assertEquals(0, metadata.getEndedAt());
        assertEquals(1, metadata.getContinueHistory().size());
        assertEquals("server rollback", metadata.getContinueHistory().get(0).reason());
        assertTrue(metadata.getParticipatingPlayers().contains(playerUuid));
    }

    @Test
    void deserializeAllowsMissingOptionalCollections() {
        RunMetadata metadata = RunMetadata.fromJson("""
                {
                  "runId": "world__20260607-120000",
                  "worldName": "world",
                  "startedAt": 1,
                  "endedAt": 0,
                  "state": "ACTIVE"
                }
                """);

        assertEquals("world__20260607-120000", metadata.getRunId());
        assertEquals(RunState.ACTIVE, metadata.getState());
        assertTrue(metadata.getParticipatingPlayers().isEmpty());
        assertTrue(metadata.getContinueHistory().isEmpty());
    }
}
