package tempeststudios.hcautopsy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;

public final class HCAutopsyServerSmokeTest {
    private static final String SMOKE_TEST_PROPERTY = "hcautopsy.smokeTest";
    private static final int PASS_AFTER_TICKS = 20;

    private static int ticks;
    private static boolean complete;

    private HCAutopsyServerSmokeTest() {
    }

    public static void registerIfEnabled() {
        if (!Boolean.getBoolean(SMOKE_TEST_PROPERTY)) {
            return;
        }

        System.out.println("[HCAutopsy] Automated server smoke test armed.");
        ServerTickEvents.END_SERVER_TICK.register(HCAutopsyServerSmokeTest::tick);
    }

    private static void tick(MinecraftServer server) {
        if (complete) {
            return;
        }

        ticks++;
        if (ticks < PASS_AFTER_TICKS) {
            return;
        }

        complete = true;
        verifyInitialized(server);

        System.out.println(
                "HCAUTOPSY_SERVER_SMOKE_TEST_PASS minecraftProfile="
                        + System.getProperty("hcautopsy.smokeMinecraftProfile", "unknown")
                        + " gameVersion="
                        + System.getProperty("hcautopsy.smokeGameVersion", "unknown")
                        + " releaseProfile="
                        + System.getProperty("hcautopsy.smokeReleaseProfile", "unknown")
                        + " installSet="
                        + System.getProperty("hcautopsy.smokeInstallSet", "unknown")
                        + " commandRegistered=true"
                        + " commandsExecuted=true"
                        + " configLoaded=true"
                        + " persistenceDir="
                        + HCAutopsy.getPersistence().getBaseDirectory()
                        + " activeRun=true"
                        + " injectedMods="
                        + System.getProperty("fabric.addMods", "unknown")
        );
        stopServer(server);
    }

    private static void verifyInitialized(MinecraftServer server) {
        if (HCAutopsy.getConfig() == null) {
            throw new IllegalStateException("Server smoke test found no HC Autopsy config.");
        }
        if (HCAutopsy.getPersistence() == null
                || !Files.isDirectory(HCAutopsy.getPersistence().getBaseDirectory())) {
            throw new IllegalStateException("Server smoke test found no HC Autopsy persistence directory.");
        }
        if (HCAutopsy.getRunManager() == null || !HCAutopsy.getRunManager().hasActiveRun()) {
            throw new IllegalStateException("Server smoke test found no active HC Autopsy run.");
        }
        requireCommand(server, "hcautopsy");
        requireCommand(server, "hcautopsy", "status");
        requireCommand(server, "hcautopsy", "run", "list");
        requireCommand(server, "hcautopsy", "run", "continue");
        requireCommand(server, "hcautopsy", "player");
        requireCommand(server, "hcautopsy", "players");
        requireCommand(server, "hcautopsy", "leaderboard", "playtime");
        requireCommand(server, "hcautopsy", "server", "totals");
        requireCommand(server, "hcautopsy", "recalc");
        requireCommand(server, "hcautopsy", "config", "reload");
        requireCommand(server, "hcautopsy", "discord", "test");
        executeSmokeCommands(server);
    }

    private static void requireCommand(MinecraftServer server, String... commandPath) {
        if (!hasCommand(server, commandPath)) {
            throw new IllegalStateException(
                    "Server smoke test could not find /" + String.join(" ", commandPath)
                            + " in the command dispatcher."
            );
        }
    }

    private static boolean hasCommand(MinecraftServer server, String... commandPath) {
        try {
            Object commands = invokeNoArg(server, "getCommands");
            Object dispatcher = invokeNoArg(commands, "getDispatcher");
            Object node = invokeNoArg(dispatcher, "getRoot");
            for (String commandName : commandPath) {
                Method getChild = node.getClass().getMethod("getChild", String.class);
                node = getChild.invoke(node, commandName);
                if (node == null) {
                    return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void executeSmokeCommands(MinecraftServer server) {
        for (String command : List.of(
                "hcautopsy",
                "hcautopsy status",
                "hcautopsy run list",
                "hcautopsy run last",
                "hcautopsy run continue smoke-test",
                "hcautopsy player SmokeTester totals",
                "hcautopsy players",
                "hcautopsy leaderboard playtime",
                "hcautopsy server totals",
                "hcautopsy recalc",
                "hcautopsy config reload",
                "hcautopsy discord test"
        )) {
            executeCommand(server, command);
        }
    }

    private static void executeCommand(MinecraftServer server, String command) {
        try {
            Object commands = invokeNoArg(server, "getCommands");
            Object source = invokeNoArg(server, "createCommandSourceStack");
            Method method = findPerformPrefixedCommand(commands, source);
            method.invoke(commands, source, command);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(
                    "Server smoke test could not execute /" + command + ".",
                    e.getTargetException()
            );
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Server smoke test could not execute /" + command + ".", e);
        }
    }

    private static Method findPerformPrefixedCommand(Object commands, Object source) throws NoSuchMethodException {
        for (Method method : commands.getClass().getMethods()) {
            if (!method.getName().equals("performPrefixedCommand") || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes[0].isAssignableFrom(source.getClass()) && parameterTypes[1] == String.class) {
                return method;
            }
        }
        throw new NoSuchMethodException("performPrefixedCommand(" + source.getClass().getName() + ", String)");
    }

    private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static void stopServer(MinecraftServer server) {
        if (invokeStop(server, "halt", new Class<?>[]{boolean.class}, new Object[]{false})) {
            return;
        }
        if (invokeStop(server, "stopServer", new Class<?>[0], new Object[0])) {
            return;
        }
        if (invokeStop(server, "stop", new Class<?>[0], new Object[0])) {
            return;
        }
        throw new IllegalStateException("Server smoke test could not stop the Minecraft server.");
    }

    private static boolean invokeStop(
            MinecraftServer server,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] arguments) {
        try {
            Method method = findMethod(methodName, parameterTypes);
            method.invoke(server, arguments);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(String methodName, Class<?>[] parameterTypes) throws NoSuchMethodException {
        try {
            return MinecraftServer.class.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            Method method = MinecraftServer.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        }
    }
}
