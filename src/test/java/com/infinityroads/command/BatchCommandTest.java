package com.infinityroads.command;

import com.infinityroads.batch.BatchCandidateScanner;
import com.infinityroads.batch.BatchChunkProcessor;
import com.infinityroads.batch.BatchQueuePersistence;
import com.infinityroads.generator.RoadGenerator;
import com.infinityroads.protection.MaintenanceManager;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas unitarias de comandos /ir chunks ...")
class BatchCommandTest {

    private MaintenanceManager maintenanceManager;
    private BatchCandidateScanner scanner;
    private TestChunkProcessor processor;
    private CommandSender testSender;
    private Command testCommand;

    private List<String> receivedMessages;
    private Set<String> senderPermissions;

    private InfinityRoadsCommand commandHandler;

    static class FakeCommand extends Command {
        protected FakeCommand(String name) {
            super(name);
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return false;
        }
    }

    static class TestChunkProcessor extends BatchChunkProcessor {
        boolean pauseCalled = false;
        boolean resumeCalled = false;
        boolean cancelCalled = false;
        boolean retryCalled = false;
        String startTokenCalled = null;

        TestChunkProcessor(Plugin plugin, RoadGenerator generator, BatchQueuePersistence persistence, BatchCandidateScanner scanner) {
            super(plugin, generator, persistence, scanner);
        }

        @Override
        public synchronized boolean pause(CommandSender sender) {
            pauseCalled = true;
            return true;
        }

        @Override
        public synchronized boolean resume(CommandSender sender) {
            resumeCalled = true;
            return true;
        }

        @Override
        public synchronized boolean cancel(CommandSender sender) {
            cancelCalled = true;
            return true;
        }

        @Override
        public synchronized boolean retry(CommandSender sender) {
            retryCalled = true;
            return true;
        }

        @Override
        public synchronized boolean startFromToken(String token, CommandSender sender) {
            startTokenCalled = token;
            return true;
        }
    }

    @BeforeEach
    void setUp() {
        maintenanceManager = new MaintenanceManager();
        RoadGenerator roadGenerator = new RoadGenerator(new NamespacedKey("infinityroads", "road_state"));
        scanner = new BatchCandidateScanner(roadGenerator, 120);

        Plugin fakePlugin = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLogger" -> Logger.getLogger("TestLogger");
                    default -> null;
                }
        );

        BatchQueuePersistence persistence = new BatchQueuePersistence(Path.of("target/test-queue.json"), null);
        processor = new TestChunkProcessor(fakePlugin, roadGenerator, persistence, scanner);

        receivedMessages = new ArrayList<>();
        senderPermissions = new HashSet<>();
        senderPermissions.add("infinityroads.admin");
        senderPermissions.add("infinityroads.chunks");

        testSender = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "sendMessage" -> {
                        if (args != null && args.length > 0 && args[0] != null) {
                            receivedMessages.add(args[0].toString());
                        }
                        yield null;
                    }
                    case "hasPermission" -> {
                        String perm = (String) args[0];
                        yield senderPermissions.contains(perm);
                    }
                    default -> null;
                }
        );

        testCommand = new FakeCommand("ir");

        commandHandler = new InfinityRoadsCommand(
                null,
                maintenanceManager,
                () -> "world",
                () -> {},
                scanner,
                processor
        );
    }

    @Test
    @DisplayName("Sin permiso infinityroads.chunks el comando es rechazado")
    void testPermissionCheck() {
        senderPermissions.remove("infinityroads.chunks");
        senderPermissions.remove("infinityroads.admin");

        boolean result = commandHandler.onCommand(testSender, testCommand, "ir", new String[]{"chunks", "status"});

        assertTrue(result);
        assertTrue(receivedMessages.stream().anyMatch(msg -> msg.contains("No tienes permiso")));
        assertFalse(processor.pauseCalled);
    }

    @Test
    @DisplayName("/ir chunks status con procesador IDLE informa estado adecuadamente")
    void testStatusWhenIdle() {
        boolean result = commandHandler.onCommand(testSender, testCommand, "ir", new String[]{"chunks", "status"});

        assertTrue(result);
        assertTrue(receivedMessages.stream().anyMatch(msg -> msg.contains("No hay ninguna cola de procesamiento")));
    }

    @Test
    @DisplayName("/ir chunks pause invoca pause en el procesador")
    void testPauseSubcommand() {
        boolean result = commandHandler.onCommand(testSender, testCommand, "ir", new String[]{"chunks", "pause"});

        assertTrue(result);
        assertTrue(processor.pauseCalled);
    }

    @Test
    @DisplayName("/ir chunks resume invoca resume en el procesador")
    void testResumeSubcommand() {
        boolean result = commandHandler.onCommand(testSender, testCommand, "ir", new String[]{"chunks", "resume"});

        assertTrue(result);
        assertTrue(processor.resumeCalled);
    }

    @Test
    @DisplayName("/ir chunks cancel invoca cancel en el procesador")
    void testCancelSubcommand() {
        boolean result = commandHandler.onCommand(testSender, testCommand, "ir", new String[]{"chunks", "cancel"});

        assertTrue(result);
        assertTrue(processor.cancelCalled);
    }

    @Test
    @DisplayName("/ir chunks retry invoca retry en el procesador")
    void testRetrySubcommand() {
        boolean result = commandHandler.onCommand(testSender, testCommand, "ir", new String[]{"chunks", "retry"});

        assertTrue(result);
        assertTrue(processor.retryCalled);
    }

    @Test
    @DisplayName("/ir chunks start <token> pasa el token al procesador")
    void testStartSubcommand() {
        boolean result = commandHandler.onCommand(testSender, testCommand, "ir", new String[]{"chunks", "start", "ABC123"});

        assertTrue(result);
        assertEquals("ABC123", processor.startTokenCalled);
    }

    @Test
    @DisplayName("Tab completion ofrece sugerencias correctas para /ir chunks")
    void testTabCompletion() {
        List<String> completions = commandHandler.onTabComplete(testSender, testCommand, "ir", new String[]{"chunks", ""});

        assertNotNull(completions);
        assertTrue(completions.contains("preview"));
        assertTrue(completions.contains("start"));
        assertTrue(completions.contains("status"));
        assertTrue(completions.contains("pause"));
        assertTrue(completions.contains("resume"));
        assertTrue(completions.contains("cancel"));
        assertTrue(completions.contains("retry"));

        List<String> radiusCompletions = commandHandler.onTabComplete(testSender, testCommand, "ir", new String[]{"chunks", "preview", ""});
        assertNotNull(radiusCompletions);
        assertTrue(radiusCompletions.contains("1000"));
    }
}
