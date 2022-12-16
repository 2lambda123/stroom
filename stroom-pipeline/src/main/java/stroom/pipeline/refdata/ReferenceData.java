/*
 * Copyright 2016 Crown Copyright
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
 */

package stroom.pipeline.refdata;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SizeAwareInputStream;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.cache.DocumentPermissionCache;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class ReferenceData {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceData.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ReferenceData.class);

    // Actually 11.5 days but this is fine for the purposes of reference data.
    static final long APPROX_TEN_DAYS = 1000000000;

    // Maps can be nested during the look up process e.g. "MAP1/MAP2"
    private static final int MINIMUM_BYTE_COUNT = 10;

    private EffectiveStreamCache effectiveStreamCache;
    private final FeedHolder feedHolder;
    private final MetaHolder metaHolder;
    private final ContextDataLoader contextDataLoader;
    private final DocumentPermissionCache documentPermissionCache;
    private final Map<PipelineReference, Boolean> localDocumentPermissionCache = new HashMap<>();
    private final ReferenceDataLoader referenceDataLoader;
    private final RefDataStoreHolder refDataStoreHolder;
    private final RefDataLoaderHolder refDataLoaderHolder;
    private final PipelineStore pipelineStore;
    private final SecurityContext securityContext;


    @Inject
    ReferenceData(final EffectiveStreamCache effectiveStreamCache,
                  final FeedHolder feedHolder,
                  final MetaHolder metaHolder,
                  final ContextDataLoader contextDataLoader,
                  final DocumentPermissionCache documentPermissionCache,
                  final ReferenceDataLoader referenceDataLoader,
                  final RefDataStoreHolder refDataStoreHolder,
                  final RefDataLoaderHolder refDataLoaderHolder,
                  final PipelineStore pipelineStore,
                  final SecurityContext securityContext) {
        this.effectiveStreamCache = effectiveStreamCache;
        this.feedHolder = feedHolder;
        this.metaHolder = metaHolder;
        this.contextDataLoader = contextDataLoader;
        this.documentPermissionCache = documentPermissionCache;
        this.referenceDataLoader = referenceDataLoader;
        this.refDataStoreHolder = refDataStoreHolder;
        this.refDataLoaderHolder = refDataLoaderHolder;
        this.pipelineStore = pipelineStore;
        this.securityContext = securityContext;
    }

    /**
     * <p>
     * Given a {@link LookupIdentifier} and a list of ref data pipelines, ensure that
     * the data required to perform the lookup is in the ref store. This method will not
     * perform the lookup, instead it will populate the {@link ReferenceDataResult} with
     * a proxy object that can later be used to do the lookup.
     * </p>
     *
     * @param pipelineReferences The references to look for reference data in.
     * @param lookupIdentifier   The identifier to lookup in the reference data
     * @param result             The reference result object containing the proxy object for performing the lookup
     */
    public void ensureReferenceDataAvailability(final List<PipelineReference> pipelineReferences,
                                                final LookupIdentifier lookupIdentifier,
                                                final ReferenceDataResult result) {

        LOGGER.debug("ensureReferenceDataAvailability({}, {})", lookupIdentifier, pipelineReferences);

        // TODO @AT It would be better if the nested map logic was pushed down into the store so that the store
        //   can accept nested map names. This would allow the store to do the lookup chain in a more efficient
        //   way with the bytebuffer of the lookup value being used as the key for the next lookup.
        //   You would still need to have code here to ensure that the data for all maps in the chain was loaded.


        // Do we have a nested map e.g a map of 'USER_ID_TO_MANAGER/USER_ID_TO_LOCATION' to get the location of a
        // user's manager using a chained lookup
        if (lookupIdentifier.isMapNested()) {
            LOGGER.trace("lookupIdentifier is nested {}", lookupIdentifier);

            // Look up the KV then use that to recurse
            doGetValue(pipelineReferences, lookupIdentifier, result);

            final Optional<RefDataValue> optValue = result.getRefDataValueProxy()
                    .flatMap(RefDataValueProxy::supplyValue);
            // This is a nested map so we are expecting the value of the first map to be a simple
            // string so we can use it as the key for the next map. The next map could also be nested.

            if (optValue.isEmpty()) {
                LOGGER.trace("sub-map not found for {}", lookupIdentifier);
                // map broken ... no link found
                result.logSimpleTemplate(Severity.WARNING, "No map found for '{}'", lookupIdentifier);
            } else {
                final RefDataValue refDataValue = optValue.get();
                try {
                    final String nextKey = ((StringValue) refDataValue).getValue();
                    LOGGER.trace("Found value to use as next key {}", nextKey);

                    logMapLocations(result);

                    // use the value from this lookup as the key for the nested map
                    final LookupIdentifier nestedIdentifier = lookupIdentifier.getNestedLookupIdentifier(nextKey);

                    // As we are recursing, make sure the result is in a clean state for the next level of
                    // the recursion
                    result.setCurrentLookupIdentifier(nestedIdentifier);

                    result.logLazyTemplate(
                            Severity.INFO,
                            "Doing nested lookup using previous lookup value as new key: '{}' - " +
                                    "(primary map: {}, secondary map: {})",
                            () -> Arrays.asList(
                                    nextKey,
                                    nestedIdentifier.getPrimaryMapName(),
                                    nestedIdentifier.getSecondaryMapName()));

                    // Recurse with the nested lookup
                    ensureReferenceDataAvailability(pipelineReferences, nestedIdentifier, result);
                } catch (ClassCastException e) {
                    result.logLazyTemplate(Severity.ERROR,
                            "Value is the wrong type, expected: {}, found: {}",
                            () -> Arrays.asList(StringValue.class.getName(),
                                    refDataValue.getClass().getName()));
                }
            }
        } else {
            LOGGER.trace("lookupIdentifier is not nested {}", lookupIdentifier);
            // non-nested map so just do a lookup
            doGetValue(pipelineReferences, lookupIdentifier, result);
        }
    }

    private void logMapLocations(final ReferenceDataResult result) {
        // We need to compute these values now rather than in the lamba, else
        // they will change when the ReferenceDataResult is changed during recursion.
        // May want to consider storing the chain of results so we can get at any of the
        // result levels.
        final String mapName = result.getRefDataValueProxy()
                .map(RefDataValueProxy::getMapName)
                .orElse(null);
        final List<RefStreamDefinition> qualifyingStreams = new ArrayList<>(result.getQualifyingStreams());
        final int effectiveStreamCount = result.getEffectiveStreams().size();

        result.logLazyTemplate(Severity.INFO,
                "Map '{}' found in {} out of {} effective streams: [{}]",
                () -> {
                    final String streamsStr = qualifyingStreams
                            .stream()
                            .map(RefStreamDefinition::getStreamId)
                            .map(String::valueOf)
                            .collect(Collectors.joining(", "));
                    return Arrays.asList(
                            mapName,
                            qualifyingStreams.size(),
                            effectiveStreamCount,
                            streamsStr);
                });
    }

    private void doGetValue(final List<PipelineReference> pipelineReferences,
                            final LookupIdentifier lookupIdentifier,
                            final ReferenceDataResult referenceDataResult) {

        // An event pipeline can have multiple ref loader pipelines (PipelineReference) associated with it.
        // Each PipelineReference defines a feed to look for an effective stream in, based on the effective
        // date of the stream. Each ref stream can contain zero-many maps and a map may exist in a stream with
        // one effective date, but not in another.
        // Feeds for different PipelineReferences may contain the same maps and keys so the PipelineReferences
        // are used in strict order, with the result being taken from the first hit.
        // Once a stream has been loaded we can find out the distinct set of maps it contains and then
        // use this information to ignore it based on the lookupIdentifier. This state is held in
        // RefDataLoaderHolder.
        for (final PipelineReference pipelineReference : pipelineReferences) {

            LOGGER.trace("doGetValue - processing pipelineReference {} for {}",
                    pipelineReference, lookupIdentifier);

            // Handle context data differently loading it from the current stream context.
            if (NullSafe.test(pipelineReference.getStreamType(), StreamTypeNames.CONTEXT::equals)) {

                getValueFromNestedContextStream(
                        pipelineReference,
                        lookupIdentifier.getPrimaryMapName(),
                        lookupIdentifier.getKey(),
                        referenceDataResult);
            } else {
                getValueFromExternalRefStream(
                        pipelineReference,
                        lookupIdentifier.getEventTime(),
                        lookupIdentifier.getPrimaryMapName(),
                        lookupIdentifier.getKey(),
                        referenceDataResult);
            }

            // If a lookup is viable a RefDataValueProxy will have been added to the result

            LAMBDA_LOGGER.trace(() -> LogUtil.message("refDataValueProxy: {}",
                    referenceDataResult.getRefDataValueProxy().orElse(null)));

            // We are dealing with multiple ref pipelines so collect the value proxy for
            // each one
//            if (referenceDataResult.getQualifyingStreams().size() > 1) {
//                // If a proxy has been set on the result then it means this pipeline reference
//                // contains the requested map, i.e. there is no point in doing a lookup on the
//                // map def if the map is known to not exist for that ref stream
//                referenceDataResult.getRefDataValueProxy().ifPresent(refDataValueProxy -> {
//                    if (refDataValueProxy instanceof SingleRefDataValueProxy) {
//                        // Add it to a list and remove it from the result so we
//                        // can add it back once we have checked all pipe refs
//                        refDataValueProxies.add((SingleRefDataValueProxy) refDataValueProxy);
//                        referenceDataResult.clearRefDataValueProxy();
//                    } else {
//                        throw new RuntimeException("Unexpected type " +
//                                refDataValueProxy.getClass().getName());
//                    }
//                });
//            }
        }

        // We are dealing with multiple ref pipelines so replace the current value proxy with a
        // multi one that will perform a lookup on each one in turn until it finds a hit
//        if (!refDataValueProxies.isEmpty()) {
//            if (refDataValueProxies.size() > 1) {
//                LAMBDA_LOGGER.trace(() -> LogUtil.message(
//                        "Replacing value proxy with multi proxy ({})", refDataValueProxies.size()));
//                referenceDataResult.setRefDataValueProxy(new MultiRefDataValueProxy(refDataValueProxies));
//            } else {
//                referenceDataResult.setRefDataValueProxy(refDataValueProxies.get(0));
//            }
//        }
    }


    /**
     * Get an event list from a stream that is a nested child of the current
     * stream context and is therefore not effective time sensitive.
     * i.e. a context stream attached to this stream and contains data applicable
     * to this stream only.
     */
    private RefStreamDefinition getValueFromNestedContextStream(final PipelineReference pipelineReference,
                                                                final String mapName,
                                                                final String keyName,
                                                                final ReferenceDataResult result) {

        LAMBDA_LOGGER.trace(() -> LogUtil.message(
                "getNestedStreamEventList called, pipe: {}, feed: {}, streamType: {}, map: {}, key: {}",
                pipelineReference.getName(),
                pipelineReference.getFeed().getName(),
                pipelineReference.getStreamType(),
                mapName,
                keyName));

        // Get nested stream part.
        final long partIndex = metaHolder.getPartIndex();

        LAMBDA_LOGGER.trace(() -> LogUtil.message("StreamId: {}, parentStreamId: {}",
                metaHolder.getMeta().getId(),
                metaHolder.getMeta().getParentMetaId()));

        // the parent stream appears to be null at this point so just use the stream id
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                pipelineReference.getPipeline(),
                getPipelineVersion(pipelineReference),
                metaHolder.getMeta().getId(),
                partIndex);

        // This is a context stream so the context stream is the effective stream
        result.addEffectiveStream(refStreamDefinition);

        // Establish if we have the data for the context stream in the store
        // Context data is only relevant to the event stream it is associated with so is therefore
        // transient and is stored on heap rather than in LMDB.
        final RefDataStore onHeapRefDataStore = refDataStoreHolder.getOnHeapRefDataStore();

        final Optional<ProcessingState> optLoadState = onHeapRefDataStore.getLoadState(refStreamDefinition);
        LOGGER.trace("optLoadState: {}", optLoadState);
        final boolean isRefLoadRequired = optLoadState
                .filter(loadState ->
                        loadState.equals(ProcessingState.COMPLETE))
                .isEmpty();
        LOGGER.trace("optLoadState: {}, isRefLoadRequired: {}", optLoadState, isRefLoadRequired);

        if (optLoadState.isPresent() && optLoadState.get().equals(ProcessingState.FAILED)) {
            throw new RuntimeException(LogUtil.message(
                    "Reference stream {} has been loaded previously but failed, unable to lookup key {} in map {}",
                    refStreamDefinition.getStreamId(), keyName, mapName));
        } else if (!refDataStoreHolder.isLookupNeeded(pipelineReference, refStreamDefinition, mapName)) {
            result.logLazyTemplate(Severity.INFO,
                    "Map '{}' is known not to exist in pipeline: '{}' feed: '{}', stream: {} ",
                    () -> Arrays.asList(
                            mapName,
                            pipelineReference.getPipeline().getName(),
                            pipelineReference.getFeed().getName(),
                            refStreamDefinition.getStreamId()));

            LOGGER.trace("Map '{}' known not to exist in refStreamDefinition {}", mapName, refStreamDefinition);
            // A previous lookup has established the map is not in there so no point going further
        } else {
            if (isRefLoadRequired) {
                // data is not in the store so load it
                final InputStreamProvider provider = metaHolder.getInputStreamProvider();
                // There may not be a provider for this stream type if we do not
                // have any context data stream.
                if (provider != null) {
                    final SizeAwareInputStream inputStream = provider.get(pipelineReference.getStreamType());
                    loadContextData(
                            metaHolder.getMeta(),
                            inputStream,
                            pipelineReference.getPipeline(),
                            refStreamDefinition,
                            onHeapRefDataStore);

                }
            }

            // Now the data is known to be loaded, record the maps available in the stream
            // for subsequent lookups
            refDataStoreHolder.addKnownMapNames(
                    onHeapRefDataStore,
                    refStreamDefinition);

            // Add a proxy to the lookup value now we know it is loaded
            setValueProxyOnResult(onHeapRefDataStore, mapName, keyName, result, refStreamDefinition);
        }

        return refStreamDefinition;
    }


    private void setValueProxyOnResult(final RefDataStore refDataStore,
                                       final String mapName,
                                       final String keyName,
                                       final ReferenceDataResult result,
                                       final RefStreamDefinition refStreamDefinition) {

        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);

        // This stream def may/may not have this map name. To save us hitting the DB each time to
        // find out we hold the result in pipeline scope. This assumes we have already determined
        // that the refStreamDef is fully loaded
        Boolean doesMapDefExist = refDataLoaderHolder.isMapDefinitionAvailable(mapDefinition);

        if (doesMapDefExist == null) {
            // existence unknown so do a lookup in the DB
            doesMapDefExist = refDataStore.exists(mapDefinition);
            refDataLoaderHolder.markMapDefinitionAvailability(mapDefinition, doesMapDefExist);
        }
        if (doesMapDefExist) {
            // Define a proxy object to allow callers to get the required value from the store
            // now that we know that the stream that may contain it is in there.
            final RefDataValueProxy refDataValueProxy = refDataStore.getValueProxy(mapDefinition, keyName);
            LOGGER.trace("Adding refDataValueProxy {} for map {}, refStreamDefinition {}",
                    refDataValueProxy, mapName, refStreamDefinition);
            result.addRefDataValueProxy(refDataValueProxy);
        } else {
            // this stream doesn't have this map so return a null proxy to save a pointless lookup
            LOGGER.trace("No mapDefinition for map {}, refStreamDefinition {} so no proxy to add",
                    mapName, refStreamDefinition);
        }
    }

    private String getPipelineVersion(final PipelineReference pipelineReference) {
        return refDataLoaderHolder.getPipelineVersion(pipelineReference, pipelineStore);
    }

    private void loadContextData(
            final Meta meta,
            final SizeAwareInputStream contextStream,
            final DocRef contextPipeline,
            final RefStreamDefinition refStreamDefinition,
            final RefDataStore refDataStore) {

        if (contextStream != null) {
            // Check the size of the input stream.
            final long byteCount = contextStream.size();
            // Only use context data if we actually have some.
            if (byteCount > MINIMUM_BYTE_COUNT) {
                // load the context data into the RefDataStore so it is available for lookups
                contextDataLoader.load(
                        contextStream,
                        meta,
                        feedHolder.getFeedName(),
                        contextPipeline,
                        refStreamDefinition,
                        refDataStore);
            }
        }
    }

    /**
     * Ensures that the effective external ref stream is loaded, doing the load if it
     * is not. It then mutates result to set a value proxy on it so that downstream
     * code can use the proxy to get the value from the store.
     * The effective stream is determined based on the time used in the lookup() call
     * and the effective time of the various reference streams, i.e. picking the last ref
     * stream before the lookup time.
     */
    private void getValueFromExternalRefStream(
            final PipelineReference pipelineReference,
            final long time,
            final String mapName,
            final String keyName,
            final ReferenceDataResult result) {

        // Make sure the reference feed is persistent otherwise lookups will
        // fail as the equals method will only test for feeds that are the
        // same object instance rather than id.
        if (pipelineReference.getFeed() == null ||
                pipelineReference.getFeed().getUuid() == null ||
                pipelineReference.getFeed().getUuid().isEmpty() ||
                pipelineReference.getStreamType() == null ||
                pipelineReference.getStreamType().isEmpty()) {

            result.logSimpleTemplate(Severity.ERROR,
                    "pipelineReference is not fully formed, {}",
                    pipelineReference);
        }

        // Check that the current user has permission to read the ref stream.
        final boolean hasPermission = localDocumentPermissionCache.computeIfAbsent(pipelineReference, k ->
                documentPermissionCache == null ||
                        documentPermissionCache.canUseDocument(
                                pipelineReference.getFeed().getUuid()
                        ));

        if (hasPermission) {
            final EffectiveStream effectiveStream = determineEffectiveStream(pipelineReference, time, result);

            // If we have an effective stream then use it.
            if (effectiveStream != null) {

                result.logLazyTemplate(Severity.INFO,
                        "Using stream: {} (effective time: {}) for lookup time: {}, feed: '{}'",
                        () -> Arrays.asList(effectiveStream.getStreamId(),
                                Instant.ofEpochMilli(effectiveStream.getEffectiveMs()).toString(),
                                Instant.ofEpochMilli(time).toString(),
                                pipelineReference.getFeed().getName()));

                final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                        pipelineReference.getPipeline(),
                        getPipelineVersion(pipelineReference),
                        effectiveStream.getStreamId());

                result.addEffectiveStream(refStreamDefinition);

                if (refDataStoreHolder.isLookupNeeded(pipelineReference, refStreamDefinition, mapName)) {
                    // Stream is known to hold the map we are trying to hit, or we don't yet know the
                    // contents so have to do the lookup in case.
                    final RefDataStore offHeapRefDataStore = refDataStoreHolder.getOffHeapRefDataStore();

                    // load the ref stream into the store if not already there
                    final boolean isAvailableForLookups = ensureRefStreamAvailability(
                            result,
                            pipelineReference,
                            refStreamDefinition,
                            offHeapRefDataStore);

                    if (isAvailableForLookups) {
                        // now we should have the required data in the store (unless the max age is set far too small)
                        // Note however that the effective stream may not contain the map we are interested in. A data
                        // may have two ref loaders on it.  When a lookup is done it must try the lookup against the two
                        // effective streams as it cannot know which ref streams contain (if at all) the map name of
                        // interest.
                        setValueProxyOnResult(offHeapRefDataStore, mapName, keyName, result, refStreamDefinition);
                    }
                } else {
                    LOGGER.trace("Map '{}' known not to exist in refStreamDefinition {}", mapName, refStreamDefinition);
                    // A previous lookup has established the map is not in there so no point going further
                }
            } else {
                result.logLazyTemplate(
                        Severity.WARNING,
                        "No effective stream can be found for feed '{}' and lookup time '{}'. " +
                                "Check a reference data stream exists with an effective time that is before the " +
                                "lookup time.",
                        () -> Arrays.asList(pipelineReference.getFeed().getName(),
                                Instant.ofEpochMilli(time).toString()));
            }
        } else {
            result.logLazyTemplate(
                    Severity.ERROR,
                    "User does not have permission to use data from feed '{}'",
                    () -> List.of(
                            pipelineReference.getFeed().getName()));
        }
    }

    private boolean ensureRefStreamAvailability(final ReferenceDataResult result,
                                                final PipelineReference pipelineReference,
                                                final RefStreamDefinition refStreamDefinition,
                                                final RefDataStore offHeapRefDataStore) {
        final boolean isAvailableForLookups;

        // First check the pipeline scoped object to save us hitting the store for every lookup in a
        // pipeline process run.
        if (refDataLoaderHolder.isRefStreamAvailable(refStreamDefinition)) {
            // we have already loaded or confirmed the load state for this refStream in this
            // pipeline process so no need to try again
            LOGGER.trace("refStreamDefinition {} is available for use", refStreamDefinition);
            isAvailableForLookups = true;
        } else {
            // we don't know what the load state is for this refStreamDefinition so need to find out
            // by querying the store. This will also update the last accessed time so will prevent
            // (unless the purge age is very small) a purge from removing the data we are about to use
            final Optional<ProcessingState> optLoadState =
                    offHeapRefDataStore.getLoadState(refStreamDefinition);

            final boolean isRefLoadRequired = optLoadState
                    .filter(loadState ->
                            loadState.equals(ProcessingState.COMPLETE))
                    .isEmpty();
            LOGGER.trace("optLoadState {}, isRefLoadRequired {}", optLoadState, isRefLoadRequired);

            if (optLoadState.isPresent() && optLoadState.get().equals(ProcessingState.FAILED)) {
                throw new RuntimeException(LogUtil.message(
                        "Reference stream {} has been loaded previously but failed, " +
                                "aborting lookup against this stream.",
                        refStreamDefinition.getStreamId()));
            } else {
                if (isRefLoadRequired) {
                    // we don't have the complete data so kick off a process to load it all
                    LOGGER.debug("Creating task to load reference data {}", refStreamDefinition);

                    // Initiate a load of the ref data for this stream
                    // It is possible for another thread, i.e. another pipeline process to be trying to
                    // load the same stream at the same time. There is concurrency protection further
                    // down to protect against this.
                    final StoredErrorReceiver storedErrorReceiver =
                            securityContext.asProcessingUserResult(() ->
                                    referenceDataLoader.load(refStreamDefinition));

                    // Replay any errors/warning from the load onto our result
                    if (storedErrorReceiver != null) {
                        storedErrorReceiver.replay(result);
                    }

                    LAMBDA_LOGGER.debug(() -> LogUtil.message(
                            "Loaded {} refStreamDefinition", refStreamDefinition));

                    if (storedErrorReceiver == null
                            || storedErrorReceiver.getCount(Severity.FATAL_ERROR) == 0) {
                        // mark this ref stream defs as available for future lookups within this
                        // pipeline process
                        refDataLoaderHolder.markRefStreamAsAvailable(refStreamDefinition);


                        isAvailableForLookups = true;
                    } else {
                        isAvailableForLookups = false;
                    }
                } else {
                    isAvailableForLookups = true;
                }

                if (isAvailableForLookups) {
                    // Now the data is loaded record the maps in the stream
                    refDataStoreHolder.addKnownMapNames(
                            offHeapRefDataStore,
                            refStreamDefinition);
                }
            }
        }
        return isAvailableForLookups;
    }

    private EffectiveStream determineEffectiveStream(final PipelineReference pipelineReference,
                                                     final long time,
                                                     final ReferenceDataResult result) {

        // Create a window of approx 10 days to cache effective streams.
        // First round down the time to the nearest 10 days approx (actually more like 11.5, one billion milliseconds).
        final long fromMs = (time / APPROX_TEN_DAYS) * APPROX_TEN_DAYS;
        final long toMs = fromMs + APPROX_TEN_DAYS;

        // Create a key to find a set of effective times in the pool.
        final EffectiveStreamKey effectiveStreamKey = new EffectiveStreamKey(
                pipelineReference.getFeed().getName(),
                pipelineReference.getStreamType(),
                fromMs,
                toMs);

        // Try and fetch a tree set of effective streams for this key.
        final NavigableSet<EffectiveStream> effectiveStreams = effectiveStreamCache.get(effectiveStreamKey);

        final EffectiveStream effectiveStream;

        if (effectiveStreams != null && effectiveStreams.size() > 0) {
            result.logLazyTemplate(Severity.INFO,
                    "Got {} potential effective stream(s) (from {} to {}) " +
                            "for effective stream key: {}",
                    () -> Arrays.asList(
                            effectiveStreams.size(),
                            Instant.ofEpochMilli(effectiveStreams.last().getEffectiveMs()),
                            Instant.ofEpochMilli(effectiveStreams.first().getEffectiveMs()),
                            effectiveStreamKey));

            if (LOGGER.isTraceEnabled()) {
                final String streams = effectiveStreams.stream()
                        .map(stream -> stream.getStreamId() + " - "
                                + Instant.ofEpochMilli(stream.getEffectiveMs()))
                        .collect(Collectors.joining("\n"));
                LOGGER.trace("Comparing {} to Effective streams:\n{}", Instant.ofEpochMilli(time), streams);
            }

            // Try and find the stream before the requested time that is less
            // than or equal to it.
            effectiveStream = effectiveStreams.floor(new EffectiveStream(0, time));
        } else {
            // No need to log here as it will get logged by the caller
            effectiveStream = null;
        }
        return effectiveStream;
    }


    /**
     * For testing
     */
    void setEffectiveStreamCache(final EffectiveStreamCache effectiveStreamcache) {
        this.effectiveStreamCache = effectiveStreamcache;
    }
}
