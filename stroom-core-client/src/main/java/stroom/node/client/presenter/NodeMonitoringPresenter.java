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

package stroom.node.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.client.ValueSpinnerCell;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.EntitySaveTask;
import stroom.entity.client.SaveQueue;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.ClusterNodeInfoAction;
import stroom.node.shared.FetchNodeInfoAction;
import stroom.node.shared.Node;
import stroom.node.shared.NodeInfoResult;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.presenter.Icon;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

public class NodeMonitoringPresenter extends ContentTabPresenter<DataGridView<NodeInfoResult>> implements Refreshable {
    private final ClientDispatchAsync dispatcher;
    private final TooltipPresenter tooltipPresenter;
    private final FetchNodeInfoAction action = new FetchNodeInfoAction();
    private final ActionDataProvider<NodeInfoResult> dataProvider;
    private final SaveQueue<Node> saveQueue;

    private final GlyphButtonView editButton;
    private final Provider<NodeEditPresenter> nodeEditPresenterProvider;

    @Inject
    public NodeMonitoringPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                                   final TooltipPresenter tooltipPresenter,
                                   final Provider<NodeEditPresenter> nodeEditPresenterProvider) {
        super(eventBus, new DataGridViewImpl<NodeInfoResult>(true));
        this.dispatcher = dispatcher;
        this.tooltipPresenter = tooltipPresenter;
        this.nodeEditPresenterProvider = nodeEditPresenterProvider;
        initTableColumns();
        dataProvider = new ActionDataProvider<NodeInfoResult>(dispatcher, action);
        dataProvider.addDataDisplay(getView().getDataDisplay());

        saveQueue = new SaveQueue<Node>(dispatcher);

        editButton = getView().addButton(GlyphIcons.EDIT);
        editButton.setTitle("Edit Node");
    }

    @Override
    protected void onBind() {
        registerHandler(getView().getSelectionModel().addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit(getView().getSelectionModel().getSelected());
            }
            enableButtons();
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                onEdit(getView().getSelectionModel().getSelected());
            }
        }));
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Info column.
        final InfoColumn<NodeInfoResult> infoColumn = new InfoColumn<NodeInfoResult>() {
            @Override
            protected void showInfo(final NodeInfoResult row, final int x, final int y) {
                dispatcher.exec(new ClusterNodeInfoAction(row.getEntity().getId()))
                        .onSuccess(result -> {
                            final StringBuilder html = new StringBuilder();
                            TooltipUtil.addHeading(html, "Node Details");

                            if (result != null) {
                                TooltipUtil.addRowData(html, "Node Name", result.getNodeName(), true);
                                TooltipUtil.addRowData(html, "Build Version", result.getBuildVersion(), true);
                                TooltipUtil.addRowData(html, "Build Date", result.getBuildDate(), true);
                                TooltipUtil.addRowData(html, "Up Date", result.getUpDate(), true);
                                TooltipUtil.addRowData(html, "Discover Time", result.getDiscoverTime(), true);
                                TooltipUtil.addRowData(html, "Cluster URL", result.getClusterURL(), true);
                            } else {
                                TooltipUtil.addRowData(html, "Node Name", row.getEntity().getName(), true);
                                TooltipUtil.addRowData(html, "Cluster URL", row.getEntity().getClusterURL(), true);
                            }

                            TooltipUtil.addRowData(html, "Ping", ModelStringUtil.formatDurationString(row.getPing()));
                            TooltipUtil.addRowData(html, "Error", row.getError());

                            if (result != null) {
                                TooltipUtil.addBreak(html);
                                TooltipUtil.addHeading(html, "Node List");
                                for (final ClusterNodeInfo.ClusterNodeInfoItem info : result.getItemList()) {
                                    html.append(info.getNode().getName());
                                    if (!info.isActive()) {
                                        html.append(" (Unknown)");
                                    }
                                    if (info.isMaster()) {
                                        html.append(" (Master)");
                                    }
                                    html.append("<br/>");
                                }
                            }

                            tooltipPresenter.setHTML(html.toString());
                            final PopupPosition popupPosition = new PopupPosition(x, y);
                            ShowPopupEvent.fire(NodeMonitoringPresenter.this, tooltipPresenter, PopupType.POPUP,
                                    popupPosition, null);
                        })
                        .onFailure(caught -> {
                                    tooltipPresenter.setHTML(caught.getMessage());
                                    final PopupPosition popupPosition = new PopupPosition(x, y);
                                    ShowPopupEvent.fire(NodeMonitoringPresenter.this, tooltipPresenter, PopupType.POPUP,
                                            popupPosition, null);
                                }
                        );
            }
        };
        getView().addColumn(infoColumn, "<br/>", 20);

        // Name.
        final Column<NodeInfoResult, String> nameColumn = new Column<NodeInfoResult, String>(new TextCell()) {
            @Override
            public String getValue(final NodeInfoResult row) {
                if (row == null) {
                    return null;
                }
                return row.getEntity().getName();
            }
        };
        getView().addResizableColumn(nameColumn, "Name", 100);

        // Host Name.
        final Column<NodeInfoResult, String> hostNameColumn = new Column<NodeInfoResult, String>(new TextCell()) {
            @Override
            public String getValue(final NodeInfoResult row) {
                if (row == null) {
                    return null;
                }
                return row.getEntity().getClusterURL();
            }
        };
        getView().addResizableColumn(hostNameColumn, "Cluster URL", 400);

        // Ping.
        final Column<NodeInfoResult, String> pingColumn = new Column<NodeInfoResult, String>(new TextCell()) {
            @Override
            public String getValue(final NodeInfoResult row) {
                if (row == null) {
                    return null;
                }
                if ("No response".equals(row.getError())) {
                    return row.getError();
                }
                if (row.getError() != null) {
                    return "Error";
                }

                return ModelStringUtil.formatDurationString(row.getPing());
            }
        };
        getView().addResizableColumn(pingColumn, "Ping", 150);

        // Master.
        final Column<NodeInfoResult, TickBoxState> masterColumn = new Column<NodeInfoResult, TickBoxState>(
                TickBoxCell.create(new TickBoxCell.NoBorderAppearance(), false, false, false)) {
            @Override
            public TickBoxState getValue(final NodeInfoResult row) {
                if (row == null) {
                    return null;
                }
                return TickBoxState.fromBoolean(row.isMaster());
            }
        };
        getView().addColumn(masterColumn, "Master", 50);

        // Priority.
        final Column<NodeInfoResult, Number> priorityColumn = new Column<NodeInfoResult, Number>(
                new ValueSpinnerCell(1, 100)) {
            @Override
            public Number getValue(final NodeInfoResult row) {
                if (row == null) {
                    return null;
                }
                return new EditableInteger(row.getEntity().getPriority());
            }
        };
        priorityColumn.setFieldUpdater((index, row, value) -> saveQueue.save(new EntitySaveTask<Node>(row) {
            @Override
            protected void setValue(final Node entity) {
                entity.setPriority(value.intValue());
            }
        }));
        getView().addColumn(priorityColumn, "Priority", 55);

        // Enabled
        final Column<NodeInfoResult, TickBoxState> enabledColumn = new Column<NodeInfoResult, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final NodeInfoResult row) {
                if (row == null) {
                    return null;
                }
                return TickBoxState.fromBoolean(row.getEntity().isEnabled());
            }
        };
        enabledColumn.setFieldUpdater((index, row, value) -> saveQueue.save(new EntitySaveTask<Node>(row) {
            @Override
            protected void setValue(final Node entity) {
                entity.setEnabled(value.toBoolean());
            }
        }));

        getView().addColumn(enabledColumn, "Enabled", 60);

        getView().addEndColumn(new EndColumn<NodeInfoResult>());
    }

    private void onEdit(final NodeInfoResult nodeInfo) {
        final Node node = nodeInfo.getEntity();
        final NodeEditPresenter editor = nodeEditPresenterProvider.get();
        editor.setName(node.getName());
        editor.setClusterUrl(node.getClusterURL());

        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    if (node.getClusterURL() == null || !node.getClusterURL().equals(editor.getClusterUrl())) {
                        node.setClusterURL(editor.getClusterUrl());
                        dispatcher.exec(new EntityServiceSaveAction<>(node)).onSuccess(result -> refresh());
                    }
                }

                HidePopupEvent.fire(NodeMonitoringPresenter.this, editor);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        };

        final PopupSize popupSize = new PopupSize(400, 103, 400, 103, 1000, 103, true);
        ShowPopupEvent.fire(this, editor, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Node", popupUiHandlers);
    }

    private void enableButtons() {
        final NodeInfoResult selected = getView().getSelectionModel().getSelected();
        editButton.setEnabled(selected != null);
    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    @Override
    public Icon getIcon() {
        return GlyphIcons.NODES;
    }

    @Override
    public String getLabel() {
        return "Nodes";
    }
}
