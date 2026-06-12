package tempeststudios.hcautopsy.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import tempeststudios.hcautopsy.HCAutopsy;

import java.lang.reflect.Method;

/**
 * Bridges server-player message delivery across mapped runtime method shapes.
 */
public final class PlayerMessageCompat {
    private PlayerMessageCompat() {
    }

    public static boolean sendSystemMessage(ServerPlayer player, Component message) {
        return sendSystemMessageTo(player, message);
    }

    static boolean sendSystemMessageTo(Object target, Component message) {
        if (target == null || message == null) {
            return false;
        }

        return invoke(target, "sendSystemMessage", new Class<?>[]{Component.class}, message)
                || invoke(target, "method_43496", new Class<?>[]{Component.class}, message)
                || invoke(target, "sendSystemMessage", new Class<?>[]{Component.class, boolean.class}, message, false)
                || invoke(target, "method_43502", new Class<?>[]{Component.class, boolean.class}, message, false)
                || invoke(target, "displayClientMessage", new Class<?>[]{Component.class, boolean.class}, message, false)
                || invoke(target, "method_7353", new Class<?>[]{Component.class, boolean.class}, message, false);
    }

    private static boolean invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.invoke(target, arguments);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            HCAutopsy.LOGGER.debug(
                    "Player message method {}{} was not usable on {}",
                    methodName,
                    parameterTypes.length == 1 ? "(Component)" : "(Component, boolean)",
                    target.getClass().getName()
            );
            return false;
        }
    }
}
