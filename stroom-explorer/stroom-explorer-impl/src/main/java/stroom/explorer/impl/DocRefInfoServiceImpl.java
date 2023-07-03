package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.shared.DocumentType;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;

class DocRefInfoServiceImpl implements DocRefInfoService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocRefInfoServiceImpl.class);

    private final DocRefInfoCache docRefInfoCache;
    private final SecurityContext securityContext;
    private final ExplorerActionHandlers explorerActionHandlers;

    @Inject
    DocRefInfoServiceImpl(final DocRefInfoCache docRefInfoCache,
                          final SecurityContext securityContext,
                          final ExplorerActionHandlers explorerActionHandlers) {
        this.docRefInfoCache = docRefInfoCache;
        this.securityContext = securityContext;
        this.explorerActionHandlers = explorerActionHandlers;
    }

    @Override
    public List<DocRef> findByType(final String type) {
        Objects.requireNonNull(type);
        return securityContext.asProcessingUserResult(() -> {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(type);
            Objects.requireNonNull(handler, () -> "No handler for type " + type);
            return new ArrayList<>(handler.listDocuments());
        });
    }

    @Override
    public Optional<DocRefInfo> info(final DocRef docRef) {
        return docRefInfoCache.get(docRef);
    }

    @Override
    public Optional<String> name(final DocRef docRef) {
        return info(docRef)
                .map(DocRefInfo::getDocRef)
                .map(DocRef::getName);
    }

    @Override
    public List<DocRef> findByName(final String type,
                                   final String nameFilter,
                                   final boolean allowWildCards) {
        if (NullSafe.isEmptyString(nameFilter)) {
            return Collections.emptyList();
        } else {
            return securityContext.asProcessingUserResult(() -> {
                if (type == null) {
                    final List<DocRef> result = new ArrayList<>();
                    for (final DocumentType documentType : explorerActionHandlers.getTypes()) {
                        final ExplorerActionHandler handler =
                                explorerActionHandlers.getHandler(documentType.toString());
                        if (handler != null) {
                            result.addAll(handler.findByName(nameFilter, allowWildCards));
                        }
                    }
                    return result;
                } else {
                    final ExplorerActionHandler handler = explorerActionHandlers.getHandler(type);
                    Objects.requireNonNull(handler, () -> "No handler for type " + type);
                    return handler.findByName(nameFilter, allowWildCards);
                }
            });
        }
    }

    @Override
    public List<DocRef> findByNames(final String type,
                                    final List<String> nameFilters,
                                    final boolean allowWildCards) {
        Objects.requireNonNull(type);
        if (NullSafe.isEmptyCollection(nameFilters)) {
            return Collections.emptyList();
        } else {
            return securityContext.asProcessingUserResult(() -> {
                final ExplorerActionHandler handler = explorerActionHandlers.getHandler(type);
                Objects.requireNonNull(handler, () -> "No handler for type " + type);
                return handler.findByNames(nameFilters, allowWildCards);
            });
        }
    }

    @Override
    public List<DocRef> decorate(final List<DocRef> docRefs) {
        if (NullSafe.isEmptyCollection(docRefs)) {
            return Collections.emptyList();
        } else {
            return docRefs.stream()
                    .filter(Objects::nonNull)
                    .map(this::decorate)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public DocRef decorate(final DocRef docRef) {
        Objects.requireNonNull(docRef);
        Objects.requireNonNull(docRef.getType(), "DocRef type is not set.");
        Objects.requireNonNull(docRef.getUuid(), "DocRef UUID is not set.");

        if (NullSafe.isEmptyString(docRef.getName())) {
            return docRefInfoCache.get(docRef)
                    .map(DocRefInfo::getDocRef)
                    .orElseThrow(() -> new RuntimeException("No docRefInfo for docRef: " + docRef));
        } else {
            return docRef;
        }
    }
}
