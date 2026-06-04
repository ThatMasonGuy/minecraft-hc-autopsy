package tempeststudios.hcautopsy.compat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import tempeststudios.hcautopsy.HCAutopsy;

import java.lang.reflect.Constructor;

/**
 * Bridges chat click/hover event construction across Minecraft's text API split.
 */
public final class TextEventCompat {
    private static final String RUN_COMMAND_CLASS = "net.minecraft.network.chat.ClickEvent$RunCommand";
    private static final String SHOW_TEXT_CLASS = "net.minecraft.network.chat.HoverEvent$ShowText";
    private static final String CLICK_ACTION_CLASS = "net.minecraft.network.chat.ClickEvent$Action";
    private static final String HOVER_ACTION_CLASS = "net.minecraft.network.chat.HoverEvent$Action";

    private TextEventCompat() {
    }

    public static void applyRunCommand(MutableComponent component, String command, Component hoverText) {
        ClickEvent clickEvent = createRunCommand(command);
        HoverEvent hoverEvent = createShowText(hoverText);

        if (clickEvent == null && hoverEvent == null) {
            return;
        }

        Style style = component.getStyle();
        if (clickEvent != null) {
            style = style.withClickEvent(clickEvent);
        }
        if (hoverEvent != null) {
            style = style.withHoverEvent(hoverEvent);
        }
        component.setStyle(style);
    }

    private static ClickEvent createRunCommand(String command) {
        try {
            Class<?> runCommandClass = Class.forName(RUN_COMMAND_CLASS);
            Constructor<?> constructor = runCommandClass.getConstructor(String.class);
            return (ClickEvent) constructor.newInstance(command);
        } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException ignored) {
            return createLegacyRunCommand(command);
        } catch (LinkageError error) {
            HCAutopsy.LOGGER.debug("Failed to create modern click event", error);
            return createLegacyRunCommand(command);
        }
    }

    private static ClickEvent createLegacyRunCommand(String command) {
        try {
            Class<?> actionClass = Class.forName(CLICK_ACTION_CLASS);
            Object action = actionClass.getField("RUN_COMMAND").get(null);
            Constructor<ClickEvent> constructor = ClickEvent.class.getConstructor(actionClass, String.class);
            return constructor.newInstance(action, command);
        } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException | LinkageError error) {
            HCAutopsy.LOGGER.debug("Failed to create legacy click event", error);
            return null;
        }
    }

    private static HoverEvent createShowText(Component text) {
        try {
            Class<?> showTextClass = Class.forName(SHOW_TEXT_CLASS);
            Constructor<?> constructor = showTextClass.getConstructor(Component.class);
            return (HoverEvent) constructor.newInstance(text);
        } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException ignored) {
            return createLegacyShowText(text);
        } catch (LinkageError error) {
            HCAutopsy.LOGGER.debug("Failed to create modern hover event", error);
            return createLegacyShowText(text);
        }
    }

    private static HoverEvent createLegacyShowText(Component text) {
        try {
            Class<?> actionClass = Class.forName(HOVER_ACTION_CLASS);
            Object action = actionClass.getField("SHOW_TEXT").get(null);
            Constructor<HoverEvent> constructor = HoverEvent.class.getConstructor(actionClass, Object.class);
            return constructor.newInstance(action, text);
        } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException | LinkageError error) {
            HCAutopsy.LOGGER.debug("Failed to create legacy hover event", error);
            return null;
        }
    }
}
