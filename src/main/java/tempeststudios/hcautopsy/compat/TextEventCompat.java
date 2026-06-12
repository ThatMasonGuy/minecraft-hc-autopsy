package tempeststudios.hcautopsy.compat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import tempeststudios.hcautopsy.HCAutopsy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Bridges chat click/hover event construction across Minecraft's text API split.
 */
public final class TextEventCompat {
    private static final List<String> RUN_COMMAND_CLASSES = List.of(
            "net.minecraft.network.chat.ClickEvent$RunCommand",
            "net.minecraft.class_2558$class_10609"
    );
    private static final List<String> SHOW_TEXT_CLASSES = List.of(
            "net.minecraft.network.chat.HoverEvent$ShowText",
            "net.minecraft.class_2568$class_10613"
    );
    private static final List<String> CLICK_ACTION_CLASSES = List.of(
            "net.minecraft.network.chat.ClickEvent$Action",
            "net.minecraft.class_2558$class_2559"
    );
    private static final List<String> HOVER_ACTION_CLASSES = List.of(
            "net.minecraft.network.chat.HoverEvent$Action",
            "net.minecraft.class_2568$class_5247"
    );
    private static final List<String> RUN_COMMAND_FIELDS = List.of("RUN_COMMAND", "field_11750");
    private static final List<String> SHOW_TEXT_FIELDS = List.of("SHOW_TEXT", "field_24342");

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
        for (String className : RUN_COMMAND_CLASSES) {
            try {
                Class<?> runCommandClass = Class.forName(className);
                Constructor<?> constructor = runCommandClass.getConstructor(String.class);
                return (ClickEvent) constructor.newInstance(command);
            } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException ignored) {
            } catch (LinkageError error) {
                HCAutopsy.LOGGER.debug("Failed to create modern click event {}", className, error);
            }
        }
        return createLegacyRunCommand(command);
    }

    private static ClickEvent createLegacyRunCommand(String command) {
        for (String className : CLICK_ACTION_CLASSES) {
            try {
                Class<?> actionClass = Class.forName(className);
                Object action = getFirstStaticField(actionClass, RUN_COMMAND_FIELDS);
                if (action == null) {
                    continue;
                }
                Constructor<ClickEvent> constructor = ClickEvent.class.getConstructor(actionClass, String.class);
                return constructor.newInstance(action, command);
            } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException ignored) {
            } catch (LinkageError error) {
                HCAutopsy.LOGGER.debug("Failed to create legacy click event {}", className, error);
            }
        }
        HCAutopsy.LOGGER.debug("Failed to create legacy click event");
        return null;
    }

    private static HoverEvent createShowText(Component text) {
        for (String className : SHOW_TEXT_CLASSES) {
            try {
                Class<?> showTextClass = Class.forName(className);
                Constructor<?> constructor = showTextClass.getConstructor(Component.class);
                return (HoverEvent) constructor.newInstance(text);
            } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException ignored) {
            } catch (LinkageError error) {
                HCAutopsy.LOGGER.debug("Failed to create modern hover event {}", className, error);
            }
        }
        return createLegacyShowText(text);
    }

    private static HoverEvent createLegacyShowText(Component text) {
        for (String className : HOVER_ACTION_CLASSES) {
            try {
                Class<?> actionClass = Class.forName(className);
                Object action = getFirstStaticField(actionClass, SHOW_TEXT_FIELDS);
                if (action == null) {
                    continue;
                }
                Constructor<HoverEvent> constructor = HoverEvent.class.getConstructor(actionClass, Object.class);
                return constructor.newInstance(action, text);
            } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException ignored) {
            } catch (LinkageError error) {
                HCAutopsy.LOGGER.debug("Failed to create legacy hover event {}", className, error);
            }
        }
        HCAutopsy.LOGGER.debug("Failed to create legacy hover event");
        return null;
    }

    private static Object getFirstStaticField(Class<?> holderClass, List<String> fieldNames)
            throws IllegalAccessException {
        for (String fieldName : fieldNames) {
            try {
                Field field = holderClass.getField(fieldName);
                return field.get(null);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
