package stroom.test.common;

import stroom.test.common.DynamicTestBuilder.InitialBuilder;
import stroom.util.NullSafe;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.DynamicTest;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Useful utility methods for junit tests
 */
public class TestUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestUtil.class);

    private TestUtil() {
        // Static Utils only
    }

    /**
     * A builder for creating a Junit5 {@link DynamicTest} {@link Stream} for use with the
     * {@link org.junit.jupiter.api.TestFactory} annotation.
     * Simplifies the testing of multiple inputs to the same test method.
     * See TestTestUtil for examples of how to use this builder.
     * NOTE: @{@link org.junit.jupiter.api.BeforeEach} and @{@link org.junit.jupiter.api.AfterEach}
     * are only called at the {@link org.junit.jupiter.api.TestFactory} level, NOT for each
     * {@link DynamicTest}.
     */
    public static InitialBuilder buildDynamicTestStream() {
        return new InitialBuilder();
    }

    /**
     * Logs to info message followed by ':\n', followed by an ASCII table
     * of the map entries
     */
    public static <K, V> void dumpMapToInfo(final String message,
                                            final Map<K, V> map) {
        LOGGER.info("{}:\n{}", message, AsciiTable.from(map));
    }

    /**
     * Logs to debug message followed by ':\n', followed by an ASCII table
     * of the map entries
     */
    public static <K, V> void dumpMapToDebug(final String message,
                                             final Map<K, V> map) {
        LOGGER.debug("{}:\n{}", message, AsciiTable.from(map));
    }

    /**
     * Repeatedly call test with pollFrequency until it returns true, or timeout is reached.
     * If timeout is reached before test returns true a {@link RuntimeException} is thrown.
     *
     * @param valueSupplier   Supplier of the value to test. This will be called repeatedly until
     *                        its return value match requiredValue, or timeout is reached.
     * @param requiredValue   The value that valueSupplier is required to ultimately return.
     * @param messageSupplier Supplier of the name of the thing being waited for.
     * @param timeout         The timeout duration after which waitForIt will give up and throw
     *                        a {@link RuntimeException}.
     * @param pollFrequency   The time between calls to valueSupplier.
     */
    public static <T> void waitForIt(final Supplier<T> valueSupplier,
                                     final T requiredValue,
                                     final Supplier<String> messageSupplier,
                                     final Duration timeout,
                                     final Duration pollFrequency) {

        final Instant startTime = Instant.now();
        final Instant endTime = startTime.plus(timeout);
        T currValue = null;
        while (Instant.now().isBefore(endTime)) {
            currValue = valueSupplier.get();
            if (Objects.equals(currValue, requiredValue)) {
                LOGGER.debug("Waited {}", Duration.between(startTime, Instant.now()));
                return;
            } else {
                ThreadUtil.sleepIgnoringInterrupts(pollFrequency.toMillis());
            }
        }

        // Timed out so throw
        throw new RuntimeException(LogUtil.message("Timed out (timeout: {}) waiting for '{}' to be '{}'. " +
                        "Last value '{}'",
                timeout,
                messageSupplier.get(),
                requiredValue,
                currValue));
    }

    /**
     * Repeatedly call test with pollFrequency until it returns true, or timeout is reached.
     * If timeout is reached before test returns true a {@link RuntimeException} is thrown.
     * A default timeout of 5s is used with a default pollFrequency of 1ms.
     *
     * @param valueSupplier   Supplier of the value to test. This will be called repeatedly until
     *                        its return value match requiredValue, or timeout is reached.
     * @param requiredValue   The value that valueSupplier is required to ultimately return.
     * @param messageSupplier Supplier of the name of the thing being waited for.
     */
    public static <T> void waitForIt(final Supplier<T> valueSupplier,
                                     final T requiredValue,
                                     final Supplier<String> messageSupplier) {
        waitForIt(
                valueSupplier,
                requiredValue,
                messageSupplier,
                Duration.ofSeconds(5),
                Duration.ofMillis(1));
    }

    /**
     * Repeatedly call test with pollFrequency until it returns true, or timeout is reached.
     * If timeout is reached before test returns true a {@link RuntimeException} is thrown.
     * A default timeout of 5s is used with a default pollFrequency of 1ms.
     *
     * @param valueSupplier Supplier of the value to test. This will be called repeatedly until
     *                      its return value match requiredValue, or timeout is reached.
     * @param requiredValue The value that valueSupplier is required to ultimately return.
     * @param message       The name of the thing being waited for.
     */
    public static <T> void waitForIt(final Supplier<T> valueSupplier,
                                     final T requiredValue,
                                     final String message) {
        waitForIt(
                valueSupplier,
                requiredValue,
                () -> message,
                Duration.ofSeconds(5),
                Duration.ofMillis(1));
    }

    /**
     * Run runnable with a temporary log level for the supplied classes
     */
    public static void withTemporaryLogLevel(final Level logLevel,
                                             final Class<?> clazz,
                                             final Runnable runnable) {
        withTemporaryLogLevel(logLevel, List.of(clazz), runnable);
    }

    /**
     * Run runnable with a temporary log level for the supplied classes
     */
    public static void withTemporaryLogLevel(final Level logLevel,
                                             final Collection<Class<?>> classes,
                                             final Runnable runnable) {
        final Map<Class<?>, Level> existingLevels = new HashMap<>();

        for (final Class<?> clazz : classes) {
            final ch.qos.logback.classic.Logger logger = getLogbackLogger(clazz);
            Objects.requireNonNull(logger);
            final Level existingLevel = logger.getLevel();
            existingLevels.put(clazz, existingLevel);
        }

        try {
            for (final Class<?> clazz : classes) {
                final ch.qos.logback.classic.Logger logger = getLogbackLogger(clazz);
                logger.setLevel(logLevel);
            }
            runnable.run();
        } finally {
            for (final Class<?> clazz : classes) {
                final ch.qos.logback.classic.Logger logger = getLogbackLogger(clazz);
                NullSafe.consume(existingLevels.get(clazz), logger::setLevel);
            }
        }
    }

    private static ch.qos.logback.classic.Logger getLogbackLogger(final Class<?> clazz) {
        final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        return loggerContext.getLogger(clazz);
    }
}
