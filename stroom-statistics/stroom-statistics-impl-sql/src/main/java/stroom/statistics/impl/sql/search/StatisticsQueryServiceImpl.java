package stroom.statistics.impl.sql.search;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.security.api.SecurityContext;
import stroom.statistics.impl.sql.SQLStatisticCacheImpl;
import stroom.statistics.impl.sql.StatisticsQueryService;
import stroom.statistics.impl.sql.entity.StatisticStoreCache;
import stroom.statistics.impl.sql.entity.StatisticsDataSourceProvider;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unused") //used by DI
public class StatisticsQueryServiceImpl implements StatisticsQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsQueryServiceImpl.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SQLStatisticCacheImpl.class);

    public static final long PROCESS_PAYLOAD_INTERVAL_SECS = 1L;

    private final StatisticsDataSourceProvider statisticsDataSourceProvider;
    private final StatisticStoreCache statisticStoreCache;
    private final SearchResponseCreatorManager searchResponseCreatorManager;
    private final SecurityContext securityContext;

    @Inject
    public StatisticsQueryServiceImpl(final StatisticsDataSourceProvider statisticsDataSourceProvider,
                                      final StatisticStoreCache statisticStoreCache,
                                      final SqlStatisticsSearchResponseCreatorManager searchResponseCreatorManager,
                                      final SecurityContext securityContext) {
        this.statisticsDataSourceProvider = statisticsDataSourceProvider;
        this.statisticStoreCache = statisticStoreCache;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.securityContext = securityContext;
    }


    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            LOGGER.debug("getDataSource called for docRef {}", docRef);
            return statisticsDataSourceProvider.getDataSource(docRef);
        });
    }

    @Override
    public SearchResponse search(final SearchRequest searchRequest) {
        return securityContext.useAsReadResult(() -> {
            LOGGER.debug("search called for searchRequest {}", searchRequest);

            // Replace expression parameters.
            final Query query = searchRequest.getQuery();
            if (query != null) {
                ExpressionOperator expression = query.getExpression();
                final Map<String, String> paramMap = ExpressionParamUtil.createParamMap(query.getParams());
                expression = ExpressionUtil.replaceExpressionParameters(expression, paramMap);
                query.setExpression(expression);
            }

            final DocRef docRef = Preconditions.checkNotNull(
                    Preconditions.checkNotNull(
                            Preconditions.checkNotNull(searchRequest)
                                    .getQuery())
                            .getDataSource());
            Preconditions.checkNotNull(searchRequest.getResultRequests(), "searchRequest must have at least one resultRequest");
            Preconditions.checkArgument(!searchRequest.getResultRequests().isEmpty(), "searchRequest must have at least one resultRequest");

            final StatisticStoreDoc statisticStoreEntity = statisticStoreCache.getStatisticsDataSource(docRef);

            if (statisticStoreEntity == null) {
                return buildEmptyResponse(
                        searchRequest,
                        "Statistic configuration could not be found for uuid " + docRef.getUuid());
            } else {
                return buildResponse(searchRequest, statisticStoreEntity);
            }
        });
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        LOGGER.debug("destroy called for queryKey {}", queryKey);
        // remove the creator from the cache which will trigger the onRemove listener
        // which will call destroy on the store
        searchResponseCreatorManager.remove(new SearchResponseCreatorCache.Key(queryKey));
        return Boolean.TRUE;
    }

    private SearchResponse buildResponse(final SearchRequest searchRequest,
                                         final StatisticStoreDoc statisticStoreEntity) {

        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(statisticStoreEntity);


        // This will create/get a searchResponseCreator for this query key
        final SearchResponseCreator searchResponseCreator = searchResponseCreatorManager.get(
                new SearchResponseCreatorCache.Key(searchRequest));

        // This will build a response from the search whether it is still running or has finished
        final SearchResponse searchResponse = searchResponseCreator.create(searchRequest);

        return searchResponse;
    }

    private SearchResponse buildEmptyResponse(final SearchRequest searchRequest, final String errorMessage) {
        return buildEmptyResponse(searchRequest, Collections.singletonList(errorMessage));
    }

    private SearchResponse buildEmptyResponse(final SearchRequest searchRequest, final List<String> errorMessages) {

        List<Result> results;
        if (searchRequest.getResultRequests() != null) {
            results = searchRequest.getResultRequests().stream()
                    .map(resultRequest -> new TableResult(
                            resultRequest.getComponentId(),
                            Collections.emptyList(),
                            Collections.emptyList(),
                            new OffsetRange(0, 0),
                            0,
                            null))
                    .collect(Collectors.toList());
        } else {
            results = Collections.emptyList();
        }

        return new SearchResponse(
                Collections.emptyList(),
                results,
                errorMessages,
                true);
    }
}
