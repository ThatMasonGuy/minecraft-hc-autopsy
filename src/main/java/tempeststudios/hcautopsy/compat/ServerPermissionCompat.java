package tempeststudios.hcautopsy.compat;

import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Bridges the pre-1.21.11 integer command permission API and the newer
 * PermissionSet model used by 1.21.11 and 26.x.
 */
public final class ServerPermissionCompat {
    private static final List<String> LEGACY_PERMISSION_METHODS = List.of(
            "hasPermission",
            "method_9259"
    );
    private static final List<String> MODERN_PERMISSION_SOURCE_METHODS = List.of(
            "permissions",
            "getPermissions",
            "method_75037",
            "method_75004"
    );
    private static final List<String> MODERN_PERMISSION_FIELDS_LEVEL_1 = List.of(
            "COMMANDS_MODERATOR",
            "MODERATORS",
            "field_63209"
    );
    private static final List<String> MODERN_PERMISSION_FIELDS_LEVEL_2 = List.of(
            "COMMANDS_GAMEMASTER",
            "GAMEMASTERS",
            "field_63210"
    );
    private static final List<String> MODERN_PERMISSION_FIELDS_LEVEL_3 = List.of(
            "COMMANDS_ADMIN",
            "ADMINS",
            "field_63211"
    );
    private static final List<String> MODERN_PERMISSION_FIELDS_LEVEL_4 = List.of(
            "COMMANDS_OWNER",
            "OWNERS",
            "field_63212"
    );
    private static final List<String> MODERN_PERMISSION_HOLDER_CLASS_NAMES = List.of(
            "net.minecraft.server.permissions.Permissions",
            "net.minecraft.command.DefaultPermissions",
            "net.minecraft.class_12099"
    );
    private static final List<String> MODERN_PERMISSION_SET_METHODS = List.of(
            "hasPermission",
            "method_75033",
            "method_75036"
    );

    private ServerPermissionCompat() {
    }

    public static boolean hasCommandLevel(CommandSourceStack source, int permissionLevel) {
        if (source.getEntity() == null) {
            return true;
        }
        return hasCommandLevelForEntitySource(source, permissionLevel);
    }

    static boolean hasCommandLevelForEntitySource(Object source, int permissionLevel) {
        return hasLegacyPermissionLevel(source, permissionLevel)
                || hasModernPermission(source, permissionLevel);
    }

    private static boolean hasLegacyPermissionLevel(Object source, int permissionLevel) {
        for (String methodName : LEGACY_PERMISSION_METHODS) {
            try {
                Object result = source.getClass().getMethod(methodName, int.class).invoke(source, permissionLevel);
                if (result instanceof Boolean permitted) {
                    return permitted;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return false;
    }

    private static boolean hasModernPermission(Object source, int permissionLevel) {
        Object permissionSet = getPermissionSet(source);
        Object permission = getPermissionForLevel(permissionLevel);
        if (permissionSet == null || permission == null) {
            return false;
        }

        for (String methodName : MODERN_PERMISSION_SET_METHODS) {
            Boolean result = invokeBooleanPermissionCheck(permissionSet, methodName, permission);
            if (result != null) {
                return result;
            }
        }
        return false;
    }

    private static Object getPermissionSet(Object source) {
        for (String methodName : MODERN_PERMISSION_SOURCE_METHODS) {
            try {
                return source.getClass().getMethod(methodName).invoke(source);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static Object getPermissionForLevel(int permissionLevel) {
        List<String> fields = modernPermissionFields(permissionLevel);
        if (fields.isEmpty()) {
            return null;
        }

        for (String className : MODERN_PERMISSION_HOLDER_CLASS_NAMES) {
            try {
                Class<?> holderClass = Class.forName(className);
                Object permission = getFirstStaticField(holderClass, fields);
                if (permission != null) {
                    return permission;
                }
            } catch (LinkageError | ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Object getFirstStaticField(Class<?> holderClass, List<String> fieldNames)
            throws ReflectiveOperationException {
        for (String fieldName : fieldNames) {
            try {
                Field field = holderClass.getField(fieldName);
                return field.get(null);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static List<String> modernPermissionFields(int permissionLevel) {
        return switch (permissionLevel) {
            case 1 -> MODERN_PERMISSION_FIELDS_LEVEL_1;
            case 2 -> MODERN_PERMISSION_FIELDS_LEVEL_2;
            case 3 -> MODERN_PERMISSION_FIELDS_LEVEL_3;
            case 4 -> MODERN_PERMISSION_FIELDS_LEVEL_4;
            default -> List.of();
        };
    }

    private static Boolean invokeBooleanPermissionCheck(Object permissionSet, String methodName, Object permission) {
        for (Method method : permissionSet.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            try {
                Object result = method.invoke(permissionSet, permission);
                if (result instanceof Boolean permitted) {
                    return permitted;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }
}
