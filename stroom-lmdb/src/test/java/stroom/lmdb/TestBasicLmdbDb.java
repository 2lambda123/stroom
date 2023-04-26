/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferPair;
import stroom.lmdb.LmdbEnv.WriteTxn;
import stroom.lmdb.UnSortedDupKey.UnsortedDupKeyFactory;
import stroom.lmdb.serde.IntegerSerde;
import stroom.lmdb.serde.StringSerde;
import stroom.lmdb.serde.UnSortedDupKeySerde;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.lmdb.serde.UnsignedLong;
import stroom.lmdb.serde.UnsignedLongSerde;
import stroom.test.common.TemporaryPathCreator;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestBasicLmdbDb extends AbstractLmdbDbTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestBasicLmdbDb.class);
    private static final int UNSIGNED_LONG_LEN = 4;
    private static final UnsignedBytes UNSIGNED_BYTES = UnsignedBytesInstances.ofLength(UNSIGNED_LONG_LEN);
    private final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();

    private final UnsignedLongSerde unsignedLongSerde = new UnsignedLongSerde(UNSIGNED_LONG_LEN, UNSIGNED_BYTES);

    private BasicLmdbDb<String, String> basicLmdbDb;
    private BasicLmdbDb<String, String> basicLmdbDb2;
    private BasicLmdbDb<Integer, String> basicLmdbDb3;
    private BasicLmdbDb<Integer, String> basicLmdbDb4;
    private BasicLmdbDb<String, UnsignedLong> basicLmdbDb5;


    @BeforeEach
    void setup() {
        basicLmdbDb = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new StringSerde(),
                new StringSerde(),
                "MyBasicLmdb");

        basicLmdbDb2 = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new StringSerde(),
                new StringSerde(),
                "MyBasicLmdb2");

        basicLmdbDb3 = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new IntegerSerde(),
                new StringSerde(),
                "MyBasicLmdb3");

        basicLmdbDb4 = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new IntegerSerde(),
                new StringSerde(),
                "MyBasicLmdb4",
                DbiFlags.MDB_CREATE,
                DbiFlags.MDB_INTEGERKEY);

        basicLmdbDb5 = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new StringSerde(),
                unsignedLongSerde,
                "MyBasicLmdb5");
    }

    @Test
    void testPutDuplicate_noOverwrite() {
        byteBufferPool.doWithBufferPair(50, 50, (keyBuffer, valueBuffer) -> {
            basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");
            basicLmdbDb.getValueSerde().serialize(valueBuffer, "MyValue");

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                PutOutcome putOutcome = basicLmdbDb.put(
                        writeTxn,
                        keyBuffer,
                        valueBuffer,
                        false);

                assertThat(putOutcome.isSuccess())
                        .isTrue();
                assertThat(putOutcome.isDuplicate())
                        .hasValue(false);

                putOutcome = basicLmdbDb.put(
                        writeTxn,
                        keyBuffer,
                        valueBuffer,
                        false);

                assertThat(putOutcome.isSuccess())
                        .isFalse();
                assertThat(putOutcome.isDuplicate())
                        .hasValue(true);
            });
            assertThat(basicLmdbDb.getEntryCount())
                    .isEqualTo(1);
        });
    }

    @Test
    void testPutDuplicate_overwrite() {
        byteBufferPool.doWithBufferPair(50, 50, (keyBuffer, valueBuffer) -> {
            basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");
            basicLmdbDb.getValueSerde().serialize(valueBuffer, "MyValue");

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                PutOutcome putOutcome = basicLmdbDb.put(
                        writeTxn,
                        keyBuffer,
                        valueBuffer,
                        true);

                assertThat(putOutcome.isSuccess())
                        .isTrue();
                assertThat(putOutcome.isDuplicate())
                        .hasValue(false);

                putOutcome = basicLmdbDb.put(
                        writeTxn,
                        keyBuffer,
                        valueBuffer,
                        true);

                assertThat(putOutcome.isSuccess())
                        .isTrue();
                assertThat(putOutcome.isDuplicate())
                        .hasValue(true);

            });
            assertThat(basicLmdbDb.getEntryCount())
                    .isEqualTo(1);
        });
    }

    @Test
    void testBufferMutationAfterPut() {

        byteBufferPool.doWithBufferPair(50, 50, (keyBuffer, valueBuffer) -> {
            basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");
            basicLmdbDb.getValueSerde().serialize(valueBuffer, "MyValue");

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                basicLmdbDb.put(writeTxn, keyBuffer, valueBuffer, false);
            });
            assertThat(basicLmdbDb.getEntryCount()).isEqualTo(1);

            // it is ok to mutate the buffers used in the put outside of the txn
            basicLmdbDb.getKeySerde().serialize(keyBuffer, "XX");
            basicLmdbDb.getValueSerde().serialize(valueBuffer, "YY");

            // now get the value again and it should be correct
            String val = basicLmdbDb.get("MyKey").get();

            assertThat(val).isEqualTo("MyValue");
        });
    }

    /**
     * This test is an example of how to abuse LMDB. If run it will crash the JVM
     * as a direct bytebuffer returned from a get() was mutated outside the transaction.
     */
    @Disabled // see javadoc above
    @Test
    void testBufferMutationAfterGet() {

        basicLmdbDb.put("MyKey", "MyValue", false);

        // the buffers have been returned to the pool and cleared
        assertThat(basicLmdbDb.getByteBufferPool().getCurrentPoolSize()).isEqualTo(2);

        assertThat(basicLmdbDb.getEntryCount()).isEqualTo(1);

        ByteBuffer keyBuffer = ByteBuffer.allocateDirect(50);
        basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");

        AtomicReference<ByteBuffer> valueBufRef = new AtomicReference<>();
        // now get the value again and it should be correct
        lmdbEnv.getWithReadTxn(txn -> {
            ByteBuffer valueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer).get();

            // hold on to the buffer for later
            valueBufRef.set(valueBuffer);

            String val = basicLmdbDb.getValueSerde().deserialize(valueBuffer);

            assertThat(val).isEqualTo("MyValue");

            return val;
        });

        // now mutate the value buffer outside any txn

        assertThat(valueBufRef.get().position()).isEqualTo(0);


        // This line will crash the JVM as we are mutating a directly allocated ByteBuffer
        // (that points to memory managed by LMDB) outside of a txn.
        basicLmdbDb.getValueSerde().serialize(valueBufRef.get(), "XXX");

    }

    @Test
    void testDupSupport() {
        BasicLmdbDb<String, String> db = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new StringSerde(),
                new StringSerde(),
                "dupDb",
                DbiFlags.MDB_CREATE,
                DbiFlags.MDB_DUPSORT);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            // Two entries with the same key
            db.put(writeTxn, "key2", "val2c", true);
            db.put(writeTxn, "key2", "val2a", true);
            db.put(writeTxn, "key2", "val2b", true);
            db.put(writeTxn, "key1", "val1", true);
            db.put(writeTxn, "key3", "val3", true);
        });

        db.logDatabaseContents();

        final List<Entry<String, String>> entries = lmdbEnv.getWithReadTxn(readTxn ->
                db.streamEntries(readTxn, KeyRange.all(), stream -> stream
                        .collect(Collectors.toList())));

        LOGGER.info("Entries:\n{}", AsciiTable.builder(entries)
                .withColumn(Column.of("key", Entry::getKey))
                .withColumn(Column.of("value", Entry::getValue))
                .build());

        Assertions.assertThat(entries)
                .satisfiesExactly(
                        entry1 -> Assertions.assertThat(entry1)
                                .isEqualTo(Map.entry("key1", "val1")),
                        entry2 -> Assertions.assertThat(entry2)
                                .isEqualTo(Map.entry("key2", "val2a")),
                        entry3 -> Assertions.assertThat(entry3)
                                .isEqualTo(Map.entry("key2", "val2b")),
                        entry4 -> Assertions.assertThat(entry4)
                                .isEqualTo(Map.entry("key2", "val2c")),
                        entry5 -> Assertions.assertThat(entry5)
                                .isEqualTo(Map.entry("key3", "val3")));
    }

    @Test
    void testDupSupport_unsortedValues() {
        BasicLmdbDb<UnSortedDupKey<String>, String> db = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new UnSortedDupKeySerde<>(new StringSerde()),
                new StringSerde(),
                "dupDb",
                DbiFlags.MDB_CREATE);

        final UnsortedDupKeyFactory<String> keyFactory = UnSortedDupKey.createFactory(String.class);
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            // Three entries with the same key
            db.put(writeTxn, keyFactory.createUnsortedKey("key2"), "val2c", true);
            db.put(writeTxn, keyFactory.createUnsortedKey("key2"), "val2a", true);
            db.put(writeTxn, keyFactory.createUnsortedKey("key2"), "val2b", true);
            db.put(writeTxn, keyFactory.createUnsortedKey("key1"), "val1", true);
            // Two identical entries
            db.put(writeTxn, keyFactory.createUnsortedKey("key3"), "val3", true);
            db.put(writeTxn, keyFactory.createUnsortedKey("key3"), "val3", true);
        });

        db.logDatabaseContents();

        final List<Entry<UnSortedDupKey<String>, String>> entries = lmdbEnv.getWithReadTxn(readTxn ->
                db.streamEntries(readTxn, KeyRange.all(), stream -> stream
                        .collect(Collectors.toList())));

        LOGGER.info("Entries:\n{}", AsciiTable.builder(entries)
                .withColumn(Column.of("key", Entry::getKey))
                .withColumn(Column.of("value", Entry::getValue))
                .build());

        Assertions.assertThat(entries)
                .extracting(entry -> entry.getKey().getKey())
                .containsExactly("key1",
                        "key2",
                        "key2",
                        "key2",
                        "key3",
                        "key3");

        Assertions.assertThat(entries)
                .extracting(Entry::getValue)
                .containsExactly("val1",
                        "val2c",
                        "val2a",
                        "val2b",
                        "val3",
                        "val3");
    }

    @Test
    void testGetAsBytes() {

        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);
        basicLmdbDb.put("key3", "value3", false);

        lmdbEnv.doWithReadTxn(txn -> {
            Optional<ByteBuffer> optKeyBuffer = basicLmdbDb.getAsBytes(txn, "key2");

            assertThat(optKeyBuffer).isNotEmpty();
        });
    }

    @Test
    void testGetAsBytes2() {

        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);
        basicLmdbDb.put("key3", "value3", false);

        lmdbEnv.doWithReadTxn(txn -> {

            try (PooledByteBuffer pooledKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {
                ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                basicLmdbDb.serializeKey(keyBuffer, "key2");
                Optional<ByteBuffer> optValueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer);

                assertThat(optValueBuffer).isNotEmpty();
                String val = basicLmdbDb.deserializeKey(optValueBuffer.get());
                assertThat(val).isEqualTo("value2");
            }
        });
    }

    @Test
    void testValueMutation() {
        final List<Entry<String, UnsignedLong>> entries = IntStream.rangeClosed(1, 10)
                .boxed()
                .map(i -> new SimpleEntry<>("key-" + i, UnsignedLong.of(i, UNSIGNED_LONG_LEN)))
                .collect(Collectors.toList());

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            LOGGER.logDurationIfDebugEnabled(() -> {
                entries
                        .forEach(entry -> {
                            try (final PooledByteBufferPair pooledBufferPair = basicLmdbDb5.getPooledBufferPair()) {
                                final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                final ByteBuffer valBuff = pooledBufferPair.getValueBuffer();
                                basicLmdbDb5.serializeKey(keyBuff, entry.getKey());
                                basicLmdbDb5.serializeValue(valBuff, entry.getValue());
                                basicLmdbDb5.getLmdbDbi().put(
                                        writeTxn,
                                        keyBuff,
                                        valBuff,
                                        PutFlags.MDB_NOOVERWRITE);
                            }
                        });
            }, "initial load");
        });

        if (lmdbEnv.getEnvFlags().contains(EnvFlags.MDB_WRITEMAP)) {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                LOGGER.logDurationIfDebugEnabled(() -> {
                    entries
                            .forEach(entry -> {
                                try (final PooledByteBufferPair pooledBufferPair = basicLmdbDb5.getPooledBufferPair()) {
                                    final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                    basicLmdbDb5.serializeKey(keyBuff, entry.getKey());
                                    final ByteBuffer valBuff = basicLmdbDb5.getLmdbDbi().get(writeTxn,
                                            keyBuff);

                                    // Mutating the value buffer without a copy only works if MDB_WRITEMAP is set
                                    UNSIGNED_BYTES.increment(valBuff);
                                }
                            });
                }, "increment in place");
            });
        } else {
            LOGGER.info("{} not set on env so can't increment in place", EnvFlags.MDB_WRITEMAP);
        }
//        basicLmdbDb5.logDatabaseContents();

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            LOGGER.logDurationIfDebugEnabled(() -> {
                entries
                        .forEach(entry -> {
                            try (final PooledByteBufferPair pooledBufferPair = basicLmdbDb5.getPooledBufferPair()) {
                                final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                final ByteBuffer newValBuff = pooledBufferPair.getValueBuffer();
                                basicLmdbDb5.serializeKey(keyBuff, entry.getKey());
                                // Mutate the value using get/put via a copy of the buffer. This is the only
                                // way if MDB_WRITEMAP is not set.
                                final ByteBuffer dbValBuff = basicLmdbDb5.getLmdbDbi().get(
                                        writeTxn,
                                        keyBuff);
                                ByteBufferUtils.copy(dbValBuff, newValBuff);

                                UNSIGNED_BYTES.increment(newValBuff);
                                basicLmdbDb5.put(writeTxn, keyBuff, newValBuff, false);
                            }
                        });
            }, "increment with get/put");
        });
//        basicLmdbDb5.logDatabaseContents();
    }

    @Test
    void testLoadingSortedKeys() {
        final List<Entry<String, String>> entries = IntStream.rangeClosed(1, 10)
                .boxed()
                .map(i -> new SimpleEntry<>("key-" + i, "value-" + i))
                .collect(Collectors.toList());

        // Random order for 1st load
        Collections.shuffle(entries);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            LOGGER.logDurationIfDebugEnabled(() -> {
                entries
                        .forEach(entry -> {
                            try (final PooledByteBufferPair pooledBufferPair = basicLmdbDb.getPooledBufferPair()) {
                                final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                final ByteBuffer valBuff = pooledBufferPair.getValueBuffer();
                                basicLmdbDb.serializeKey(keyBuff, entry.getKey());
                                basicLmdbDb.serializeValue(valBuff, entry.getValue());
                                basicLmdbDb.getLmdbDbi().put(
                                        writeTxn,
                                        keyBuff,
                                        valBuff,
                                        PutFlags.MDB_NOOVERWRITE);
                            }
                        });
            }, "un-sorted puts");
        });

        // Read all entries back out in lmdb sort order
        final List<Entry<String, String>> sortedEntries = lmdbEnv.getWithReadTxn(readTxn ->
                basicLmdbDb.streamEntries(readTxn, KeyRange.all(), stream ->
                        stream.collect(Collectors.toList())));

        // Now load them into the other db in order using MDB_APPEND to tell LMDB they are in order
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            LOGGER.logDurationIfDebugEnabled(() -> {
                sortedEntries
                        .forEach(entry -> {
                            try (final PooledByteBufferPair pooledBufferPair = basicLmdbDb2.getPooledBufferPair()) {
                                final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                final ByteBuffer valBuff = pooledBufferPair.getValueBuffer();
                                basicLmdbDb2.serializeKey(keyBuff, entry.getKey());
                                basicLmdbDb2.serializeValue(valBuff, entry.getValue());
                                basicLmdbDb2.getLmdbDbi().put(
                                        writeTxn,
                                        keyBuff,
                                        valBuff,
                                        PutFlags.MDB_NOOVERWRITE,
                                        PutFlags.MDB_APPEND);
                            }
                        });
            }, "sorted puts");
        });

        // Now do all the puts again overwriting values
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            LOGGER.logDurationIfDebugEnabled(() -> {
                sortedEntries
                        .forEach(entry -> {
                            try (final PooledByteBufferPair pooledBufferPair = basicLmdbDb2.getPooledBufferPair()) {
                                final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                final ByteBuffer valBuff = pooledBufferPair.getValueBuffer();
                                basicLmdbDb2.serializeKey(keyBuff, entry.getKey());
                                basicLmdbDb2.serializeValue(valBuff, entry.getValue());
                                basicLmdbDb2.getLmdbDbi().put(
                                        writeTxn,
                                        keyBuff,
                                        valBuff);
                                // Can't user MDB_APPEND now as it will barf when it finds an existing key
                            }
                        });
            }, "sorted puts 2");
        });
    }

    @Test
    void testKeyRange() {
        basicLmdbDb.put("key11", "value1", false);
        basicLmdbDb.put("key12", "value1", false);
        basicLmdbDb.put("key13", "value1", false);
        basicLmdbDb.put("key21", "value2", false);
        basicLmdbDb.put("key22", "value2", false);
        basicLmdbDb.put("key23", "value2", false);
        basicLmdbDb.put("key31", "value3", false);
        basicLmdbDb.put("key32", "value3", false);
        basicLmdbDb.put("key33", "value3", false);

        lmdbEnv.doWithReadTxn(txn -> {

            try (PooledByteBuffer pooledStartKeyBuffer = basicLmdbDb.getPooledKeyBuffer();
                    PooledByteBuffer pooledEndKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {

                ByteBuffer startKeyBuffer = pooledStartKeyBuffer.getByteBuffer();
                ByteBuffer endKeyBuffer = pooledEndKeyBuffer.getByteBuffer();

                final String startKey = "key2";
                final String endKey = "key3";
                basicLmdbDb.serializeKey(startKeyBuffer, startKey);
                basicLmdbDb.serializeKey(endKeyBuffer, endKey);
                LOGGER.info("{} => {}",
                        ByteBufferUtils.byteBufferInfo(startKeyBuffer),
                        ByteBufferUtils.byteBufferInfo(endKeyBuffer));

                final KeyRange<ByteBuffer> keyRange = KeyRange.closedOpen(startKeyBuffer, endKeyBuffer);

                basicLmdbDb.forEachEntryAsBytes(txn, keyRange, kvTuple -> {
                    LOGGER.info("{} - {}",
                            ByteBufferUtils.byteBufferInfo(kvTuple.key()),
                            ByteBufferUtils.byteBufferInfo(kvTuple.val()));
                });

                final List<String> keysFound = new ArrayList<>();

                basicLmdbDb.forEachEntry(txn, KeyRange.closedOpen(startKey, endKey), kvTuple -> {
                    keysFound.add(kvTuple.getKey());
                });

                Assertions.assertThat(keysFound)
                        .containsExactly("key21", "key22", "key23");
            }
        });
    }

    @Test
    void testStreamEntries() {
        final int count = 5;
        populateDb(count);

        final List<Entry<String, String>> entries = lmdbEnv.getWithReadTxn(txn ->
                basicLmdbDb.streamEntries(txn, KeyRange.all(), stream ->
                        stream
                                .peek(entry -> LOGGER.info("key: '{}', value: '{}'",
                                        entry.getKey(), entry.getValue()))
                                .collect(Collectors.toList())));

        assertThat(entries)
                .hasSize(count);
        assertThat(entries)
                .extracting(Entry::getKey)
                .containsExactly(
                        "01",
                        "02",
                        "03",
                        "04",
                        "05");
    }

    @Test
    void testStreamEntriesWithFilter() {
        final int count = 10;
        populateDb(count);

        final List<Entry<String, String>> entries = lmdbEnv.getWithReadTxn(txn ->
                basicLmdbDb.streamEntries(txn, KeyRange.all(), stream ->
                        stream
                                .filter(entry -> {
                                    int i = Integer.parseInt(entry.getKey());
                                    return i > 3 && i <= 7;
                                })
                                .peek(entry -> LOGGER.info("key: '{}', value: '{}'",
                                        entry.getKey(), entry.getValue()))
                                .collect(Collectors.toList())));

        assertThat(entries)
                .hasSize(4);
        assertThat(entries)
                .extracting(Entry::getKey)
                .containsExactly(
                        "04",
                        "05",
                        "06",
                        "07");
    }

    @Test
    void testStreamEntriesWithKeyRange() {
        final int count = 15;
        populateDb(count);

        final KeyRange<String> keyRange = KeyRange.closed("06", "10");
        final List<Entry<String, String>> entries = lmdbEnv.getWithReadTxn(txn ->
                basicLmdbDb.streamEntries(txn, keyRange, stream ->
                        stream
                                .peek(entry -> LOGGER.info("key: '{}', value: '{}'",
                                        entry.getKey(), entry.getValue()))
                                .collect(Collectors.toList())));

        assertThat(entries)
                .hasSize(5);
        assertThat(entries)
                .extracting(Entry::getKey)
                .containsExactly(
                        "06",
                        "07",
                        "08",
                        "09",
                        "10");
    }

    @Test
    void testKeyReuse() {
        // key 1 => key2 => value2 & value 3
        basicLmdbDb.put("key1", "key2", false);
        basicLmdbDb.put("key2", "value2", false);

        // different DB with same key in it so we can test two lookups using same key
        basicLmdbDb2.put("key2", "value3", false);

        lmdbEnv.doWithReadTxn(txn -> {
            ByteBuffer keyBuffer = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer, "key1");
            ByteBuffer keyBufferCopy = keyBuffer.asReadOnlyBuffer();

            ByteBuffer valueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer).get();
            ByteBuffer keyBuffer2 = ByteBufferUtils.copyToDirectBuffer(valueBuffer);
            String value = basicLmdbDb.deserializeValue(valueBuffer);
            ByteBuffer valueBufferCopy = valueBuffer.asReadOnlyBuffer();

            assertThat(value).isEqualTo("key2");
            assertThat(keyBuffer).isEqualTo(keyBufferCopy);
            assertThat(valueBuffer).isEqualTo(valueBufferCopy);
            assertThat(valueBuffer).isEqualTo(keyBuffer2);

            // now use the value from the last get() as the key for a new get()
            ByteBuffer valueBuffer2 = basicLmdbDb.getAsBytes(txn, valueBuffer).get();

            String value2 = basicLmdbDb.deserializeValue(valueBuffer2);
            String valueBufferDeserialised = basicLmdbDb.deserializeKey(valueBuffer);

            assertThat(value2).isEqualTo("value2");

            // The second get() has overwritten our original valueBuffer with the value
            // of the second get(). This is because the txn essentially holds a cursor
            // whose position is updated by the get and that cursor is bound to the value
            // buffer returned by the get. The value from the get() can be used as a key
            // in another get() once, but that second get() will mutate it prevent it from
            // being used as a key in another get().
            assertThat(valueBufferDeserialised).isEqualTo("value2");
            assertThat(valueBuffer).isEqualTo(valueBuffer2);
            assertThat(valueBuffer).isNotEqualTo(valueBufferCopy);
            assertThat(keyBuffer2).isNotEqualTo(valueBuffer);

            // We can't use valueBuffer for our key here is it now points to "value2"
            ByteBuffer valueBuffer3 = basicLmdbDb2.getAsBytes(txn, keyBuffer2).get();

            String value3 = basicLmdbDb2.deserializeValue(valueBuffer3);

            assertThat(value3).isEqualTo("value3");
            assertThat(keyBuffer).isEqualTo(keyBufferCopy);
        });
    }

    @Test
    void testValueReuse() {
        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);

        lmdbEnv.doWithReadTxn(txn -> {

            ByteBuffer keyBuffer1 = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer1, "key1");

            ByteBuffer valueBuffer1 = basicLmdbDb.getAsBytes(txn, keyBuffer1).get();
            ByteBuffer valueBuffer1Copy = valueBuffer1.asReadOnlyBuffer();
            assertThat(valueBuffer1Copy).isEqualTo(valueBuffer1);
            String value1 = basicLmdbDb.deserializeValue(valueBuffer1);

            assertThat(value1).isEqualTo("value1");

            // now do another get on a different key to get a different value
            ByteBuffer keyBuffer2 = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer2, "key2");
            ByteBuffer valueBuffer2 = basicLmdbDb.getAsBytes(txn, keyBuffer2).get();

            String value2 = basicLmdbDb.deserializeValue(valueBuffer2);
            assertThat(value2).isEqualTo("value2");

            // The valueBuffer1 is tied to the txn's cursor which has now moved to key2 => value2,
            // thus valueBuffer1 now contains "value2".
            value1 = basicLmdbDb.deserializeValue(valueBuffer1);
            assertThat(value1).isEqualTo("value2");
            assertThat(valueBuffer1Copy).isNotEqualTo(valueBuffer1);
        });
    }

    @Test
    void testKeyUseOutsideCursor() {
        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);

        lmdbEnv.doWithReadTxn(txn -> {

            ByteBuffer keyBuffer1 = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer1, "key1");

            ByteBuffer startKeyBuf = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(startKeyBuf, "key1");

            // Same start/end key
            final KeyRange<ByteBuffer> keyRange = KeyRange.closed(startKeyBuf, startKeyBuf);

            ByteBuffer foundKeyBuffer = null;
            try (CursorIterable<ByteBuffer> cursorIterable = basicLmdbDb.iterate(txn, keyRange)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                if (iterator.hasNext()) {
                    foundKeyBuffer = iterator.next().key();
                }
            }

            // foundKeyBuffer is not out of scope of the cursor
            Assertions.assertThat(foundKeyBuffer)
                    .isNotNull();

            assertThat(basicLmdbDb.deserializeKey(foundKeyBuffer))
                    .isEqualTo("key1");

            // now do another get on a different key to get a different value
            ByteBuffer keyBuffer2 = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer2, "key2");
            basicLmdbDb.getAsBytes(txn, keyBuffer2).get();

            // Now bake sure the buffer we got in the cursor is still the same
            // and has not been affected by the other get.
            assertThat(basicLmdbDb.deserializeKey(foundKeyBuffer))
                    .isEqualTo("key1");
        });
    }

    @Test
    void testVerifyNumericKeyOrder() {

        // Ensure entries come back in the right order
        final List<Entry<Integer, String>> data = new ArrayList<>(List.of(
                Map.entry(1, "val1"),
                Map.entry(2, "val2"),
                Map.entry(3, "val3"),
                Map.entry(4, "val4")));

        Collections.shuffle(data, new Random(12345L));

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            data.forEach(entry -> {
                basicLmdbDb3.put(writeTxn, entry.getKey(), entry.getValue(), false);
            });
        });

        final KeyRange<Integer> keyRangeAll = KeyRange.all();

        final List<Integer> output = lmdbEnv.getWithReadTxn(readTxn ->
                basicLmdbDb3.streamEntries(readTxn, keyRangeAll, stream ->
                        stream
                                .map(Entry::getKey)
                                .collect(Collectors.toList())));

        // Verify key order
        Assertions.assertThat(output)
                .containsExactly(
                        1,
                        2,
                        3,
                        4);
    }

    /**
     * This is more of a manual performance test for comparing the difference between
     * puts in integer order vs in random order. Also compares the impact of the INTEGER_KEY
     * dbi flag, which seems to slow things down a fair bit.
     */
    @Test
    void testLoadOrderAndIntKeyPerformance() {

        // TODO: 18/04/2023 I think this test is wrong, see https://github.com/lmdbjava/lmdbjava/wiki/Keys#numeric-keys
        //  Think it needs to be long not integer and ensure the correct endianness.

//        final int iterations = 10_000_000;
        final int iterations = 10;

        LOGGER.info("info {}", basicLmdbDb3.getDbInfo());

        // Ensure entries come back in the right order
        final List<Tuple2<Integer, String>> ascendingData = IntStream
                .range(0, iterations)
                .boxed()
                .map(i -> Tuple.of(i, String.format("Val %010d", i)))
                .collect(Collectors.toList());

        Assertions.assertThat(ascendingData)
                .hasSize(iterations);

        Random random = new Random();
        final List<Tuple2<Integer, String>> randomData = IntStream
                .range(Integer.MAX_VALUE - iterations, Integer.MAX_VALUE)
                .boxed()
                .sorted(Comparator.comparingInt(i -> random.nextInt(iterations)))
                .map(i -> Tuple.of(i, String.format("Val %010d", i)))
                .collect(Collectors.toList());

        Assertions.assertThat(ascendingData)
                .hasSize(iterations);

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                ascendingData.forEach(tuple -> {
                    basicLmdbDb3.put(writeTxn, tuple._1(), tuple._2(), false);
                });
            });
        }, "Ascending");

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                randomData.forEach(tuple -> {
                    basicLmdbDb3.put(writeTxn, tuple._1(), tuple._2(), false);
                });
            });
        }, "Random");

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                ascendingData.forEach(tuple -> {
                    basicLmdbDb4.put(writeTxn, tuple._1(), tuple._2(), false);
                });
            });
        }, "Ascending (INTEGER_KEY)");

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                randomData.forEach(tuple -> {
                    basicLmdbDb4.put(writeTxn, tuple._1(), tuple._2(), false);
                });
            });
        }, "Random (INTEGER_KEY)");

        if (iterations < 50) {
            basicLmdbDb3.logDatabaseContents(LOGGER::info);
            basicLmdbDb3.logRawDatabaseContents(LOGGER::info);
            basicLmdbDb4.logDatabaseContents(LOGGER::info);
            basicLmdbDb4.logRawDatabaseContents(LOGGER::info);
        }
        LOGGER.info("entry count: " + basicLmdbDb3.getEntryCount());
        LOGGER.info("entry count: " + basicLmdbDb4.getEntryCount());
    }

    @Test
    void testMaxReaders() {
        assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(0);
        IntStream.rangeClosed(1, 20).forEach(i -> {
            basicLmdbDb.put(buildKey(i), buildValue(i), false);

        });
        // Show that writes to the db do not effect the num readers high water mark
        assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(0);

        basicLmdbDb.get(buildKey(1));

        assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(1);
    }

    /**
     * Intended for manual running at high iteration count to test lookup difference
     */
    @Test
    void testGetVsCursorPerformance() {

//        final int iterations = 100_000;
//        final int iterations = 1_000_000;
//        final int iterations = 10_000_000;
        final int iterations = 10;

        LOGGER.info("info {}", basicLmdbDb3.getDbInfo());

        // Ensure entries come back in the right order
        final List<Tuple2<Integer, String>> ascendingData = IntStream
                .range(0, iterations)
                .boxed()
                .map(i -> Tuple.of(i, String.format("Val %010d", i)))
                .collect(Collectors.toList());

        Assertions.assertThat(ascendingData)
                .hasSize(iterations);

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                ascendingData.forEach(tuple -> {
                    basicLmdbDb3.put(writeTxn, tuple._1(), tuple._2(), false);
                });
            });
        }, "Ascending puts");

        if (iterations < 50) {
            basicLmdbDb3.logDatabaseContents(LOGGER::info);
            basicLmdbDb3.logRawDatabaseContents(LOGGER::info);
        }

        LOGGER.info("entry count: " + basicLmdbDb3.getEntryCount());

        final int runCount = 3;
        // Do it a few times so the jvm can warm up
        for (int i = 0; i < runCount; i++) {
            LOGGER.logDurationIfInfoEnabled(() -> {
                lmdbEnv.doWithReadTxn(writeTxn -> {
                    ascendingData.forEach(tuple -> {
                        final Integer key = tuple._1();
                        final String val = basicLmdbDb3.get(writeTxn, key)
                                .orElseThrow(() ->
                                        new RuntimeException("No value for key " + key));
                        assertThat(val)
                                .isEqualTo(tuple._2());
                    });
                });
            }, "Gets");
        }

        for (int i = 0; i < runCount; i++) {
            LOGGER.logDurationIfInfoEnabled(() -> {
                lmdbEnv.doWithReadTxn(readTxn -> {
                    ascendingData.forEach(tuple -> {
                        final Integer key = tuple._1();

                        try (final PooledByteBuffer startKeyBuf = basicLmdbDb3.getPooledKeyBuffer();
                                final PooledByteBuffer endKeyBuf = basicLmdbDb3.getPooledKeyBuffer()) {

                            basicLmdbDb3.serializeKey(startKeyBuf.getByteBuffer(), key);
                            basicLmdbDb3.serializeKey(endKeyBuf.getByteBuffer(), key);

                            final KeyRange<ByteBuffer> keyRange = KeyRange.closed(
                                    startKeyBuf.getByteBuffer(),
                                    endKeyBuf.getByteBuffer());

                            try (final CursorIterable<ByteBuffer> cursorIterable = basicLmdbDb3.iterate(
                                    readTxn, keyRange)) {

                                final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                                String val = null;
                                if (iterator.hasNext()) {
                                    val = basicLmdbDb3.deserializeValue(iterator.next().val());
                                } else {
                                    fail(LogUtil.message("Key {} not found", key));
                                }

                                assertThat(val)
                                        .isEqualTo(tuple._2());
                            }
                        }
                    });
                });
            }, "Cursor gets");
        }
    }

    /**
     * Ensure two envs can operate independently of each other, i.e. both hold a write txn open
     * in different threads.
     */
    @Test
    void testConcurrentEnvs() throws IOException, ExecutionException, InterruptedException {

        try (TemporaryPathCreator temporaryPathCreator = new TemporaryPathCreator()) {
            final EnvFlags[] envFlags = new EnvFlags[]{
                    EnvFlags.MDB_NOTLS
            };
            LOGGER.info("baseDir: {}", temporaryPathCreator.getBaseTempDir().toAbsolutePath().normalize());

            final BasicLmdbDb<String, String> basicLmdb1 = createDb(temporaryPathCreator, envFlags, "1");
            final BasicLmdbDb<String, String> basicLmdb2 = createDb(temporaryPathCreator, envFlags, "2");

            final LmdbEnv lmdbEnv1 = basicLmdb1.getLmdbEnvironment();
            final LmdbEnv lmdbEnv2 = basicLmdb2.getLmdbEnvironment();

            final CountDownLatch countDownLatch = new CountDownLatch(2);

            final CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
                LOGGER.info("Opening writeTxn1");
                final WriteTxn writeTxn1 = lmdbEnv1.openWriteTxn();
                LOGGER.info("writeTxn1 open");
                basicLmdb1.put(writeTxn1.getTxn(), "1", "one", true);
                countDownLatch.countDown();
                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                try {
                    LOGGER.info("Closing writeTxn1");
                    writeTxn1.commit();
                    writeTxn1.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            final CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
                LOGGER.info("Opening writeTxn2");
                final WriteTxn writeTxn2 = lmdbEnv2.openWriteTxn();
                LOGGER.info("writeTxn2 open");
                basicLmdb1.put(writeTxn2.getTxn(), "2", "two", true);
                countDownLatch.countDown();
                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                try {
                    LOGGER.info("Closing writeTxn2");
                    writeTxn2.commit();
                    writeTxn2.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            future1.get();
            future2.get();

            Assertions.assertThat(basicLmdb1.get("1"))
                    .hasValue("one");
            Assertions.assertThat(basicLmdb2.get("2"))
                    .hasValue("two");
        }
    }

    private BasicLmdbDb<String, String> createDb(final TemporaryPathCreator temporaryPathCreator,
                                                 final EnvFlags[] envFlags,
                                                 final String id) {
        final LmdbEnv lmdbEnv = new LmdbEnvFactory(
                temporaryPathCreator,
                temporaryPathCreator.getTempDirProvider(),
                LmdbLibraryConfig::new)
                .builder(temporaryPathCreator.getBaseTempDir())
                .withSubDirectory("env" + id)
                .withMapSize(getMaxSizeBytes())
                .withMaxDbCount(10)
                .withEnvFlags(envFlags)
                .makeWritersBlockReaders()
                .build();

        return new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new StringSerde(),
                new StringSerde(),
                "db" + id);
    }

    private void populateDb(final int count) {
        final Random random = new Random(123);
        final List<Entry<String, String>> entryList = IntStream.rangeClosed(1, count)
                .boxed()
                .map(i ->
                        Map.entry(buildKey(i), buildValue(i)))
                .collect(Collectors.toList());
        // Consistent seeded shuffle
        Collections.shuffle(entryList, random);
        // pad the keys to a fixed length, so they are stored in number order
        entryList.forEach(entry -> {
            basicLmdbDb.put(entry.getKey(), entry.getValue(), false);
        });

        basicLmdbDb.logDatabaseContents(LOGGER::info);
    }

    private String buildKey(int i) {
        return String.format("%02d", i);
    }

    private String buildValue(int i) {
        return "value" + i;
    }
}
