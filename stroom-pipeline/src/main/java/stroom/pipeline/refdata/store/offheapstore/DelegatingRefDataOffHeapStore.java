package stroom.pipeline.refdata.store.offheapstore;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.feed.shared.FeedDoc;
import stroom.lmdb.LmdbEnv;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.ProcessingInfoResponse;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.time.StroomDuration;
import stroom.util.time.TimeUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Is a front for multiple {@link RefDataOffHeapStore} instances, one per feed.
 * It either delegates to the appropriate store if the stream id is know (so the feed can be derived
 * from the stream ID), or it delegates to all stores and aggregates the results.
 */
@Singleton
public class DelegatingRefDataOffHeapStore implements RefDataStore, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DelegatingRefDataOffHeapStore.class);

    private static final String CACHE_NAME = "Reference Data - Meta ID to Ref Store Cache";
    private static final String PARAM_NAME_FEED = "feed";
    protected static final String DELIMITER = "___";
    protected static final Pattern DELIMITER_PATTERN = Pattern.compile(DELIMITER);

    private final Provider<ReferenceDataConfig> referenceDataConfigProvider;
    private final RefDataLmdbEnv.Factory refDataLmdbEnvFactory;
    private final RefDataOffHeapStore.Factory refDataOffHeapStoreFactory;
    private final MetaService metaService;
    private final DocRefInfoService docRefInfoService;
    private final SecurityContext securityContext;
    private final PathCreator pathCreator;

    // feed => refDataOffHeapStore, shouldn't be that many ref feeds
    // Feeds are immutable things too so no TTL needed
    private final Map<String, RefDataOffHeapStore> feedNameToStoreMap = new ConcurrentHashMap<>();

    // Following items all relate to migration of a legacy ref data store that may or may not
    // be present depending on when the instance was first deployed.
    private volatile RefDataOffHeapStore legacyRefDataStore;
    private volatile boolean migrationCheckRequired = false;
    private final Set<Long> migratedRefStreamIds = ConcurrentHashMap.newKeySet();

    // Save us hitting the db all the time
    private final LoadingStroomCache<Long, FeedSpecificStore> metaIdToFeedStoreCache;

    @Inject
    @SuppressWarnings("unused")
    public DelegatingRefDataOffHeapStore(final Provider<ReferenceDataConfig> referenceDataConfigProvider,
                                         final CacheManager cacheManager,
                                         final RefDataLmdbEnv.Factory refDataLmdbEnvFactory,
                                         final RefDataOffHeapStore.Factory refDataOffHeapStoreFactory,
                                         final MetaService metaService,
                                         final DocRefInfoService docRefInfoService,
                                         final SecurityContext securityContext,
                                         final PathCreator pathCreator) {
        this.referenceDataConfigProvider = referenceDataConfigProvider;
        this.refDataLmdbEnvFactory = refDataLmdbEnvFactory;
        this.refDataOffHeapStoreFactory = refDataOffHeapStoreFactory;
        this.metaService = metaService;
        this.docRefInfoService = docRefInfoService;
        this.securityContext = securityContext;
        this.pathCreator = pathCreator;

        metaIdToFeedStoreCache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> referenceDataConfigProvider.get().getMetaIdToRefStoreCache(),
                this::getOrCreateFeedSpecificStore);

        initLegacyStore(false);
        // Set up all the stores we find on disk so NodeStatusServiceUtil can get all the size on disk
        // values
        discoverFeedSpecificStores();
    }

    /**
     * Get all ref stores keyed by feed name
     *
     * @return Map of feedName => {@link RefDataOffHeapStore}
     */
    public Map<String, RefDataOffHeapStore> getFeedNameToStoreMap() {
        return Collections.unmodifiableMap(feedNameToStoreMap);
    }

    @Override
    public Set<String> getMapNames(final RefStreamDefinition refStreamDefinition) {
        return getEffectiveStore(refStreamDefinition).getMapNames(refStreamDefinition);
    }

    @Override
    public Optional<ProcessingState> getLoadState(final RefStreamDefinition refStreamDefinition) {
        return getEffectiveStore(refStreamDefinition).getLoadState(refStreamDefinition);
    }

    @Override
    public boolean exists(final MapDefinition mapDefinition) {
        return getEffectiveStore(mapDefinition).exists(mapDefinition);
    }

    @Override
    public Optional<RefDataValue> getValue(final MapDefinition mapDefinition, final String key) {
        return getEffectiveStore(mapDefinition).getValue(mapDefinition, key);
    }

    @Override
    public RefDataValueProxy getValueProxy(final MapDefinition mapDefinition, final String key) {
        return getEffectiveStore(mapDefinition).getValueProxy(mapDefinition, key);
    }

    @Override
    public boolean consumeValueBytes(final MapDefinition mapDefinition,
                                     final String key,
                                     final Consumer<TypedByteBuffer> valueBytesConsumer) {
        return getEffectiveStore(mapDefinition).consumeValueBytes(mapDefinition, key, valueBytesConsumer);
    }

    @Override
    public boolean doWithLoaderUnlessComplete(final RefStreamDefinition refStreamDefinition,
                                              final long effectiveTimeMs,
                                              final Consumer<RefDataLoader> work) {
        return getEffectiveStore(refStreamDefinition)
                .doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, work);
    }

    @Override
    public List<RefStoreEntry> list(final int limit) {
        return getListOnAllStores(limit, store ->
                store.list(limit));
    }

    @Override
    public List<RefStoreEntry> list(final int limit, final Predicate<RefStoreEntry> filter) {
        return getListOnAllStores(limit, store ->
                store.list(limit, filter));
    }

    @Override
    public void consumeEntries(final Predicate<RefStoreEntry> filter,
                               final Predicate<RefStoreEntry> takeWhilePredicate,
                               final Consumer<RefStoreEntry> entryConsumer) {

        Stream<RefStoreEntry> stream = getAllStoresAsStream()
                .parallel()
                .flatMap(store ->
                        store.list(Integer.MAX_VALUE, filter).stream());

        if (takeWhilePredicate != null) {
            stream = stream.takeWhile(takeWhilePredicate);
        }
        stream.forEach(entryConsumer);
    }

    @Override
    public List<ProcessingInfoResponse> listProcessingInfo(final int limit) {
        return getListOnAllStores(
                limit, store ->
                        store.listProcessingInfo(limit)
        );
    }

    private <T> List<T> getListOnAllStores(final int limit,
                                           final Function<RefDataOffHeapStore, List<T>> listFunction) {

        Stream<T> stream = getAllStoresAsStream()
                .parallel()
                .flatMap(store ->
                        listFunction.apply(store).stream());
        if (limit > 0 && limit < Integer.MAX_VALUE) {
            stream = stream.limit(limit);
        }
        return stream.collect(Collectors.toList());
    }

    @Override
    public List<ProcessingInfoResponse> listProcessingInfo(final int limit,
                                                           final Predicate<ProcessingInfoResponse> filter) {
        return getAllStoresAsStream()
                .parallel()
                .flatMap(store ->
                        store.listProcessingInfo(limit, filter).stream())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public long getKeyValueEntryCount() {
        return getStoresAggregateValue(RefDataOffHeapStore::getKeyValueEntryCount);
    }

    @Override
    public long getRangeValueEntryCount() {
        return getStoresAggregateValue(RefDataOffHeapStore::getRangeValueEntryCount);
    }

    @Override
    public long getProcessingInfoEntryCount() {
        return getStoresAggregateValue(RefDataOffHeapStore::getProcessingInfoEntryCount);
    }

    private long getStoresAggregateValue(final ToLongFunction<RefDataOffHeapStore> valueFunc) {
        return getAllStoresAsStream()
                .parallel()
                .mapToLong(valueFunc)
                .sum();
    }

    @Override
    public void purgeOldData() {
        LOGGER.debug("purgeOldData() called");
        // Purge sequentially
        getAllStoresAsStream()
                .forEach(refDataOffHeapStore -> {
                    LOGGER.debug("Calling purgeOldData() on store {}", refDataOffHeapStore);
                    refDataOffHeapStore.purgeOldData();
                });
        checkLegacyStoreState();
    }

    @Override
    public void purgeOldData(final StroomDuration purgeAge) {
        LOGGER.debug("purgeOldData({}) called", purgeAge);
        // Purge sequentially
        getAllStoresAsStream()
                .forEach(refDataOffHeapStore -> {
                    LOGGER.debug("Calling purgeOldData({}) on store {}", purgeAge, refDataOffHeapStore);
                    refDataOffHeapStore.purgeOldData(purgeAge);
                });
        checkLegacyStoreState();
    }

    @Override
    public void purge(final long refStreamId, final long partIndex) {
        // Purge sequentially
        final RefDataOffHeapStore effectiveStore = getEffectiveStore(refStreamId);
        LOGGER.debug("Calling purge() - refStreamId {}, partIndex: {}, store: {}",
                refStreamId, partIndex, effectiveStore);
        effectiveStore.purge(refStreamId, partIndex);
        checkLegacyStoreState();
    }

    @Override
    public void logAllContents() {
        // Not parallel so we don't get a disordered mess
        getAllStoresAsStream()
                .forEach(refDataOffHeapStore -> {
                    LOGGER.debug("Dumping contents of store {}", refDataOffHeapStore);
                    refDataOffHeapStore.logAllContents();
                });
    }

    @Override
    public void logAllContents(final Consumer<String> logEntryConsumer) {
        // Not parallel so we don't get a disordered mess
        getAllStoresAsStream()
                .forEach(store -> store.logAllContents(logEntryConsumer));
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.OFF_HEAP;
    }

    @Override
    public long getSizeOnDisk() {
        return getAllStoresAsStream()
                .parallel()
                .mapToLong(RefDataOffHeapStore::getSizeOnDisk)
                .sum();
    }

    @Override
    public SystemInfoResult getSystemInfo(final Map<String, String> params) {
        // Let the caller specify the feed to inspect. Too much info for each feed store
        // to show it all
        return Optional.ofNullable(params.get(PARAM_NAME_FEED))
                .map(feedName ->
                        Optional.ofNullable(feedNameToStoreMap.get(feedName))
                                .map(RefDataOffHeapStore::getSystemInfo)
                                .orElseGet(() -> SystemInfoResult.builder(this)
                                        .build()))
                .orElseGet(this::getSystemInfo);
    }

    @Override
    public List<ParamInfo> getParamInfo() {
        return List.of(
                ParamInfo.optionalParam(PARAM_NAME_FEED,
                        "The name of the feed to get the store system info for."));
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        try {
            final ReferenceDataConfig referenceDataConfig = referenceDataConfigProvider.get();

            final SystemInfoResult.Builder builder = SystemInfoResult.builder(this)
                    .addDetail("Feed store paths", feedNameToStoreMap.values()
                            .stream()
                            .map(store -> store.getLmdbEnvironment().getLocalDir().toAbsolutePath().toString())
                            .sorted()
                            .collect(Collectors.toList()))
                    .addDetail("Environment max size", referenceDataConfig.getLmdbConfig().getMaxStoreSize())
                    .addDetail("Environment current size (total)",
                            ModelStringUtil.formatIECByteSizeString(getSizeOnDisk()))
                    .addDetail("Purge age", referenceDataConfig.getPurgeAge())
                    .addDetail("Purge cut off",
                            TimeUtils.durationToThreshold(referenceDataConfig.getPurgeAge()).toString())
                    .addDetail("Max readers", referenceDataConfig.getLmdbConfig().getMaxReaders())
                    .addDetail("Read-ahead enabled", referenceDataConfig.getLmdbConfig().isReadAheadEnabled());

            if (legacyRefDataStore != null) {
                builder.addDetail("Legacy store", legacyRefDataStore.getSystemInfo());
            }

            return builder.build();
        } catch (RuntimeException e) {
            return SystemInfoResult.builder(this)
                    .addError(e)
                    .build();
        }
    }

    private Stream<RefDataOffHeapStore> getAllStoresAsStream() {
        return legacyRefDataStore != null
                ? Stream.concat(Stream.of(legacyRefDataStore), feedNameToStoreMap.values().stream())
                : feedNameToStoreMap.values().stream();
    }

    private String lookUpFeedName(final long refSteamId) {
        // Ref store is not specific to one user, so we need to see all feeds.
        // Ref lookups will take care of perm checks
        return securityContext.asProcessingUserResult(() ->
                NullSafe.getAsOptional(metaService.getMeta(refSteamId),
                                Meta::getFeedName)
                        .orElseThrow(() -> new RuntimeException("No meta record found for meta ID " + refSteamId)));
    }

    public RefDataOffHeapStore getEffectiveStore(final MapDefinition mapDefinition) {
        Objects.requireNonNull(mapDefinition);
        return getEffectiveStore(mapDefinition.getRefStreamDefinition().getStreamId());
    }

    public RefDataOffHeapStore getEffectiveStore(final RefStreamDefinition refStreamDefinition) {
        Objects.requireNonNull(refStreamDefinition);
        return getEffectiveStore(refStreamDefinition.getStreamId());
    }

    public RefDataOffHeapStore getEffectiveStore(final long refStreamId) {
        final FeedSpecificStore feedSpecificStore = metaIdToFeedStoreCache.get(refStreamId);
        // Loading cache so should not be null
        Objects.requireNonNull(feedSpecificStore);
        LOGGER.debug("getEffectiveStore() - refStreamId: {}, feedSpecificStore: {}", refStreamId, feedSpecificStore);
        return feedSpecificStore.refDataOffHeapStore;
    }

    private FeedSpecificStore getOrCreateFeedSpecificStore(final long refStreamId) {
        final String feedName = lookUpFeedName(refStreamId);
        Objects.requireNonNull(feedName);

        final RefDataOffHeapStore feedSpecificStore = feedNameToStoreMap.computeIfAbsent(
                feedName,
                this::getOrCreateFeedSpecificStore);

        // We migrate at the refStreamId level, so we just hold a set of IDs rather than a load of
        // RefStreamDefinition objects which will be more costly to check against. In most cases there
        // will only be one RefStreamDefinition per refStreamId
        if (legacyRefDataStore != null
                && migrationCheckRequired
                && !migratedRefStreamIds.contains(refStreamId)) {

            if (feedSpecificStore.exists(refStreamId)) {
                // Already migrated, therefore add it to our set, so we don't need to do the
                // costly exists check again.
                LOGGER.trace("refStreamId: {} already migrated", refStreamId);
                migratedRefStreamIds.add(refStreamId);
            } else {
                migrateRefStream(refStreamId, feedSpecificStore);
            }
        } else {
            LOGGER.trace("No migration required");
        }

        return new FeedSpecificStore(feedName, feedSpecificStore);
    }

    long getEntryCount(final String dbName) {
        return getAllStoresAsStream()
                .parallel()
                .mapToLong(store -> store.getEntryCount(dbName))
                .sum();
    }

    private void migrateRefStream(final long refStreamId,
                                  final RefDataOffHeapStore feedSpecificStore) {
        LOGGER.info("Migrating ref stream {} from legacy ref store to feed specific store '{}'",
                refStreamId, feedSpecificStore);

        legacyRefDataStore.migrateRefStreams(refStreamId, feedSpecificStore);

        // Record the ID, so we don't have to migrate it again.
        migratedRefStreamIds.add(refStreamId);
    }

    private void discoverFeedSpecificStores() {
        final String localDirStr = referenceDataConfigProvider.get().getLmdbConfig().getLocalDir();
        final Path localDir = pathCreator.toAppPath(localDirStr);
        if (Files.isDirectory(localDir)) {
            try (final Stream<Path> pathStream = Files.list(localDir)) {
                pathStream.filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().contains(DELIMITER))
                        .forEach(dir -> {
                            final String dirName = dir.getFileName().toString();
                            final String[] parts = DELIMITER_PATTERN.split(dirName);
                            if (parts.length != 2) {
                                LOGGER.error("Unable to parse store directory {}, parts: {}. Ignoring this store",
                                        dir, parts);
                            } else {
                                final String feedDocUuid = parts[1];
                                final String feedName = NullSafe.get(
                                        docRefInfoService.decorate(FeedDoc.buildDocRef()
                                                .uuid(feedDocUuid)
                                                .build()),
                                        DocRef::getName);

                                if (!NullSafe.isBlankString(feedName)) {
                                    feedNameToStoreMap.computeIfAbsent(feedName, this::getOrCreateFeedSpecificStore);
                                } else {
                                    LOGGER.error("No feed name for UUID {}. Ignoring store {}", feedDocUuid, dir);
                                }
                            }
                        });

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            LOGGER.debug("localDir '{}' doesn't exist, no stores to create", localDir);
        }
    }

    private void initLegacyStore(final boolean createIfNotExists) {
        final String localDirStr = referenceDataConfigProvider.get().getLmdbConfig().getLocalDir();
        final Path localDir = pathCreator.toAppPath(localDirStr);

        // localDir is the parent for all the feed specific stores so may as well ensure its presence
        try {
            Files.createDirectories(localDir);
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error ensuring directory {}: {}", localDir, e.getMessage()), e);
        }

        // Legacy store may not exist, e.g. for a recent deployment, or it may be empty
        try (final Stream<Path> pathStream = Files.list(localDir).filter(Files::isRegularFile)) {
            final boolean foundDataFile = pathStream.anyMatch(LmdbEnv::isLmdbDataFile);
            if (foundDataFile) {
                final RefDataOffHeapStore legacyStore = getOrCreateLegacyStore();
                if (legacyStore.isEmpty()) {
                    LOGGER.debug("Found empty legacy store in {}", localDir);
                    legacyRefDataStore = null;
                    migrationCheckRequired = false;
                } else {
                    LOGGER.debug("Found non-empty legacy store in {}", localDir);
                    legacyRefDataStore = legacyStore;
                    migrationCheckRequired = true;
                }
            } else {
                if (createIfNotExists) {
                    LOGGER.debug("No legacy store found in {}, creating an empty one", localDir);
                    legacyRefDataStore = getOrCreateLegacyStore();
                    // createIfNotExists is only really here for migration testing so require a mig check
                    migrationCheckRequired = true;
                } else {
                    LOGGER.debug("No legacy store found in {}", localDir);
                    legacyRefDataStore = null;
                    migrationCheckRequired = false;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error listing files in {}: {}", localDir, e.getMessage()), e);
        }
    }

    private RefDataOffHeapStore getOrCreateLegacyStore() {
        return getOrCreateFeedSpecificStore(null);
    }

    private RefDataOffHeapStore getOrCreateFeedSpecificStore(final String feedName) {
        final String subDirName = NullSafe.get(feedName, this::feedNameToSubDirName);
        // This will get/create the env on disk
        final RefDataLmdbEnv refDataLmdbEnv = refDataLmdbEnvFactory.create(subDirName);
        final RefDataOffHeapStore refDataOffHeapStore = refDataOffHeapStoreFactory.create(refDataLmdbEnv);
        LOGGER.info("Created reference data store for feed: '{}' in sub dir: '{}'", feedName, subDirName);
        return refDataOffHeapStore;
    }

    private String feedNameToSubDirName(final String feedName) {
        final String cleanedFeedName = feedName.toUpperCase()
                .replaceAll("[^A-Z0-9_-]", "_");

        final List<DocRef> feedDocRefs = docRefInfoService.findByName(
                FeedDoc.DOCUMENT_TYPE, feedName, false);
        if (feedDocRefs.isEmpty()) {
            throw new RuntimeException(LogUtil.message("Expecting to find feed doc for name " + feedName));
        } else if (feedDocRefs.size() > 1) {
            throw new RuntimeException(LogUtil.message("Found " + feedDocRefs.size() + " feed docs for name "
                    + feedName + ". Feed names should be unique."));
        }
        final String feedDocUuid = feedDocRefs.get(0).getUuid();

        // It is possible for two feed names to share the same cleaned name, so add a UUID on the end to make it
        // unique
        final String subDirName = String.join(DELIMITER, cleanedFeedName, feedDocUuid);
        LOGGER.debug("feedName: '{}', subDirName: '{}'", feedName, subDirName);
        return subDirName;
    }

    private void checkLegacyStoreState() {
        LOGGER.debug("checkLegacyStoreState()");
        final RefDataOffHeapStore legacyStore = this.legacyRefDataStore;
        if (legacyStore != null && legacyStore.isEmpty()) {
            // Purge may have cleaned it out, so don't need to check for migrations in future
            if (LOGGER.isDebugEnabled()) {
                if (migrationCheckRequired) {
                    LOGGER.debug("Setting migrationCheckRequired to false");
                }
            }
            migrationCheckRequired = false;
        }
    }

    /**
     * For testing only!
     */
    RefDataOffHeapStore getLegacyRefDataStore(final boolean createIfNotExists) {
        initLegacyStore(true);
        return legacyRefDataStore;
    }


    // --------------------------------------------------------------------------------


    private record FeedSpecificStore(String feedName, RefDataOffHeapStore refDataOffHeapStore) {

        @Override
        public String toString() {
            return "FeedSpecificStore{" +
                    "feedName='" + feedName + '\'' +
                    ", refDataOffHeapStore=" + refDataOffHeapStore +
                    '}';
        }
    }
}
