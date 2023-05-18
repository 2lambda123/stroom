package stroom.pipeline.refdata.store;

import stroom.cache.impl.CacheModule;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeModule;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class RefDataStoreTestModule extends AbstractModule {

    public static final long REF_STREAM_1_ID = 123L;
    public static final long REF_STREAM_2_ID = 456L;
    public static final long REF_STREAM_4_ID = 789L;
    public static final String FEED_1_NAME = "FEED1";
    public static final String FEED_1_UUID = "45a5ea9b-fc80-4685-beff-2fb1e84fb2bd";
    public static final String FEED_2_NAME = "FEED2";
    public static final String FEED_2_UUID = "9388a896-eb74-4474-ad4a-17dc7b8315a6";
    public static final String PIPE_1_UUID = "cd48049c-a7b1-4b64-bd7d-6cdc94159721";
    public static final String PIPE_2_UUID = "143abb82-1d8c-4261-b6dd-e8701fef08ef";
    public static final String PIPE_1_VER_1 = "a741e190-b2be-4b5b-910c-1480a1306b49";
    public static final String PIPE_1_VER_2 = "8c98dcd2-7b2d-4111-b278-961e9cabb885";
    public static final String PIPE_2_VER_1 = "7de88440-6fe3-4bd0-b1d6-c6082eb018db";
    // FEED_1, stream 1, pipe 1
    public static RefStreamDefinition REF_STREAM_1_DEF = new RefStreamDefinition(
            PIPE_1_UUID, PIPE_1_VER_1, REF_STREAM_1_ID);
    // FEED_1, stream 1, pipe 2
    public static RefStreamDefinition REF_STREAM_2_DEF = new RefStreamDefinition(
            PIPE_1_UUID, PIPE_1_VER_2, REF_STREAM_1_ID);
    // FEED_1, stream 2, pipe 1
    public static RefStreamDefinition REF_STREAM_3_DEF = new RefStreamDefinition(
            PIPE_1_UUID, PIPE_1_VER_1, REF_STREAM_2_ID);
    // FEED_2, stream 3, pipe 1
    public static RefStreamDefinition REF_STREAM_4_DEF = new RefStreamDefinition(
            PIPE_1_UUID, PIPE_1_VER_1, REF_STREAM_4_ID);

    public static final List<RefStreamDefinition> DEFAULT_REF_STREAM_DEFINITIONS = List.of(
            REF_STREAM_1_DEF,
            REF_STREAM_2_DEF,
            REF_STREAM_3_DEF,
            REF_STREAM_4_DEF);

    private final Provider<ReferenceDataConfig> referenceDataConfigSupplier;
    private final HomeDirProvider homeDirProvider;
    private final TempDirProvider tempDirProvider;

    private Map<Long, String> metaIdToFeedNameMap = new HashMap<>();
    private Map<DocRef, DocRef> docRefs = new HashMap<>();

    public RefDataStoreTestModule(final Provider<ReferenceDataConfig> referenceDataConfigSupplier,
                                  final HomeDirProvider homeDirProvider,
                                  final TempDirProvider tempDirProvider) {
        this.referenceDataConfigSupplier = referenceDataConfigSupplier;
        this.homeDirProvider = homeDirProvider;
        this.tempDirProvider = tempDirProvider;
        addFeeds(FeedDoc.buildDocRef().name(FEED_1_NAME).uuid(FEED_1_UUID).build(),
                FeedDoc.buildDocRef().name(FEED_2_NAME).uuid(FEED_2_UUID).build());
        addMetaFeedAssociations(Map.of(
                REF_STREAM_1_ID, FEED_1_NAME,
                REF_STREAM_2_ID, FEED_1_NAME,
                REF_STREAM_4_ID, FEED_2_NAME));
    }

    @Override
    protected void configure() {
        if (referenceDataConfigSupplier != null) {
            bind(ReferenceDataConfig.class).toProvider(referenceDataConfigSupplier);
        } else {
            bind(ReferenceDataConfig.class).toInstance(new ReferenceDataConfig());
        }
        bind(HomeDirProvider.class).toInstance(homeDirProvider);
        bind(TempDirProvider.class).toInstance(tempDirProvider);

        install(new CacheModule());
        install(new MockSecurityContextModule());
        install(new MockTaskModule());
        install(new PipelineScopeModule());
        install(new RefDataStoreModule());

        bind(MetaService.class)
                .toInstance(getMockMetaService());
        bind(DocRefInfoService.class)
                .toInstance(getMockDocRefInfoService());
    }

    private DocRefInfoService getMockDocRefInfoService() {
        final DocRefInfoService mockDocRefInfoService = Mockito.mock(DocRefInfoService.class);

        Mockito.doAnswer(invocation -> {
            final DocRef docRef = invocation.getArgument(0, DocRef.class);
            final DocRef ourDocRef = docRefs.get(docRef);
            return ourDocRef == null
                    ? docRef
                    : ourDocRef;
        }).when(mockDocRefInfoService).decorate(Mockito.any(DocRef.class));

        Mockito.doAnswer(invocation -> {
            final String type = invocation.getArgument(0, String.class);
            final String feedName = invocation.getArgument(1, String.class);
            return docRefs.keySet().stream()
                    .filter(docRef ->
                            feedName.equals(docRef.getName())
                                    && type.equals(docRef.getType()))
                    .collect(Collectors.toList());
        }).when(mockDocRefInfoService).findByName(
                Mockito.eq(FeedDoc.DOCUMENT_TYPE),
                Mockito.anyString(),
                Mockito.anyBoolean());

        return mockDocRefInfoService;
    }

    private MetaService getMockMetaService() {
        final MetaService mockMetaService = Mockito.mock(MetaService.class);

        Mockito.doAnswer(invocation -> {
            final Long metaId = invocation.getArgument(0, Long.class);
            final String feedName = metaIdToFeedNameMap.get(metaId);
            if (feedName == null) {
                throw new RuntimeException(LogUtil.message(
                        "No mapping set up for metaId {}. See addMetaFeedAssociation()", metaId));
            } else {
                final Meta mockMeta = Mockito.mock(Meta.class);
                Mockito.when(mockMeta.getFeedName())
                        .thenReturn(feedName);
                return mockMeta;
            }
        }).when(mockMetaService).getMeta(Mockito.anyLong());

        return mockMetaService;
    }

    public void addMetaFeedAssociations(final Map<Long, String> metaIdToFeedNameMap) {
        if (metaIdToFeedNameMap != null) {
            this.metaIdToFeedNameMap.putAll(metaIdToFeedNameMap);
        }
    }

    public void addMetaFeedAssociation(final long refStreamId, final String feedName) {
        Objects.requireNonNull(feedName);
        metaIdToFeedNameMap.put(refStreamId, feedName);
    }

    public void addFeeds(final DocRef... docRefs) {
        if (docRefs != null) {
            for (final DocRef docRef : docRefs) {
                Objects.requireNonNull(docRef);
                if (!docRef.getType().equals(FeedDoc.DOCUMENT_TYPE)) {
                    throw new RuntimeException(LogUtil.message("Invalid type " + docRef.getType()));
                }
                this.docRefs.put(docRef, docRef);
            }
        }
    }


    // --------------------------------------------------------------------------------


    public record MetaFeedAssociation(
            long metaId,
            String feedName) {

    }
}
