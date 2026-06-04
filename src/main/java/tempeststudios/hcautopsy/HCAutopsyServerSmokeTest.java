package tempeststudios.hcautopsy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.nio.file.Files;

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
        if (!hasCommand(server, "hcautopsy")) {
            throw new IllegalStateException("Server smoke test could not find /hcautopsy in the command dispatcher.");
        }
    }

    private static boolean hasCommand(MinecraftServer server, String commandName) {
        try {
            Object commands = invokeNoArg(server, "getCommands");
            Object dispatcher = invokeNoArg(commands, "getDispatcher");
            Object root = invokeNoArg(dispatcher, "getRoot");
            Method getChild = root.getClass().getMethod("getChild", String.class);
            return getChild.invoke(root, commandName) != null;
        } catch (Throwable ignored) {
            return false;
        }
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
