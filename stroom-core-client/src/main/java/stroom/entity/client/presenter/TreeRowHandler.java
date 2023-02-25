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

package stroom.entity.client.presenter;

import stroom.util.shared.Expander;
import stroom.util.shared.TreeAction;
import stroom.util.shared.TreeRow;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;

import java.util.List;

public class TreeRowHandler<R> {

    private final TreeAction<R> action;
    private final DataGrid<R> dataGrid;
    private final Column<R, Expander> expanderColumn;

    public TreeRowHandler(final TreeAction<R> action, final DataGrid<R> dataGrid,
                          final Column<R, Expander> expanderColumn) {
        this.action = action;
        this.dataGrid = dataGrid;
        this.expanderColumn = expanderColumn;
    }

    public void handle(final List<R> rows) {
        int maxDepth = -1;
        for (final R row : rows) {
            if (row instanceof TreeRow) {
                final TreeRow treeRow = (TreeRow) row;

                if (treeRow.getExpander() != null) {
                    if (maxDepth < treeRow.getExpander().getDepth()) {
                        maxDepth = treeRow.getExpander().getDepth();
                    }

                    if (treeRow.getExpander().isExpanded()) {
                        action.setRowExpanded(row, true);
                    }
                }
            }
        }

        // Set the width of the expander column so
        // that all expanders can be seen.
        if (maxDepth >= 0) {
            dataGrid.setColumnWidth(expanderColumn, 16 + (maxDepth * 10), Unit.PX);
        } else {
            dataGrid.setColumnWidth(expanderColumn, 0, Unit.PX);
        }
    }
}
