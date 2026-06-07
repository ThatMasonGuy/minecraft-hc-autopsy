package tempeststudios.hcautopsy.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerPermissionCompatTest {
    @Test
    void legacyNamedPermissionMethodAllowsRequestedLevel() {
        assertTrue(ServerPermissionCompat.hasCommandLevelForEntitySource(new LegacyNamedSource(2), 2));
        assertFalse(ServerPermissionCompat.hasCommandLevelForEntitySource(new LegacyNamedSource(1), 2));
    }

    @Test
    void legacyIntermediaryPermissionMethodAllowsRequestedLevel() {
        assertTrue(ServerPermissionCompat.hasCommandLevelForEntitySource(new LegacyIntermediarySource(2), 2));
        assertFalse(ServerPermissionCompat.hasCommandLevelForEntitySource(new LegacyIntermediarySource(1), 2));
    }

    @Test
    void unknownEntitySourceIsNotPermitted() {
        assertFalse(ServerPermissionCompat.hasCommandLevelForEntitySource(new Object(), 2));
    }

    record LegacyNamedSource(int maxLevel) {
        public boolean hasPermission(int permissionLevel) {
            return permissionLevel <= maxLevel;
        }
    }

    record LegacyIntermediarySource(int maxLevel) {
        public boolean method_9259(int permissionLevel) {
            return permissionLevel <= maxLevel;
        }
    }
}
