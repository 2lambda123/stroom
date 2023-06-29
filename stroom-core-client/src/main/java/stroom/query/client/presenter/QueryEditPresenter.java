/*
 * Copyright 2022 Crown Copyright
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

package stroom.query.client.presenter;

import stroom.core.client.event.WindowCloseEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.HasToolbar;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.client.presenter.QueryEditPresenter.QueryEditView;
import stroom.query.client.presenter.QueryHelpPresenter.HelpItemType;
import stroom.query.client.presenter.QueryHelpPresenter.QueryHelpDataSupplier;
import stroom.util.shared.GwtNullSafe;
import stroom.view.client.presenter.DataSourceFieldsMap;
import stroom.view.client.presenter.IndexLoader;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class QueryEditPresenter
        extends MyPresenterWidget<QueryEditView>
        implements HasDirtyHandlers, HasToolbar {

    private static final Set<HelpItemType> SUPPORTED_HELP_TYPES = EnumSet.of(
            HelpItemType.DATA_SOURCE,
            HelpItemType.STRUCTURE,
            HelpItemType.FIELD,
            HelpItemType.FUNCTION);

    private final QueryHelpPresenter queryHelpPresenter;
    private final QueryToolbarPresenter queryToolbarPresenter;
    private final EditorPresenter editorPresenter;
    private final QueryResultTablePresenter tablePresenter;
    private final IndexLoader indexLoader;
    private final Views views;
    private boolean dirty;
    private boolean reading;
    private boolean readOnly = true;
    private final QueryModel queryModel;

    @Inject
    public QueryEditPresenter(final EventBus eventBus,
                              final QueryEditView view,
                              final QueryHelpPresenter queryHelpPresenter,
                              final QueryToolbarPresenter queryToolbarPresenter,
                              final EditorPresenter editorPresenter,
                              final QueryResultTablePresenter tablePresenter,
                              final RestFactory restFactory,
                              final IndexLoader indexLoader,
                              final Views views,
                              final DateTimeSettingsFactory dateTimeSettingsFactory,
                              final ResultStoreModel resultStoreModel) {
        super(eventBus, view);
        this.queryHelpPresenter = queryHelpPresenter;
        this.queryToolbarPresenter = queryToolbarPresenter;
        this.tablePresenter = tablePresenter;
        this.indexLoader = indexLoader;
        this.views = views;

        queryModel = new QueryModel(
                restFactory,
                indexLoader,
                dateTimeSettingsFactory,
                resultStoreModel,
                tablePresenter);
        queryModel.addSearchErrorListener(queryToolbarPresenter);
        queryModel.addSearchStateListener(queryToolbarPresenter);

        this.editorPresenter = editorPresenter;
        this.editorPresenter.setMode(AceEditorMode.STROOM_QUERY);

        // This glues the editor code completion to the QueryHelpPresenter's completion provider
        // Need to do this via addAttachHandler so the editor is fully loaded
        // else it moans about the id not being a thing on the AceEditor
        this.editorPresenter.getWidget().addAttachHandler(event -> {
            this.editorPresenter.registerCompletionProviders(queryHelpPresenter.getKeyedAceCompletionProvider());
        });

        view.setQueryHelp(queryHelpPresenter.getView());
        view.setQueryEditor(this.editorPresenter.getView());
        view.setTable(tablePresenter.getView());
    }

    @Override
    public List<Widget> getToolbars() {
        return Collections.singletonList(queryToolbarPresenter.getWidget());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(editorPresenter.addValueChangeHandler(event -> {
            queryHelpPresenter.updateQuery(editorPresenter.getText(), indexLoader::loadDataSource);
            setDirty(true);
        }));
        registerHandler(editorPresenter.addFormatHandler(event -> setDirty(true)));
        registerHandler(tablePresenter.addExpanderHandler(event -> queryModel.refresh()));
        registerHandler(tablePresenter.addRangeChangeHandler(event -> queryModel.refresh()));
        registerHandler(queryToolbarPresenter.addStartQueryHandler(e -> run(true, true)));
        registerHandler(queryToolbarPresenter.addTimeRangeChangeHandler(e -> run(true, true)));
        queryHelpPresenter.linkToEditor(editorPresenter);

        registerHandler(getEventBus().addHandler(WindowCloseEvent.getType(), event -> {
            // If a user is even attempting to close the browser or browser tab then destroy the query.
            queryModel.reset(DestroyReason.WINDOW_CLOSE);
        }));

        setupQueryHelpDataSupplier();
    }

    private void setupQueryHelpDataSupplier() {
        queryHelpPresenter.setQueryHelpDataSupplier(new QueryHelpDataSupplier() {

            @Override
            public DataSourceFieldsMap getDataSourceFieldsMap() {
                return indexLoader.getDataSourceFieldsMap();
            }

            @Override
            public String decorateFieldName(final String fieldName) {
                return GwtNullSafe.get(fieldName, str ->
                        str.contains(" ")
                                ? "\"" + str + "\""
                                : str);
            }

            @Override
            public void registerChangeHandler(final Consumer<DataSourceFieldsMap> onChange) {
                registerHandler(indexLoader.addChangeDataHandler(e -> {
                    onChange.accept(indexLoader.getDataSourceFieldsMap());
                }));
            }

            @Override
            public boolean isSupported(final HelpItemType helpItemType) {
                return helpItemType != null && SUPPORTED_HELP_TYPES.contains(helpItemType);
            }

            @Override
            public void fetchDataSources(final Consumer<List<DocRef>> dataSourceConsumer) {
                views.fetchViews(dataSourceConsumer);
            }
        });
    }

    private void setDirty(final boolean dirty) {
        if (!reading && this.dirty != dirty) {
            this.dirty = dirty;
            DirtyEvent.fire(this, dirty);
        }
    }

    public boolean isDirty() {
        return !readOnly && dirty;
    }

    public void onClose() {
        queryModel.reset(DestroyReason.TAB_CLOSE);
    }

    private void run(final boolean incremental,
                     final boolean storeHistory) {
        run(incremental, storeHistory, Function.identity());
    }

    private void run(final boolean incremental,
                     final boolean storeHistory,
                     final Function<ExpressionOperator, ExpressionOperator> expressionDecorator) {
//        final DocRef dataSourceRef = getQuerySettings().getDataSource();
//
//        if (dataSourceRef == null) {
//            warnNoDataSource();
//        } else {
//            currentWarnings = null;
//            expressionPresenter.clearSelection();
//
//            warningsButton.setVisible(false);
//
//            // Write expression.
//            final ExpressionOperator root = expressionPresenter.write();
//            final ExpressionOperator decorated = expressionDecorator.apply(root);

        // Start search.
        queryModel.reset(DestroyReason.NO_LONGER_NEEDED);
        queryModel.startNewSearch(
                editorPresenter.getText(),
                null, //getDashboardContext().getCombinedParams(),
                queryToolbarPresenter.getTimeRange(),
                incremental,
                storeHistory,
                null);
//        }
    }

    public void setQuery(final DocRef docRef, final String query, final boolean readOnly) {
        this.readOnly = readOnly;

        queryModel.init(docRef.getUuid(), "query");
        if (query != null) {
            reading = true;
            editorPresenter.setText(query);
            queryHelpPresenter.setQuery(query, indexLoader::loadDataSource);
            reading = false;
        }
        queryToolbarPresenter.setEnabled(true);
        queryToolbarPresenter.onSearching(false);

        editorPresenter.setReadOnly(readOnly);
        editorPresenter.getFormatAction().setAvailable(!readOnly);

        dirty = false;
    }

    public String getQuery() {
        return editorPresenter.getText();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public void setSourceType(final SourceType sourceType) {
        this.queryModel.setSourceType(sourceType);
    }


    // --------------------------------------------------------------------------------


    public interface QueryEditView extends View {

        void setQueryHelp(View view);

        void setQueryEditor(View view);

        void setTable(View view);
    }
}