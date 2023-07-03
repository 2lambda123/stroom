package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.explorer.api.ExplorerDecorator;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.QuickFilterResultPage;
import stroom.util.shared.ResultPage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

public class DependencyServiceImpl implements DependencyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyServiceImpl.class);

    private final ImportExportActionHandlers importExportActionHandlers;
    private final DocRefInfoService docRefInfoService;
    private final TaskContextFactory taskContextFactory;
    private final ExplorerDecorator explorerDecorator;

    private static final Comparator<Dependency> FROM_TYPE_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getFrom, DocRef::getType);
    private static final Comparator<Dependency> FROM_NAME_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getFrom, DocRef::getName);
    private static final Comparator<Dependency> FROM_UUID_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getFrom, DocRef::getUuid);
    private static final Comparator<Dependency> TO_TYPE_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getTo, DocRef::getType);
    private static final Comparator<Dependency> TO_NAME_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getTo, DocRef::getName);
    private static final Comparator<Dependency> TO_UUID_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(Dependency::getTo, DocRef::getUuid);

    private static final Map<String, Comparator<Dependency>> COMPARATOR_MAP = Map.of(
            DependencyCriteria.FIELD_FROM_TYPE, FROM_TYPE_COMPARATOR,
            DependencyCriteria.FIELD_FROM_NAME, FROM_NAME_COMPARATOR,
            DependencyCriteria.FIELD_FROM_UUID, FROM_UUID_COMPARATOR,
            DependencyCriteria.FIELD_TO_TYPE, TO_TYPE_COMPARATOR,
            DependencyCriteria.FIELD_TO_NAME, TO_NAME_COMPARATOR,
            DependencyCriteria.FIELD_TO_UUID, TO_UUID_COMPARATOR,
            DependencyCriteria.FIELD_STATUS, Comparator.comparing(Dependency::isOk)
    );

    private static final FilterFieldMappers<Dependency> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_FROM_TYPE, Dependency::getFrom, DocRef::getType),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_FROM_NAME, Dependency::getFrom, DocRef::getName),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_FROM_UUID, Dependency::getFrom, DocRef::getUuid),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_TO_TYPE, Dependency::getTo, DocRef::getType),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_TO_NAME, Dependency::getTo, DocRef::getName),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_TO_UUID, Dependency::getTo, DocRef::getUuid),
            FilterFieldMapper.of(DependencyCriteria.FIELD_DEF_STATUS,
                    Dependency::isOk,
                    bool -> bool
                            ? "OK"
                            : "Missing")
    );

    @Inject
    public DependencyServiceImpl(final ImportExportActionHandlers importExportActionHandlers,
                                 final DocRefInfoService docRefInfoService,
                                 final TaskContextFactory taskContextFactory,
                                 final ExplorerDecorator explorerDecorator) {
        this.importExportActionHandlers = importExportActionHandlers;
        this.docRefInfoService = docRefInfoService;
        this.taskContextFactory = taskContextFactory;
        this.explorerDecorator = explorerDecorator;
    }

    @Override
    public QuickFilterResultPage<Dependency> getDependencies(final DependencyCriteria criteria) {
        return taskContextFactory.contextResult(
                        "Get Dependencies",
                        taskContext -> {
                            try {
                                return getDependencies(criteria, taskContext);
                            } catch (Exception e) {
                                LOGGER.error("Error getting dependencies for criteria " + criteria, e);
                                throw e;
                            }
                        })
                .get();
    }

    private QuickFilterResultPage<Dependency> getDependencies(final DependencyCriteria criteria,
                                                              final TaskContext parentTaskContext) {
        // Build a map of deps (parent to children)
        final Map<DocRef, Set<DocRef>> allDependencies = buildDependencyMap(parentTaskContext);

        final Optional<Comparator<Dependency>> optSortListComparator = getDependencyComparator(criteria);

//        final Predicate<Dependency> filterPredicate = buildFilterPredicate(criteria);

        // Get the additional types that we use to decorate the explorer tree.
        final Set<DocRef> additionalRefs = new HashSet<>(explorerDecorator.list());

        // Flatten the dependency map
        final List<Dependency> flatDependencies = buildFlatDependencies(
                criteria,
                allDependencies,
                additionalRefs,
                optSortListComparator);

        final ResultPage<Dependency> resultPage = ResultPage.createPageLimitedList(
                flatDependencies,
                Optional.ofNullable(criteria)
                        .map(DependencyCriteria::getPageRequest)
                        .orElse(new PageRequest()));

        return new QuickFilterResultPage<>(
                resultPage,
                QuickFilterPredicateFactory.fullyQualifyInput(criteria.getPartialName(), FIELD_MAPPERS));
    }


    private List<Dependency> buildFlatDependencies(final DependencyCriteria criteria,
                                                   final Map<DocRef, Set<DocRef>> allDependencies,
                                                   final Set<DocRef> pseudoDocRefs,
                                                   final Optional<Comparator<Dependency>> optSortListComparator) {
        final Map<DocRef, Optional<DocRefInfo>> docRefInfoCache = new ConcurrentHashMap<>();
        Stream<Dependency> filteredStream = QuickFilterPredicateFactory.filterStream(
                criteria.getPartialName(),
                FIELD_MAPPERS,
                allDependencies.entrySet()
                        .stream()
                        .flatMap(entry -> {
                            final DocRef parentDocRef = entry.getKey();
                            final Set<DocRef> childDocRefs = entry.getValue();
                            return childDocRefs.stream()
                                    .map(childDocRef -> {
                                        // Resolve doc info.
                                        final Optional<DocRefInfo> parentInfo = docRefInfoCache
                                                .computeIfAbsent(parentDocRef, docRefInfoService::info);
                                        final Optional<DocRefInfo> childInfo = docRefInfoCache
                                                .computeIfAbsent(childDocRef, docRefInfoService::info);

                                        return new Dependency(
                                                parentInfo.map(DocRefInfo::getDocRef).orElse(parentDocRef),
                                                childInfo.map(DocRefInfo::getDocRef).orElse(childDocRef),
                                                pseudoDocRefs.contains(childDocRef) ||
                                                        allDependencies.containsKey(childDocRef));
                                    });
                        }),
                optSortListComparator.orElse(null));


        if (optSortListComparator.isPresent()) {
            filteredStream = filteredStream.sorted(optSortListComparator.get());
        }

        return filteredStream.collect(Collectors.toList());
    }

    private Map<DocRef, Set<DocRef>> buildDependencyMap(final TaskContext parentTaskContext) {
        return importExportActionHandlers
                .getHandlers()
                .values()
                .parallelStream()
                .map(handler ->
                        taskContextFactory.childContextResult(
                                parentTaskContext,
                                "Get " + handler.getType() + " dependencies",
                                taskContext -> {
                                    Map<DocRef, Set<DocRef>> deps = null;
                                    try {
                                        deps = handler.getDependencies();
                                    } catch (final RuntimeException e) {
                                        LOGGER.error(e.getMessage(), e);
                                    }
                                    return deps;
                                }).get())
                .filter(Objects::nonNull)
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) ->
                                Stream.concat(e1.stream(), e2.stream())
                                        .collect(Collectors.toSet())));
    }

    private Optional<Comparator<Dependency>> getDependencyComparator(final DependencyCriteria criteria) {
        // Make the sort comparator base on the criteria sort list
        final Optional<Comparator<Dependency>> sortListComparator;
        if (criteria != null
                && criteria.getSortList() != null
                && !criteria.getSortList().isEmpty()) {
            sortListComparator = buildComparatorFromSortList(criteria);
        } else {
            sortListComparator = Optional.empty();
        }
        return sortListComparator;
    }

    private Optional<Comparator<Dependency>> buildComparatorFromSortList(
            final DependencyCriteria dependencyCriteria) {

        if (dependencyCriteria != null && !dependencyCriteria.getSortList().isEmpty()) {
            Comparator<Dependency> compositeComparator = null;

            for (final CriteriaFieldSort sort : dependencyCriteria.getSortList()) {
                Comparator<Dependency> comparator = COMPARATOR_MAP.get(sort.getId());
                if (comparator != null) {
                    if (sort.isDesc()) {
                        comparator = comparator.reversed();
                    }
                    compositeComparator = compositeComparator != null
                            ? compositeComparator.thenComparing(comparator)
                            : comparator;
                }
            }
            return compositeComparator != null
                    ? Optional.of(compositeComparator)
                    : Optional.empty();
        } else {
            // Unsorted
            return Optional.empty();
        }
    }

//    private static  Comparator<Dependency> getComparator(
//            final Function<Dependency, DocRef> docRefExtractor,
//            final Function<DocRef, String> valueExtractor) {
//
//        // Sort with nulls first but also handle deps with null docref
//        return Comparator.comparing(
//                docRefExtractor,
//                Comparator.nullsFirst(
//                        Comparator.comparing(
//                                valueExtractor,
//                                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))));
//    }

}
