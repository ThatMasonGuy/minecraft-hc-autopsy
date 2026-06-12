package tempeststudios.hcautopsy.compat;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMessageCompatTest {
    @Test
    void usesNamedSingleArgumentSystemMessage() {
        NamedSingleArgumentTarget target = new NamedSingleArgumentTarget();

        assertTrue(PlayerMessageCompat.sendSystemMessageTo(target, Component.literal("hello")));

        assertEquals("hello", target.message.getString());
    }

    @Test
    void usesIntermediarySingleArgumentSystemMessage() {
        IntermediarySingleArgumentTarget target = new IntermediarySingleArgumentTarget();

        assertTrue(PlayerMessageCompat.sendSystemMessageTo(target, Component.literal("hello")));

        assertEquals("hello", target.message.getString());
    }

    @Test
    void usesTwoArgumentFallback() {
        TwoArgumentTarget target = new TwoArgumentTarget();

        assertTrue(PlayerMessageCompat.sendSystemMessageTo(target, Component.literal("hello")));

        assertEquals("hello", target.message.getString());
        assertFalse(target.overlay);
    }

    @Test
    void unknownTargetReturnsFalse() {
        assertFalse(PlayerMessageCompat.sendSystemMessageTo(new Object(), Component.literal("hello")));
    }

    static final class NamedSingleArgumentTarget {
        Component message;

        public void sendSystemMessage(Component message) {
            this.message = message;
        }
    }

    static final class IntermediarySingleArgumentTarget {
        Component message;

        public void method_43496(Component message) {
            this.message = message;
        }
    }

    static final class TwoArgumentTarget {
        Component message;
        boolean overlay = true;

        public void method_43502(Component message, boolean overlay) {
            this.message = message;
            this.overlay = overlay;
        }
    }
}
