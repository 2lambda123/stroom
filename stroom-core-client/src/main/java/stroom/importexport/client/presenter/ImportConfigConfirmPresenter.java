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

package stroom.importexport.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.importexport.client.event.ImportConfigConfirmEvent;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportState;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.Message;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.Severity;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.tooltip.client.presenter.TooltipUtil.Builder;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.ArrayList;
import java.util.List;

public class ImportConfigConfirmPresenter extends
        MyPresenter<ImportConfigConfirmPresenter.ImportConfigConfirmView,
                ImportConfigConfirmPresenter.ImportConfirmProxy>
        implements ImportConfigConfirmEvent.Handler,
        HidePopupRequestEvent.Handler {

    private static final ContentResource CONTENT_RESOURCE =
            com.google.gwt.core.client.GWT.create(ContentResource.class);

    private final TooltipPresenter tooltipPresenter;
    private final ImportConfigConfirmView view;
    private final MyDataGrid<ImportState> dataGrid;
    private final RestFactory restFactory;
    private ResourceKey resourceKey;
    private List<ImportState> confirmList;

    @Inject
    public ImportConfigConfirmPresenter(final EventBus eventBus,
                                        final ImportConfigConfirmView view,
                                        final ImportConfirmProxy proxy,
                                        final TooltipPresenter tooltipPresenter,
                                        final RestFactory restFactory) {
        super(eventBus, view, proxy);

        this.tooltipPresenter = tooltipPresenter;
        this.restFactory = restFactory;

        this.view = view;

        dataGrid = new MyDataGrid<>(MyDataGrid.MASSIVE_LIST_PAGE_SIZE);

        view.setDataGrid(dataGrid);
        view.setEnableFilters(true);

        addColumns();
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        // Disable the popup ok/cancel buttons before we attempt import.
        DisablePopupEvent.builder(this).fire();

        if (e.isOk()) {
            boolean warnings = false;
            int count = 0;
            for (final ImportState importState : confirmList) {
                importState.setEnableTime(getView().getEnableFromDate());
                importState.setEnable(getView().isEnableFilters());
                if (importState.isAction()) {
                    count++;
                    if (importState.getSeverity().greaterThan(Severity.INFO)) {
                        warnings = true;
                    }
                }
            }

            if (count == 0) {
                AlertEvent.fireWarn(
                        ImportConfigConfirmPresenter.this,
                        "No items are selected for import", () -> {
                            // Re-enable popup buttons.
                            EnablePopupEvent.builder(this).fire();
                        });
            } else if (warnings) {
                ConfirmEvent.fireWarn(ImportConfigConfirmPresenter.this,
                        "There are warnings in the items selected.  Are you sure you want to import?.",
                        result -> {
                            if (result) {
                                importData();
                            } else {
                                // Re-enable popup buttons.
                                EnablePopupEvent.builder(this).fire();
                            }
                        });

            } else {
                importData();
            }
        } else {
            abortImport();
        }
    }

    @ProxyEvent
    @Override
    public void onConfirmImport(final ImportConfigConfirmEvent event) {
        resourceKey = event.getResourceKey();
        confirmList = event.getConfirmList();

        if (confirmList == null) {
            dataGrid.setRowCount(0);
        } else {
            dataGrid.setRowData(0, confirmList);
            dataGrid.setRowCount(confirmList.size());
        }
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        final PopupSize popupSize = PopupSize.resizable(800, 400);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Confirm Import")
                .onShow(e -> dataGrid.setFocus(true))
                .onHideRequest(this)
                .fire();
    }

    private void addColumns() {
        addSelectedColumn();
        addInfoColumn();
        addActionColumn();
        addTypeColumn();
        addSourcePathColumn();
        addDestPathColumn();
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addSelectedColumn() {

        // Select Column
        final Column<ImportState, TickBoxState> column = new Column<ImportState, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final ImportState object) {
                final Severity severity = object.getSeverity();
                if (severity != null && severity.greaterThanOrEqual(Severity.ERROR)) {
                    return null;
                }

                return TickBoxState.fromBoolean(object.isAction());
            }
        };
        final Header<TickBoxState> header = new Header<TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue() {
                return getHeaderState();
            }
        };
        dataGrid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

        // Add Handlers
        column.setFieldUpdater((index, row, value) -> row.setAction(value.toBoolean()));
        header.setUpdater(value -> {
            if (confirmList != null) {
                if (value.equals(TickBoxState.UNTICK)) {
                    for (final ImportState item : confirmList) {
                        item.setAction(false);
                    }
                }
                if (value.equals(TickBoxState.TICK)) {
                    for (final ImportState item : confirmList) {
                        item.setAction(true);
                    }
                }
                // Refresh list
                dataGrid.setRowData(0, confirmList);
                dataGrid.setRowCount(confirmList.size());
            }
        });
    }

    private TickBoxState getHeaderState() {
        TickBoxState state = TickBoxState.UNTICK;

        if (confirmList != null && confirmList.size() > 0) {
            boolean allAction = true;
            boolean allNotAction = true;

            for (final ImportState item : confirmList) {
                if (item.isAction()) {
                    allNotAction = false;
                } else {
                    allAction = false;
                }
            }

            if (allAction) {
                state = TickBoxState.TICK;
            } else if (allNotAction) {
                state = TickBoxState.UNTICK;
            } else {
                state = TickBoxState.HALF_TICK;
            }
        }
        return state;

    }

    protected void addInfoColumn() {
        // Info column.
        final InfoColumn<ImportState> infoColumn = new InfoColumn<ImportState>() {
            @Override
            public Preset getValue(final ImportState object) {
                if (object.getMessageList().size() > 0 || object.getUpdatedFieldList().size() > 0) {
                    final Severity severity = object.getSeverity();
                    switch (severity) {
                        case INFO:
                            return SvgPresets.INFO;
                        case WARNING:
                            return SvgPresets.ALERT;
                        case ERROR:
                            return SvgPresets.ERROR;
                        default:
                            return SvgPresets.ERROR;
                    }
                }
                return null;
            }

            @Override
            protected void showInfo(final ImportState action, final int x, final int y) {

                final Builder builder = TooltipUtil.builder();
                if (action.getMessageList().size() > 0) {
                    builder
                            .addHeading("Messages:")
                            .addTwoColTable(tableBuilder -> {
                                for (final Message msg : action.getMessageList()) {
                                    tableBuilder.addRow(
                                            msg.getSeverity().getDisplayValue(),
                                            msg.getMessage());
                                }
                                return tableBuilder.build();
                            });
                }

                if (action.getUpdatedFieldList().size() > 0) {
                    if (action.getMessageList().size() > 0) {
                        builder.addBreak();
                    }

                    builder.addHeading("Fields Updated:");
                    action.getUpdatedFieldList().forEach(builder::addLine);
                }
                tooltipPresenter.show(builder.build(), x, y);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", 18);
    }

    private void addActionColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                if (action.getState() != null) {
                    return action.getState().getDisplayValue();
                }
                return "Error";
            }
        };
        dataGrid.addResizableColumn(column, "Action", 50);
    }

    private void addTypeColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                return action.getDocRef().getType();
            }
        };
        dataGrid.addResizableColumn(column, "Type", 100);
    }

    private void addSourcePathColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                return action.getSourcePath();
            }
        };
        dataGrid.addResizableColumn(column, "Source Path", 300);
    }

    private void addDestPathColumn() {
        final Column<ImportState, String> column = new Column<ImportState, String>(
                new TextCell()) {
            @Override
            public String getValue(final ImportState action) {
                return action.getDestPath();
            }
        };
        dataGrid.addResizableColumn(column, "Destination Path", 300);
    }

    public void abortImport() {
        // Abort ... set the confirm list to blank
        final Rest<ResourceKey> rest = restFactory.create();
        rest
                .onSuccess(result2 -> AlertEvent.fireWarn(ImportConfigConfirmPresenter.this,
                        "Import Aborted",
                        () -> HidePopupEvent.builder(ImportConfigConfirmPresenter.this).ok(false).fire()))
                .onFailure(caught -> AlertEvent.fireError(ImportConfigConfirmPresenter.this,
                        caught.getMessage(),
                        () -> HidePopupEvent.builder(ImportConfigConfirmPresenter.this).ok(false).fire()))
                .call(CONTENT_RESOURCE)
                .importContent(new ImportConfigRequest(resourceKey, new ArrayList<>()));
    }

    public void importData() {
        final Rest<ResourceKey> rest = restFactory.create();
        rest
                .onSuccess(result2 ->
                        AlertEvent.fireInfo(
                                ImportConfigConfirmPresenter.this,
                                "Import Complete", () -> {
                                    HidePopupEvent.builder(ImportConfigConfirmPresenter.this).fire();
                                    RefreshExplorerTreeEvent.fire(ImportConfigConfirmPresenter.this);

                                    // We might have loaded a new visualisation or updated
                                    // an existing one.
                                    clearCaches();
                                }))
                .onFailure(caught -> {
                    HidePopupEvent.builder(ImportConfigConfirmPresenter.this).fire();
                    // Even if the import was error we should refresh the tree in
                    // case it got part done.
                    RefreshExplorerTreeEvent.fire(ImportConfigConfirmPresenter.this);

                    // We might have loaded a new visualisation or updated an
                    // existing one.
                    clearCaches();
                })
                .call(CONTENT_RESOURCE)
                .importContent(new ImportConfigRequest(resourceKey, confirmList));
    }

    private void clearCaches() {
        // TODO : Add cache clearing functionality.

        // ClearScriptCacheEvent.fire(this);
        // ClearFunctionCacheEvent.fire(this);
    }

    public interface ImportConfigConfirmView extends View {

        void setDataGrid(Widget widget);

        Long getEnableFromDate();

        boolean isEnableFilters();

        void setEnableFilters(boolean enableFilters);

        Widget getDataGridViewWidget();
    }

    @ProxyCodeSplit
    public interface ImportConfirmProxy extends Proxy<ImportConfigConfirmPresenter> {

    }
}
