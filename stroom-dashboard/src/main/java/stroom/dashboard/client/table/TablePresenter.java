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

package stroom.dashboard.client.table;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.app.client.LocationManager;
import stroom.cell.expander.client.ExpanderCell;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ResultComponent;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.client.query.QueryPresenter;
import stroom.dashboard.client.table.TablePresenter.TableView;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.DownloadSearchResultsAction;
import stroom.dashboard.shared.ParamUtil;
import stroom.dashboard.shared.Row;
import stroom.dashboard.shared.TableResult;
import stroom.dashboard.shared.TableResultRequest;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.dispatch.client.ExportFileCompleteUtil;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.query.shared.ComponentResultRequest;
import stroom.query.shared.ComponentSettings;
import stroom.query.shared.Field;
import stroom.query.shared.Format;
import stroom.query.shared.Format.Type;
import stroom.query.shared.IndexConstants;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFieldsMap;
import stroom.query.shared.QueryKey;
import stroom.query.shared.Search;
import stroom.query.shared.TableSettings;
import stroom.security.client.ClientSecurityContext;
import stroom.util.shared.Expander;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.SharedObject;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TablePresenter extends AbstractComponentPresenter<TableView>
        implements HasDirtyHandlers, ResultComponent {
    public static final ComponentType TYPE = new ComponentType(1, "table", "Table");
    private static final int MIN_EXPANDER_COL_WIDTH = 0;

    private final LocationManager locationManager;
    private final TableResultRequest tableResultRequest = new TableResultRequest(0, 100);
    private final List<Column<Row, ?>> existingColumns = new ArrayList<>();
    private final List<HandlerRegistration> searchModelHandlerRegistrations = new ArrayList<>();
    private final GlyphButtonView addFieldButton;
    private final GlyphButtonView downloadButton;
    private final Provider<FieldAddPresenter> fieldAddPresenterProvider;
    private final DownloadPresenter downloadPresenter;
    private final ClientDispatchAsync dispatcher;
    private final TimeZones timeZones;
    private final FieldsManager fieldsManager;
    private final DataGridView<Row> dataGrid;

    private int lastExpanderColumnWidth;
    private int currentExpanderColumnWidth;
    private SearchModel currentSearchModel;
    private FieldAddPresenter fieldAddPresenter;
    // TODO : Temporary action mechanism.
    private int streamIdIndex = -1;
    private int eventIdIndex = -1;
    private String selectedStreamId;
    private String selectedEventId;

    private TableSettings tableSettings;
    private boolean ignoreRangeChange;
    private int[] maxResults = TableSettings.DEFAULT_MAX_RESULTS;

    @Inject
    public TablePresenter(final EventBus eventBus,
                          final TableView view,
                          final ClientSecurityContext securityContext,
                          final LocationManager locationManager,
                          final MenuListPresenter menuListPresenter,
                          final ExpressionPresenter expressionPresenter,
                          final FormatPresenter formatPresenter,
                          final FilterPresenter filterPresenter,
                          final Provider<FieldAddPresenter> fieldAddPresenterProvider,
                          final Provider<TableSettingsPresenter> settingsPresenterProvider,
                          final DownloadPresenter downloadPresenter,
                          final ClientDispatchAsync dispatcher,
                          final ClientPropertyCache clientPropertyCache,
                          final TimeZones timeZones) {
        super(eventBus, view, settingsPresenterProvider);
        this.locationManager = locationManager;
        this.fieldAddPresenterProvider = fieldAddPresenterProvider;
        this.downloadPresenter = downloadPresenter;
        this.dispatcher = dispatcher;
        this.timeZones = timeZones;
        this.dataGrid = new DataGridViewImpl<>(true);

        view.setTableView(dataGrid);

        // Add the 'add field' button.
        addFieldButton = dataGrid.addButton(GlyphIcons.ADD);
        addFieldButton.setTitle("Add Field");

        // Download
        downloadButton = dataGrid.addButton(GlyphIcons.DOWNLOAD);
        downloadButton.setVisible(securityContext.hasAppPermission(Dashboard.DOWNLOAD_SEARCH_RESULTS_PERMISSION));

        fieldsManager = new FieldsManager(this, menuListPresenter, expressionPresenter, formatPresenter,
                filterPresenter);
        dataGrid.setHeadingListener(fieldsManager);

        clientPropertyCache.get()
                .onSuccess(result -> {
                    final String value = result.get(ClientProperties.MAX_RESULTS);
                    if (value != null) {
                        final String[] parts = value.split(",");
                        final int[] arr = new int[parts.length];
                        for (int i = 0; i < arr.length; i++) {
                            arr[i] = Integer.valueOf(parts[i].trim());
                        }
                        maxResults = arr;
                    }
                })
                .onFailure(caught -> AlertEvent.fireError(TablePresenter.this, caught.getMessage(), null));
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(dataGrid.getSelectionModel().addSelectionHandler(event -> performRowAction(dataGrid.getSelectionModel().getSelected())));
        registerHandler(dataGrid.addRangeChangeHandler(event -> {
            if (!ignoreRangeChange) {
                final com.google.gwt.view.client.Range range = event.getNewRange();
                tableResultRequest.setRange(range.getStart(), range.getLength());
                refresh();
            }
        }));
        registerHandler(addFieldButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onAddField(event);
            }
        }));

        registerHandler(downloadButton.addClickHandler(event -> {
            if (currentSearchModel != null) {
                if (currentSearchModel.isSearching()) {
                    ConfirmEvent.fire(TablePresenter.this,
                            "Search still in progress. Do you want to download the current results? Note that these may be incomplete.",
                            ok -> {
                                if (ok) {
                                    download();
                                }
                            });
                } else {
                    download();
                }
            }
        }));
    }

    @Override
    protected void onUnbind() {
        super.onUnbind();
        cleanupSearchModelAssociation();
    }

    private void onAddField(final ClickEvent event) {
        if (currentSearchModel != null && fieldAddPresenter == null) {
            fieldAddPresenter = fieldAddPresenterProvider.get();
            final AddSelectionHandler selectionHandler = new AddSelectionHandler(fieldAddPresenter);
            final HandlerRegistration handlerRegistration = fieldAddPresenter
                    .addSelectionChangeHandler(selectionHandler);

            final List<Field> addFields = new ArrayList<>();
            if (currentSearchModel.getIndexLoader().getIndexFieldNames() != null) {
                for (final String indexFieldName : currentSearchModel.getIndexLoader().getIndexFieldNames()) {
                    final Field field = new Field(indexFieldName);
                    field.setExpression(ParamUtil.makeParam(indexFieldName));
                    final IndexFieldsMap indexFieldsMap = getIndexFieldsMap();
                    if (indexFieldsMap != null) {
                        final IndexField indexField = indexFieldsMap.get(indexFieldName);
                        if (indexField != null) {
                            switch (indexField.getFieldType()) {
                                case DATE_FIELD:
                                    field.setFormat(new Format(Type.DATE_TIME));
                                    break;
                                case NUMERIC_FIELD:
                                    field.setFormat(new Format(Type.NUMBER));
                                    break;
                                case ID:
                                    field.setFormat(new Format(Type.NUMBER));
                                    break;
                                default:
                                    field.setFormat(new Format(Type.GENERAL));
                                    break;
                            }
                        }

                        addFields.add(field);
                    }
                }
            }

            final Field count = new Field("Count");
            count.setFormat(new Format(Type.NUMBER));
            count.setExpression("count()");
            addFields.add(count);

            final Field countGroups = new Field("Count Groups");
            countGroups.setFormat(new Format(Type.NUMBER));
            countGroups.setExpression("countGroups()");
            addFields.add(countGroups);

            final Field custom = new Field("Custom");
            addFields.add(custom);

            fieldAddPresenter.setFields(addFields);
            fieldAddPresenter.clearSelection();

            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    HidePopupEvent.fire(TablePresenter.this, fieldAddPresenter);
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    handlerRegistration.removeHandler();
                    fieldAddPresenter = null;
                }
            };

            final com.google.gwt.dom.client.Element target = event.getNativeEvent().getEventTarget().cast();

            final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft() - 3,
                    target.getAbsoluteTop() + target.getClientHeight() + 1);
            ShowPopupEvent.fire(this, fieldAddPresenter, PopupType.POPUP, popupPosition, popupUiHandlers, target);
        }
    }

    private void download() {
        if (currentSearchModel != null) {
            final Search search = currentSearchModel.getActiveSearch();
            final QueryKey queryKey = currentSearchModel.getCurrentQueryKey();
            if (search != null && queryKey != null) {
                final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                    @Override
                    public void onHideRequest(final boolean autoClose, final boolean ok) {
                        if (ok) {
                            dispatcher.exec(
                                    new DownloadSearchResultsAction(queryKey, search, getComponentData().getId(),
                                            downloadPresenter.getFileType(), downloadPresenter.isSample(),
                                            downloadPresenter.getPercent(), timeZones.getTimeZone()))
                                    .onSuccess(result -> ExportFileCompleteUtil.onSuccess(locationManager, null, result));
                        }

                        HidePopupEvent.fire(TablePresenter.this, downloadPresenter);
                    }

                    @Override
                    public void onHide(final boolean autoClose, final boolean ok) {
                    }
                };

                final PopupSize popupSize = new PopupSize(316, 124, false);
                ShowPopupEvent.fire(this, downloadPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Download Options",
                        popupUiHandlers);
            }
        }
    }

    @Override
    public void startSearch() {
    }

    @Override
    public void endSearch() {
    }

    @Override
    public void setWantsData(final boolean wantsData) {
        getView().setRefreshing(wantsData);
        tableResultRequest.setWantsData(wantsData);
    }

    @Override
    public void setData(final SharedObject result) {
        ignoreRangeChange = true;

//        if (!paused) {
        lastExpanderColumnWidth = MIN_EXPANDER_COL_WIDTH;
        currentExpanderColumnWidth = MIN_EXPANDER_COL_WIDTH;

        if (result != null && result instanceof TableResult) {
            // Don't refresh the table unless the results have changed.
            final TableResult tableResult = (TableResult) result;
            final List<Row> values = tableResult.getRows();
            final OffsetRange<Integer> valuesRange = tableResult.getResultRange();

            // Only set data in the table if we have got some results and
            // they have changed.
            if (valuesRange.getOffset() == 0 || values.size() > 0) {
                updateColumns();
                dataGrid.setRowData(valuesRange.getOffset(), values);
                dataGrid.setRowCount(tableResult.getTotalResults(), true);
            }

            // Enable download of current results.
            downloadButton.setEnabled(true);
        } else {
            // Disable download of current results.
            downloadButton.setEnabled(false);

            dataGrid.setRowData(0, new ArrayList<>());
            dataGrid.setRowCount(0, true);

            dataGrid.getSelectionModel().clear();
        }
//        }

        ignoreRangeChange = false;
    }

    private void addExpanderColumn(final int maxDepth) {
        // Expander column.
        final Column<Row, Expander> column = new Column<Row, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final Row row) {
                if (row == null) {
                    return null;
                }

                if (row.getDepth() < maxDepth) {
                    // Set the width of the expander column if it needs to be
                    // made wider.
                    final int width = 16 + (row.getDepth() * 10);
                    if (width > currentExpanderColumnWidth) {
                        currentExpanderColumnWidth = width;
                        lastExpanderColumnWidth = width;
                        dataGrid.setColumnWidth(this, width, Unit.PX);
                    }

                    final boolean open = tableResultRequest.isGroupOpen(row.getGroupKey());
                    return new Expander(row.getDepth(), open, false);
                } else if (row.getDepth() > 0) {
                    return new Expander(row.getDepth(), false, true);
                }

                return null;
            }
        };
        column.setFieldUpdater((index, result, value) -> {
            tableResultRequest.setGroupOpen(result.getGroupKey(), !value.isExpanded());
            refresh();
        });
        dataGrid.addColumn(column, "<br/>", lastExpanderColumnWidth);
        existingColumns.add(column);
    }

    private void addColumn(final Field field, final int pos) {
        final Column<Row, SafeHtml> column = new Column<Row, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final Row row) {
                if (row == null) {
                    return null;
                }

                final SharedObject[] values = row.getValues();
                if (values != null) {
                    final SharedObject res = values[pos];
                    if (res != null) {
                        final String value = res.toString();

                        if (field.getGroup() != null && field.getGroup() >= row.getDepth()) {
                            final SafeHtmlBuilder sb = new SafeHtmlBuilder();
                            sb.appendHtmlConstant("<b>");
                            sb.appendEscaped(value);
                            sb.appendHtmlConstant("</b>");

                            return sb.toSafeHtml();
                        }
                        return SafeHtmlUtils.fromString(value);
                    }
                }
                return null;
            }

            @Override
            public String getCellStyleNames(Cell.Context context, Row object) {
                if (field.getFormat() != null && field.getFormat().getWrap() != null && field.getFormat().getWrap()) {
                    return super.getCellStyleNames(context, object) + " " + dataGrid.getResources().dataGridStyle().dataGridCellWrapText();
                }

                return super.getCellStyleNames(context, object);

            }
        };

        final FieldHeader fieldHeader = new FieldHeader(fieldsManager, field);
        fieldHeader.setUpdater(value -> dataGrid.redrawHeaders());

        dataGrid.addResizableColumn(column, fieldHeader, field.getWidth());
        existingColumns.add(column);
    }

    private void performRowAction(final Row result) {
        selectedStreamId = null;
        selectedEventId = null;
        if (result != null && streamIdIndex >= 0 && eventIdIndex >= 0) {
            final SharedObject[] values = result.getValues();
            if (values.length > streamIdIndex && values[streamIdIndex] != null) {
                selectedStreamId = values[streamIdIndex].toString();
            }
            if (values.length > eventIdIndex && values[eventIdIndex] != null) {
                selectedEventId = values[eventIdIndex].toString();
            }
        }

        getComponents().fireComponentChangeEvent(this);
    }

    private void setQueryId(final String queryId) {
        cleanupSearchModelAssociation();

        if (queryId != null) {
            final Component component = getComponents().get(queryId);
            if (component != null && component instanceof QueryPresenter) {
                final QueryPresenter queryPresenter = (QueryPresenter) component;
                currentSearchModel = queryPresenter.getSearchModel();
                if (currentSearchModel != null) {
                    currentSearchModel.addComponent(getComponentData().getId(), this);
                }
            }
        }

        if (currentSearchModel != null) {
            searchModelHandlerRegistrations
                    .add(currentSearchModel.getIndexLoader().addChangeDataHandler(event -> updateFields()));
        }

        updateFields();
        getComponents().fireComponentChangeEvent(this);
    }

    private void cleanupSearchModelAssociation() {
        if (currentSearchModel != null) {
            // Remove this component from the list of components the search
            // model expects to update.
            currentSearchModel.removeComponent(getComponentData().getId());

            // Clear any existing handler registrations on the search model.
            for (final HandlerRegistration handlerRegistration : searchModelHandlerRegistrations) {
                handlerRegistration.removeHandler();
            }
            searchModelHandlerRegistrations.clear();

            currentSearchModel = null;
        }
    }

    private void updateFields() {
        if (tableSettings.getFields() == null) {
            tableSettings.setFields(new ArrayList<>());
        }

        // Update columns.
        updateColumns();
    }

    private void removeHiddenFields() {
        tableSettings.getFields().removeIf(field -> !field.isVisible());
    }

    private int ensureHiddenField(final String indexFieldName) {
        // Now add new hidden field.
        final IndexFieldsMap indexFieldsMap = getIndexFieldsMap();
        if (indexFieldsMap != null) {
            final IndexField indexField = indexFieldsMap.get(indexFieldName);
            if (indexField != null) {
                final Field field = new Field(indexFieldName);
                field.setExpression(ParamUtil.makeParam(indexFieldName));
                field.setVisible(false);
                tableSettings.addField(field);
            }
        }

        return tableSettings.getFields().size() - 1;
    }

    private IndexFieldsMap getIndexFieldsMap() {
        if (currentSearchModel != null && currentSearchModel.getIndexLoader() != null
                && currentSearchModel.getIndexLoader().getIndexFieldsMap() != null) {
            return currentSearchModel.getIndexLoader().getIndexFieldsMap();
        }

        return null;
    }

    void updateColumns() {
        final List<Field> fields = tableSettings.getFields();

        // First remove existing hidden fields.
        removeHiddenFields();

        // Now make sure hidden fields exist for stream id and event id and get
        // their result index.
        streamIdIndex = ensureHiddenField(IndexConstants.STREAM_ID);
        eventIdIndex = ensureHiddenField(IndexConstants.EVENT_ID);

        // Remove existing columns.
        for (final Column<Row, ?> column : existingColumns) {
            dataGrid.removeColumn(column);
        }
        existingColumns.clear();

        // See if any fields have more than 1 level. If they do then we will add
        // an expander column.
        int maxGroup = -1;
        final boolean showDetail = tableSettings.showDetail();
        for (final Field field : fields) {
            if (field.getGroup() != null) {
                final int group = field.getGroup();
                if (group > maxGroup) {
                    maxGroup = group;
                }
            }
        }

        int maxDepth = maxGroup;
        if (showDetail) {
            maxDepth++;
        }

        if (maxDepth > 0) {
            addExpanderColumn(maxDepth);
            fieldsManager.setFieldsStartIndex(1);
        } else {
            fieldsManager.setFieldsStartIndex(0);
        }

        // Add fields as columns.
        int i = 0;
        for (final Field field : fields) {
            final int pos = i++;

            // Only include the field if it is supposed to be visible.
            if (field.isVisible()) {
                addColumn(field, pos);
            }
        }

        dataGrid.resizeTableToFitColumns();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    @Override
    public ComponentType getType() {
        return TYPE;
    }

    @Override
    public void read(final ComponentConfig componentData) {
        super.read(componentData);

        tableSettings = getSettings();
        tableResultRequest.setTableSettings(tableSettings);
        fieldsManager.setTableSettings(tableSettings);
    }

    @Override
    public void write(final ComponentConfig componentData) {
        super.write(componentData);
        componentData.setSettings(tableSettings);
    }

    @Override
    public void link() {
        String queryId = tableSettings.getQueryId();
        queryId = getComponents().validateOrGetFirstComponentId(queryId, QueryPresenter.TYPE.getId());
        tableSettings.setQueryId(queryId);
        setQueryId(queryId);
    }

    @Override
    protected void changeSettings() {
        super.changeSettings();
        setQueryId(tableSettings.getQueryId());
    }

    @Override
    public ComponentResultRequest getResultRequest() {
        return tableResultRequest;
    }

    @Override
    public void reset() {
        tableResultRequest.setRange(0, 100);
    }

    void clearAndRefresh() {
        clear();
    }

    private void refresh() {
        currentSearchModel.refresh(getComponentData().getId());
    }

    private void clear() {
        setData(null);
    }

    public String getSelectedStreamId() {
        return selectedStreamId;
    }

    public String getSelectedEventId() {
        return selectedEventId;
    }

    @Override
    public TableSettings getSettings() {
        ComponentSettings settings = getComponentData().getSettings();
        if (settings == null || !(settings instanceof TableSettings)) {
            settings = createSettings();
            getComponentData().setSettings(settings);
        }

        return (TableSettings) settings;
    }

    private ComponentSettings createSettings() {
        final TableSettings tableSettings = new TableSettings();

        int[] arr = null;
        if (maxResults != null && maxResults.length > 0) {
            arr = new int[1];
            arr[0] = maxResults[0];
        }

        tableSettings.setMaxResults(arr);
        return tableSettings;
    }

    public Set<String> getHighlights() {
        if (currentSearchModel != null && currentSearchModel.getCurrentResult() != null
                && currentSearchModel.getCurrentResult().getHighlights() != null) {
            return currentSearchModel.getCurrentResult().getHighlights();
        }

        return null;
    }

    private class AddSelectionHandler implements SelectionChangeEvent.Handler {
        private final FieldAddPresenter presenter;

        AddSelectionHandler(final FieldAddPresenter presenter) {
            this.presenter = presenter;
        }

        @Override
        public void onSelectionChange(final SelectionChangeEvent event) {
            final Field field = presenter.getSelectedObject();
            if (field != null) {
                HidePopupEvent.fire(TablePresenter.this, presenter);
                fieldsManager.addField(field);
            }
        }
    }

    public interface TableView extends View {
        void setTableView(View view);

        void setRefreshing(boolean refreshing);
    }
}
