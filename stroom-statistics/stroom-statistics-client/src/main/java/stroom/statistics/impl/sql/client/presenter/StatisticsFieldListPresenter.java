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
 *
 */

package stroom.statistics.impl.sql.client.presenter;

import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;

public class StatisticsFieldListPresenter extends MyPresenterWidget<PagerView>
        implements HasDocumentRead<StatisticStoreDoc>, HasWrite<StatisticStoreDoc>, HasDirtyHandlers,
        ReadOnlyChangeHandler {

    private final MyDataGrid<StatisticField> dataGrid;
    private final MultiSelectionModelImpl<StatisticField> selectionModel;

    private final StatisticsFieldEditPresenter statisticsFieldEditPresenter;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private StatisticsDataSourceData statisticsDataSourceData;

    private StatisticsCustomMaskListPresenter customMaskListPresenter;

    private boolean readOnly = true;

    @Inject
    public StatisticsFieldListPresenter(final EventBus eventBus,
                                        final PagerView view,
                                        final StatisticsFieldEditPresenter statisticsFieldEditPresenter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        this.statisticsFieldEditPresenter = statisticsFieldEditPresenter;

        newButton = view.addButton(SvgPresets.NEW_ITEM);
        editButton = view.addButton(SvgPresets.EDIT);
        removeButton = view.addButton(SvgPresets.REMOVE);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(newButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onAdd();
            }
        }));

        registerHandler(editButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onEdit();
            }
        }));

        registerHandler(removeButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onRemove();
            }
        }));

        registerHandler(selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
    }

    private void enableButtons() {
        newButton.setEnabled(!readOnly);
        if (statisticsDataSourceData != null && statisticsDataSourceData.getFields() != null) {
            StatisticField selected = selectionModel.getSelected();
            final boolean enabled = !readOnly && selected != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
        }

        if (readOnly) {
            newButton.setTitle("New field disabled as fields are read only");
            editButton.setTitle("Edit field disabled as fields are read only");
            removeButton.setTitle("Remove field disabled as fields are read only");
        } else {
            newButton.setTitle("New Field");
            editButton.setTitle("Edit Field");
            removeButton.setTitle("Remove Field");
        }
    }

    private void addColumns() {
        addNameColumn();
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addNameColumn() {
        dataGrid.addResizableColumn(new Column<StatisticField, String>(new TextCell()) {
            @Override
            public String getValue(final StatisticField row) {
                return row.getFieldName();
            }
        }, "Name", 150);
    }

    private void onAdd() {
        if (!readOnly) {
            final StatisticField statisticField = new StatisticField();
            final StatisticsDataSourceData oldStatisticsDataSourceData = statisticsDataSourceData.deepCopy();
            final List<StatisticField> otherFields = statisticsDataSourceData.getFields();

            statisticsFieldEditPresenter.read(statisticField, otherFields);
            statisticsFieldEditPresenter.show("New Field", e -> {
                if (e.isOk()) {
                    if (statisticsFieldEditPresenter.write(statisticField)) {
                        statisticsDataSourceData.addStatisticField(statisticField);
                        reComputeRollUpBitMask(oldStatisticsDataSourceData, statisticsDataSourceData);
                        refresh();
                        e.hide();
                        DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
                    }
                } else {
                    e.hide();
                }
            });
        }
    }

    private void onEdit() {
        if (!readOnly) {
            final StatisticField statisticField = selectionModel.getSelected();
            if (statisticField != null) {
                final StatisticsDataSourceData oldStatisticsDataSourceData = statisticsDataSourceData.deepCopy();

                // make a copy of the list of stat fields and remove the one we are
                // editing so we can check the new value
                // is not already in the list
                final List<StatisticField> otherFields = new ArrayList<>(
                        statisticsDataSourceData.getFields());
                otherFields.remove(statisticField);

                statisticsFieldEditPresenter.read(statisticField, otherFields);
                statisticsFieldEditPresenter.show("Edit Field", e -> {
                    if (e.isOk()) {
                        if (statisticsFieldEditPresenter.write(statisticField)) {
                            statisticsDataSourceData.reOrderStatisticFields();
                            reComputeRollUpBitMask(oldStatisticsDataSourceData, statisticsDataSourceData);
                            refresh();
                            e.hide();
                            DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
                        }
                    } else {
                        e.hide();
                    }
                });
            }
        }
    }

    private void onRemove() {
        if (!readOnly) {
            final List<StatisticField> list = selectionModel.getSelectedItems();
            if (list != null && list.size() > 0) {
                final StatisticsDataSourceData oldStatisticsDataSourceData = statisticsDataSourceData.deepCopy();

                statisticsDataSourceData.getFields().removeAll(list);
                selectionModel.clear();
                reComputeRollUpBitMask(oldStatisticsDataSourceData, statisticsDataSourceData);
                refresh();
                DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
            }
        }
    }

    private void reComputeRollUpBitMask(final StatisticsDataSourceData oldStatisticsDataSourceData,
                                        final StatisticsDataSourceData newStatisticsDataSourceData) {
        if (customMaskListPresenter != null) {
            customMaskListPresenter.reComputeRollUpBitMask(oldStatisticsDataSourceData, newStatisticsDataSourceData);
        }
    }

    public void refresh() {
        if (statisticsDataSourceData == null) {
            statisticsDataSourceData = new StatisticsDataSourceData(new ArrayList<>());
        }

        dataGrid.setRowData(0, new ArrayList<>(statisticsDataSourceData.getFields()));
        dataGrid.setRowCount(statisticsDataSourceData.getFields().size(), true);
    }

    @Override
    public void read(final DocRef docRef, final StatisticStoreDoc statisticsDataSource) {
        if (statisticsDataSource != null) {
            statisticsDataSourceData = statisticsDataSource.getConfig();

            if (customMaskListPresenter != null) {
                customMaskListPresenter.read(docRef, statisticsDataSource);
            }
            refresh();
        }
    }

    @Override
    public void write(final StatisticStoreDoc entity) {
        entity.setConfig(statisticsDataSourceData);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public void setCustomMaskListPresenter(final StatisticsCustomMaskListPresenter customMaskListPresenter) {
        this.customMaskListPresenter = customMaskListPresenter;
    }
}
