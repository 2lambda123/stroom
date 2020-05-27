/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.receive.rules.client.presenter;

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.svg.client.SvgPreset;
import stroom.util.client.BorderUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.function.Function;

public class DataRetentionPolicyListPresenter extends MyPresenterWidget<DataGridView<DataRetentionRule>> {
    @Inject
    public DataRetentionPolicyListPresenter(final EventBus eventBus) {
        super(eventBus, new DataGridViewImpl<>(true, false));

        // Add a border to the list.
        BorderUtil.addBorder(getWidget().getElement());

        initTableColumns();
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {

        addColumn("Rule", 40, row -> String.valueOf(row.getRuleNumber()));
        addColumn("Name", 200, DataRetentionRule::getName);
        addColumn("Retention", ColumnSizeConstants.SMALL_COL, DataRetentionRule::getAgeString);
        addColumn("Expression", 500, row -> row.getExpression().toString());
        getView().addEndColumn(new EndColumn<>());
    }

    private void addColumn(final String name, final int width, final Function<DataRetentionRule, String> valueFunc) {
        final Column<DataRetentionRule, SafeHtml> expressionColumn = new Column<DataRetentionRule, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final DataRetentionRule row) {
                return getSafeHtml(valueFunc.apply(row), row.isEnabled());
            }
        };
        getView().addResizableColumn(expressionColumn, name, width);
    }

    private SafeHtml getSafeHtml(final String string, final boolean enabled) {
        if (enabled) {
            return SafeHtmlUtils.fromString(string);
        }

        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<span style=\"color:grey\">");
        builder.appendEscaped(string);
        builder.appendHtmlConstant("</span>");
        return builder.toSafeHtml();
    }

    public void setData(final List<DataRetentionRule> data) {
        getView().setRowData(0, data);
        getView().setRowCount(data.size());
    }

    public MultiSelectionModel<DataRetentionRule> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public ButtonView add(final SvgPreset preset) {
        return getView().addButton(preset);
    }
}
