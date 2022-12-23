/*
 * Copyright 2017 Crown Copyright
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

package stroom.search.elastic.indexing;

import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.pipeline.xml.converter.json.JSONParser;
import stroom.processor.shared.ProcessorFilter;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticConfig;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexConstants;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.DocWriteRequest.OpType;
import org.elasticsearch.action.bulk.BulkItemResponse.Failure;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation.Bucket;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.NotFoundException;

/**
 * Accepts `json` schema XML and sends documents to Elasticsearch as batches for indexing
 */
@ConfigurableElement(type = "ElasticIndexingFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET,
        PipelineElementType.ROLE_HAS_TARGETS,
        PipelineElementType.VISABILITY_SIMPLE
}, icon = ElementIcons.ELASTIC_INDEX)
class ElasticIndexingFilter extends AbstractXMLFilter {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexingFilter.class);
    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final int INITIAL_JSON_STREAM_SIZE_BYTES = 1024;
    private static final int ES_MAX_EXCEPTION_CHARS = 1024;
    private static final String ES_TOO_MANY_REQUESTS_STATUS = "TOO_MANY_REQUESTS";

    // Dependencies
    private final LocationFactoryProxy locationFactory;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final Provider<ElasticConfig> elasticConfigProvider;
    private final ElasticClientCache elasticClientCache;
    private final ElasticClusterStore elasticClusterStore;
    private final PipelineStore pipelineStore;
    private final StreamProcessorHolder streamProcessorHolder;
    private final MetaHolder metaHolder;

    // Pipeline filter configuration options
    private int batchSize = 10000;
    private boolean purgeOnReprocess = true;
    private String ingestPipelineName = null;
    private boolean refreshAfterEachBatch = false;
    private DocRef clusterRef;
    private String indexBaseName;
    private String indexNameDateFormat;
    private String indexNameDateFieldName = "@timestamp";
    private Date indexNameDateMin;
    private SimpleDateFormat indexNameDateMinFormat;
    private StroomDuration indexNameDateMaxFutureOffset;

    // Cached entities
    ElasticClusterDoc elasticCluster;
    String pipelineName;

    // State
    private final List<IndexRequest> indexRequests;
    private final ByteArrayOutputStream currentDocument;
    private final StringBuilder valueBuffer = new StringBuilder();
    private String currentDocFieldName = null;
    private int currentDocPropertyCount = 0;
    private boolean inOuterArray = false;
    private int currentDepth = 0;
    private String currentDocTimestamp = null;
    private JsonGenerator jsonGenerator;
    private int currentRetry;

    private Locator locator;

    @Inject
    ElasticIndexingFilter(
            final LocationFactoryProxy locationFactory,
            final ErrorReceiverProxy errorReceiverProxy,
            final Provider<ElasticConfig> elasticConfigProvider,
            final ElasticClientCache elasticClientCache,
            final ElasticClusterStore elasticClusterStore,
            final PipelineStore pipelineStore,
            final StreamProcessorHolder streamProcessorHolder,
            final MetaHolder metaHolder) {
        this.locationFactory = locationFactory;
        this.errorReceiverProxy = errorReceiverProxy;
        this.elasticConfigProvider = elasticConfigProvider;
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
        this.pipelineStore = pipelineStore;
        this.streamProcessorHolder = streamProcessorHolder;
        this.metaHolder = metaHolder;

        indexRequests = new ArrayList<>();
        currentDocument = new ByteArrayOutputStream(INITIAL_JSON_STREAM_SIZE_BYTES);
        currentRetry = 0;

        indexNameDateMinFormat = new SimpleDateFormat("yyyy");
        indexNameDateMinFormat.setTimeZone(TimeZone.getTimeZone("UTC)"));
        indexNameDateMaxFutureOffset = StroomDuration.parse("P1D");
    }

    /**
     * Initialise
     */
    @Override
    public void startProcessing() {
        try {
            if (clusterRef == null) {
                fatalError("Elasticsearch cluster ref has not been set", new NotFoundException());
            }

            if (indexBaseName == null || indexBaseName.isEmpty()) {
                fatalError("Index name has not been set", new InvalidParameterException());
            }

            pipelineName = pipelineStore.readDocument(streamProcessorHolder.getStreamProcessor().getPipeline())
                    .getName();
            elasticCluster = elasticClusterStore.readDocument(clusterRef);
            final ElasticConnectionConfig connectionConfig = elasticCluster.getConnection();

            elasticClientCache.context(connectionConfig, elasticClient -> {
                try {
                    // If this is a reprocessing filter, delete any documents from the target index that have the same
                    // `StreamId` as the stream that's being reprocessed. This prevents duplicate documents.
                    final ProcessorFilter processorFilter = this.streamProcessorHolder.getStreamTask()
                            .getProcessorFilter();
                    if (purgeOnReprocess && processorFilter.isReprocess()) {
                        final Meta meta = this.metaHolder.getMeta();
                        final long streamId = meta.getId();
                        if (!purgeDocumentsForStream(elasticClient, streamId)) {
                            throw new RuntimeException("Failed to purge existing documents for StreamId " + streamId);
                        }
                    }
                } catch (final RuntimeException e) {
                    fatalError(e.getMessage(), e);
                }
            });

            jsonGenerator = JSON_FACTORY.createGenerator(currentDocument);

        } catch (IOException e) {
            fatalError("Failed to initialise JsonGenerator", e);
        } finally {
            super.startProcessing();
        }
    }

    @Override
    public void endProcessing() {
        try {
            // Send any remaining documents
            indexDocuments();
        } finally {
            super.endProcessing();
        }
    }

    /**
     * Sets the locator to use when reporting errors.
     *
     * @param locator The locator to use.
     */
    @Override
    public void setDocumentLocator(final Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes attributes)
            throws SAXException {

        if (!inOuterArray && currentDepth == 0) {
            // We are now inside the outer `array` element
            if (localName.equals(JSONParser.XML_ELEMENT_ARRAY)) {
                // Starting a new root JSON document array. Any `map` elements from here will be added as documents
                // for indexing
                inOuterArray = true;
            } else {
                // Terminate processing as this is a fatal error
                fatalError("Expected an array at start of document, got '" + localName + "' instead",
                        new IllegalArgumentException());
            }
        } else {
            // Get the name of the JSON property
            currentDocFieldName = attributes.getValue(JSONParser.XML_ATTRIBUTE_KEY);

            switch (localName) {
                case JSONParser.XML_ELEMENT_MAP:
                    try {
                        incrementDepth();
                        writeFieldName();
                        jsonGenerator.writeStartObject();
                    } catch (IOException e) {
                        fatalError("Invalid start of object", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_ARRAY:
                    try {
                        incrementDepth();
                        writeFieldName();
                        jsonGenerator.writeStartArray();
                    } catch (IOException e) {
                        fatalError("Invalid start of array", e);
                    }
                    break;
            }
        }

        // Starting a new value, so clear the existing one
        valueBuffer.setLength(0);

        super.startElement(uri, localName, qName, attributes);
    }

    private void incrementDepth() {
        final int maxDepth = elasticConfigProvider.get().getIndexingConfig().getMaxNestedElementDepth();

        currentDepth++;

        if (currentDepth > maxDepth) {
            fatalError("Maximum nested element depth of " + maxDepth + " exceeded", new RuntimeException());
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        final String value;

        if (inOuterArray && currentDepth == 0) {
            // We are at the end of the outer `array` element
            if (localName.equals(JSONParser.XML_ELEMENT_ARRAY)) {
                // Consider this to be the end of the stream part
                inOuterArray = false;
            } else {
                fatalError("Expected an end array to close the document, got '" + localName + "' instead",
                        new IllegalArgumentException());
            }
        } else {
            switch (localName) {
                case JSONParser.XML_ELEMENT_MAP:
                    try {
                        currentDepth--;
                        jsonGenerator.writeEndObject();
                        if (currentDepth == 0) {
                            // We have closed out an outer `map`, so queue the document for indexing
                            processDocument();
                        }
                    } catch (IOException e) {
                        fatalError("Invalid end of object", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_ARRAY:
                    try {
                        currentDepth--;
                        jsonGenerator.writeEndArray();
                    } catch (IOException e) {
                        fatalError("Invalid end of array", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_STRING:
                    value = valueBuffer.toString();
                    try {
                        if (value.length() > 0) {
                            writeFieldName();
                            jsonGenerator.writeString(value);
                            currentDocPropertyCount++;
                        }
                        if (currentDocFieldName != null && currentDocFieldName.equals(indexNameDateFieldName)) {
                            // This is the timestamp field, so store its value for formatting the full index name
                            currentDocTimestamp = value;
                        }
                    } catch (IOException e) {
                        fatalError("Invalid string value '" + value + "' for property '" +
                                currentDocFieldName + "'", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_BOOLEAN:
                    value = valueBuffer.toString();
                    try {
                        if (value.length() > 0) {
                            writeFieldName();
                            jsonGenerator.writeBoolean(Boolean.parseBoolean(value));
                            currentDocPropertyCount++;
                        }
                    } catch (IOException e) {
                        fatalError("Invalid boolean value '" + value + "' for property '" +
                                currentDocFieldName + "'", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_NULL:
                    try {
                        writeFieldName();
                        jsonGenerator.writeNull();
                        currentDocPropertyCount++;
                    } catch (IOException e) {
                        fatalError("Invalid null value for property '" + currentDocFieldName + "'", e);
                    }
                    break;
                case JSONParser.XML_ELEMENT_NUMBER:
                    value = valueBuffer.toString();
                    try {
                        if (value.length() > 0) {
                            writeFieldName();
                            jsonGenerator.writeNumber(value);
                            currentDocPropertyCount++;
                        }
                    } catch (IOException e) {
                        fatalError("Invalid number value '" + value + "' for property '" +
                                currentDocFieldName + "'", e);
                    }
                    break;
            }
        }

        valueBuffer.setLength(0);
        currentDocFieldName = null;

        super.endElement(uri, localName, qName);
    }

    /**
     * @param ch     the characters from the XML document
     * @param start  the start position in the array
     * @param length the number of characters to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly wrapping another exception
     * @see #ignorableWhitespace
     * @see org.xml.sax.Locator
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        valueBuffer.append(ch, start, length);
        super.characters(ch, start, length);
    }

    private boolean writeFieldName() throws IOException {
        if (currentDocFieldName != null) {
            jsonGenerator.writeFieldName(currentDocFieldName);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Queue a JSON document for indexing
     */
    private void processDocument() {
        try {
            if (currentDocPropertyCount > 0) {
                jsonGenerator.flush();

                final IndexRequest indexRequest = new IndexRequest(formatIndexName())
                        .opType(OpType.CREATE)
                        .source(currentDocument.toByteArray(), XContentType.JSON);

                // If an ingest pipeline name is specified, execute it when ingesting the document
                if (ingestPipelineName != null && !ingestPipelineName.isEmpty()) {
                    indexRequest.setPipeline(ingestPipelineName);
                }

                indexRequests.add(indexRequest);
            }

            if (indexRequests.size() >= batchSize) {
                indexDocuments();
            }
        } catch (IOException e) {
            fatalError("Failed to flush JSON to stream", e);
        } catch (Exception e) {
            fatalError(e.getMessage(), e);
        } finally {
            clearDocument();
        }
    }

    private void clearDocument() {
        // Discard the document
        currentDocument.reset();
        currentDocTimestamp = null;

        // Reset the count of how many fields we have indexed for the current event
        currentDocPropertyCount = 0;
    }

    /**
     * Delete documents from the target index, where the indexed field `StreamId` matches one of the provided IDs
     */
    private boolean purgeDocumentsForStream(final RestHighLevelClient elasticClient, final Long streamId)
            throws LoggedException {
        final List<String> indexNames = getTargetIndexNames(elasticClient, streamId);
        if (indexNames != null && !indexNames.isEmpty()) {
            LOGGER.debug("Purging documents for StreamId {} from indices: {}", streamId, indexNames);
            try {
                // Delete against one index at a time. We don't delete against all indices, as this may cause
                // the cluster limit `search.max_open_scroll_context` to be exceeded.
                for (final String indexName : indexNames) {
                    final DeleteByQueryRequest deleteRequest = new DeleteByQueryRequest(indexName)
                            .setQuery(new TermQueryBuilder(ElasticIndexConstants.STREAM_ID, streamId))
                            .setRefresh(true);

                    final BulkByScrollResponse deleteResponse = elasticClient.deleteByQuery(deleteRequest,
                            RequestOptions.DEFAULT);
                    final long deletedCount = deleteResponse.getDeleted();

                    LOGGER.info("Deleted {} documents matching StreamId: {} from index: {}, took {} seconds",
                            deletedCount, streamId, indexName, deleteResponse.getTook().getSecondsFrac());
                }
            } catch (IOException e) {
                fatalError("Failed to purge documents for StreamId: " + streamId, e);
                return false;
            }
        }
        return true;
    }

    /**
     * Given a StreamId, retrieve a list of names of all indices containing matching documents
     */
    private List<String> getTargetIndexNames(final RestHighLevelClient elasticClient, final Long streamId)
            throws LoggedException {
        final String indicesAggregationKey = "indices";
        final String streamIdSourceKey = "streamId";

        List<CompositeValuesSourceBuilder<?>> sources = new ArrayList<>();
        sources.add(new TermsValuesSourceBuilder(streamIdSourceKey)
                .field(ElasticIndexConstants.INDEX_ID)
        );

        // Create a composite aggregation to collect all the unique indices that we need to issue delete requests
        // against
        try {
            List<String> indexNames = new ArrayList<>();
            Map<String, Object> afterKey = null;
            int bucketSize = -1;
            while (bucketSize != 0) {
                final CompositeAggregationBuilder compositeAgg = AggregationBuilders
                        .composite(indicesAggregationKey, sources);
                if (afterKey != null) {
                    compositeAgg.aggregateAfter(afterKey);
                }
                final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                        .size(100) // Number of index names to retrieve at a time, until we have all of them
                        .query(new TermQueryBuilder(ElasticIndexConstants.STREAM_ID, streamId))
                        .aggregation(compositeAgg);
                final SearchRequest searchRequest = new SearchRequest(indexBaseName + "*")
                        .source(searchSourceBuilder);
                final SearchResponse searchResponse = elasticClient.search(searchRequest, RequestOptions.DEFAULT);
                final Aggregations aggregations = searchResponse.getAggregations();
                if (aggregations != null) {
                    final CompositeAggregation aggregation = aggregations.get(indicesAggregationKey);
                    final List<? extends Bucket> buckets = aggregation.getBuckets();
                    bucketSize = buckets.size();
                    for (final var bucket : buckets) {
                        indexNames.add((String) bucket.getKey().get(streamIdSourceKey));
                    }
                    afterKey = aggregation.afterKey();
                } else {
                    // No index exists for this stream, so no deletion necessary
                    return null;
                }
            }
            return indexNames;
        } catch (IOException e) {
            fatalError("Failed to list indices for reindex purge. StreamId: " + streamId + ". " +
                    "Base name: '" + indexBaseName + "'", e);
            return null;
        }
    }

    /**
     * Index the current batch of documents
     */
    private void indexDocuments() {
        if (indexRequests.size() == 0) {
            return;
        }

        try {
            final AtomicBoolean succeeded = new AtomicBoolean(false);
            while (!succeeded.get()) {
                elasticClientCache.context(elasticCluster.getConnection(), elasticClient -> {
                    try {
                        // Create a new bulk indexing request, containing the current batch of documents
                        final BulkRequest bulkRequest = new BulkRequest();

                        // For each document, create an indexing request and append to the bulk request
                        for (IndexRequest indexRequest : indexRequests) {
                            bulkRequest.add(indexRequest);
                        }

                        if (refreshAfterEachBatch) {
                            // Refresh upon completion of the batch index request
                            bulkRequest.setRefreshPolicy(RefreshPolicy.IMMEDIATE);
                        } else {
                            // Only refresh after all batches have been indexed
                            bulkRequest.setRefreshPolicy(RefreshPolicy.NONE);
                        }

                        final BulkResponse response = elasticClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                        if (response.hasFailures()) {
                            for (final var bulkResponse : response.getItems()) {
                                final Failure failure = bulkResponse.getFailure();
                                if (failure != null && failure.getStatus() != null &&
                                        ES_TOO_MANY_REQUESTS_STATUS.equals(failure.getStatus().name())) {
                                    throw new ElasticsearchOverloadedException(response.buildFailureMessage());
                                }
                            }
                            // Request failed for some other reason, so abort without retry
                            throw new IOException("Bulk indexing request failed: " + response.buildFailureMessage());
                        } else {
                            succeeded.set(true);
                            final String retryMessage = currentRetry > 0 ? " (retries: " + currentRetry + ")" : "";
                            LOGGER.info("Pipeline '{}' indexed {} documents to Elasticsearch cluster '{}' in " +
                                            "{} seconds" + retryMessage,
                                    pipelineName, indexRequests.size(), elasticCluster.getName(),
                                    response.getTook().getSecondsFrac());
                        }
                    } catch (ElasticsearchStatusException | ElasticsearchOverloadedException e) {
                        handleElasticsearchException(e);
                    } catch (RuntimeException | IOException e) {
                        fatalError(e.getMessage() != null ? e.getMessage().substring(0,
                                        Math.min(ES_MAX_EXCEPTION_CHARS, e.getMessage().length())) : "", e);
                    } finally {
                        currentRetry++;
                    }
                });
            }
        } finally {
            currentRetry = 0;
            indexRequests.clear();
        }
    }

    /**
     * Elasticsearch rejected the indexing request, because it is overloaded and
     * cannot queue the batched payload. Retry after a delay, if we haven't exceeded the retry
     * limit. Otherwise, terminate this thread and create an `Error` stream.
     * @param e
     */
    private void handleElasticsearchException(final RuntimeException e) {
        String statusName = "";
        if (e instanceof ElasticsearchStatusException) {
            statusName = ((ElasticsearchStatusException) e).status().name();
        }
        final String errorDetailMsg = e.getMessage() != null ? e.getMessage().substring(0,
                Math.min(ES_MAX_EXCEPTION_CHARS, e.getMessage().length())) : "";
        if (e instanceof ElasticsearchOverloadedException || ES_TOO_MANY_REQUESTS_STATUS.equals(statusName)) {
            final ElasticIndexingConfig indexingConfig = elasticConfigProvider.get().getIndexingConfig();
            if (currentRetry < indexingConfig.getRetryCount()) {
                final long sleepDurationMs = indexingConfig.getInitialRetryBackoffPeriodMs() *
                        ((long) currentRetry + 1);
                try {
                    LOGGER.warn("Indexing request by pipeline '" + pipelineName + "' was rejected by " +
                            "Elasticsearch. Retrying in " + sleepDurationMs + " milliseconds " +
                            "(retries: " + currentRetry + ")");
                    Thread.sleep(sleepDurationMs);
                } catch (InterruptedException ex) {
                    fatalError("Indexing terminated after " + currentRetry + " retries: " +
                            errorDetailMsg, ex);
                }
            } else {
                fatalError("Indexing failed to complete after " + currentRetry +
                        " retries: " + errorDetailMsg, null);
            }
        } else {
            fatalError("Indexing failed to complete after " + currentRetry +
                    " retries: " + errorDetailMsg, null);
        }
    }

    /**
     * Where an index name date pattern is specified, formats the value of the document timestamp field
     * using the date pattern. Otherwise, the index base name is returned.
     * @return Index base name appended with the formatted document timestamp
     */
    private String formatIndexName() {
        // If a date format has specified, append the formatted timestamp field
        // value to the index base name
        if (currentDocTimestamp == null || indexNameDateFormat == null || indexNameDateFormat.isEmpty()) {
            return indexBaseName;
        } else {
            try {
                final ZonedDateTime eventTime = ZonedDateTime.parse(currentDocTimestamp);
                final DateTimeFormatter pattern = DateTimeFormatter.ofPattern(indexNameDateFormat);

                // If the event occurred after now + max offset, don't append a time suffix to the index name
                if (!indexNameDateMaxFutureOffset.isZero()) {
                    final ZonedDateTime timeNow = ZonedDateTime.now(eventTime.getZone());
                    final ZonedDateTime nowPlusOffset = timeNow.plusSeconds(
                            indexNameDateMaxFutureOffset.get(ChronoUnit.SECONDS));
                    if (eventTime.isAfter(nowPlusOffset)) {
                        return indexBaseName;
                    }
                }

                // If the event occurred before the minimum date, don't append a time suffix to the index name
                if (indexNameDateMin != null) {
                    if (eventTime.isBefore(indexNameDateMin.toInstant().atZone(ZoneId.of("UTC")))) {
                        return indexBaseName;
                    }
                }

                return indexBaseName + pattern.format(eventTime);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(String.format("Invalid index name date format: %s", indexNameDateFormat));
            } catch (Exception e) {
                throw new NotFoundException(String.format("Field %s not found in document, which is " +
                        "required as property `indexNameDateFormat` has been set", indexNameDateFieldName));
            }
        }
    }

    @PipelineProperty(
            description = "Target Elasticsearch cluster",
            displayPriority = 1
    )
    @PipelinePropertyDocRef(types = ElasticClusterDoc.DOCUMENT_TYPE)
    public void setCluster(final DocRef clusterRef) {
        this.clusterRef = clusterRef;
    }

    @PipelineProperty(
            description = "Maximum number of documents to index in each bulk request",
            defaultValue = "10000",
            displayPriority = 2
    )
    public void setBatchSize(final int batchSize) {
        this.batchSize = batchSize;
    }

    @PipelineProperty(
            description = "Refresh the index after each batch is processed, making the indexed documents visible to " +
                    "searches",
            defaultValue = "false",
            displayPriority = 3
    )
    public void setRefreshAfterEachBatch(final boolean refreshAfterEachBatch) {
        this.refreshAfterEachBatch = refreshAfterEachBatch;
    }

    @PipelineProperty(
            description = "Name of the Elasticsearch ingest pipeline to execute when indexing",
            displayPriority = 4
    )
    public void setIngestPipeline(final String ingestPipelineName) {
        this.ingestPipelineName = ingestPipelineName;
    }

    @PipelineProperty(
            description = "Name of the Elasticsearch index",
            displayPriority = 5
    )
    public void setIndexBaseName(final String indexBaseName) {
        this.indexBaseName = indexBaseName;
    }

    @PipelineProperty(
            description = "Format of the date to append to the index name (example: `-yyyy`). If unspecified, no " +
                    "date is appended.",
            displayPriority = 6
    )
    public void setIndexNameDateFormat(final String indexNameDateFormat) {
        this.indexNameDateFormat = indexNameDateFormat;
    }

    @PipelineProperty(
            description = "Name of the field containing the `DateTime` value to use when determining the index date " +
                    "suffix",
            defaultValue = "@timestamp",
            displayPriority = 7
    )
    public void setIndexNameDateFieldName(final String indexNameDateFieldName) {
        this.indexNameDateFieldName = indexNameDateFieldName;
    }

    @PipelineProperty(
            description = "Do not append a time suffix to the index name for events occurring before this date. " +
                    "Date is assumed to be in UTC and of the format specified in `indexNameDateMinFormat`",
            displayPriority = 8
    )
    public void setIndexNameDateMin(final String indexNameDateMin) {
        try {
            if (indexNameDateMin != null && !indexNameDateMin.isEmpty() && indexNameDateMinFormat != null) {
                this.indexNameDateMin = indexNameDateMinFormat.parse(indexNameDateMin);
            } else {
                this.indexNameDateMin = null;
            }
        } catch (DateTimeParseException e) {
            this.indexNameDateMin = null;
            fatalError("Invalid value " + indexNameDateMin + " provided for indexNameDateMin", e);
        } catch (Exception e) {
            fatalError("Error occurred when parsing date value: " + indexNameDateMin, e);
        }
    }

    @PipelineProperty(
            description = "Date format of the supplied `indexNameDateMin` property",
            defaultValue = "yyyy",
            displayPriority = 9
    )
    public void setIndexNameDateMinFormat(final String indexNameDateMinFormat) {
        try {
            this.indexNameDateMinFormat = new SimpleDateFormat(indexNameDateMinFormat);
            this.indexNameDateMinFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        } catch (IllegalArgumentException e) {
            this.indexNameDateMinFormat = null;
            fatalError("Invalid date format " + indexNameDateMinFormat + " provided for " +
                    "indexNameDateMinFormat", e);
        }
    }

    @PipelineProperty(
            description = "Do not append a time suffix to the index name for events occurring after the current " +
                    "time plus the specified offset",
            defaultValue = "P1D",
            displayPriority = 10
    )
    public void setIndexNameDateMaxFutureOffset(final String indexNameDateMaxFutureOffset) {
        this.indexNameDateMaxFutureOffset = StroomDuration.parse(indexNameDateMaxFutureOffset);
    }

    @PipelineProperty(
            description = "When reprocessing a stream, first delete any documents from the index matching the " +
                    "stream ID",
            defaultValue = "true",
            displayPriority = 11
    )
    public void setPurgeOnReprocess(final boolean purgeOnReprocess) {
        this.purgeOnReprocess = purgeOnReprocess;
    }

    private void log(final Severity severity, final String message, final Exception e) {
        errorReceiverProxy.log(severity, locationFactory.create(locator), getElementId(), message, e);
    }

    /**
     * @param message - Message to send to the error receiver and log target
     * @param e - Original exception (optional - omit if output is likely to be excessive)
     * @throws LoggedException
     */
    private void fatalError(final String message, final Exception e) throws LoggedException {
        // Terminate processing as this is a fatal error
        log(Severity.FATAL_ERROR, message, e);

        if (e != null) {
            throw new LoggedException(message, e);
        } else {
            throw new LoggedException(message);
        }
    }

    private static class ElasticsearchOverloadedException extends RuntimeException {
        public ElasticsearchOverloadedException(final String message) {
            super(message);
        }
    }
}
