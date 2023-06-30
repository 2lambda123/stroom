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

import stroom.dashboard.client.table.TablePresenter.TableView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class TableViewImpl extends ViewWithUiHandlers<TableUiHandlers>
        implements TableView {

    private final Widget widget;
    private final SpinnerSmall spinnerSmall;
    private final InlineSvgButton pause;

    @UiField
    FlowPanel layout;

    @Inject
    public TableViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        spinnerSmall = new SpinnerSmall();
        spinnerSmall.setStyleName("dashboardTable-smallSpinner");
        spinnerSmall.setTitle("Pause Update");

        pause = new InlineSvgButton();
        pause.addStyleName("dashboardTable-pause");
        pause.setSvg(SvgImage.PAUSE);
        pause.setTitle("Resume Update");

        layout.add(spinnerSmall);
        layout.add(pause);

        spinnerSmall.addDomHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onPause();
            }
        }, ClickEvent.getType());
        pause.addDomHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onPause();
            }
        }, ClickEvent.getType());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTableView(final View view) {
        layout.add(view.asWidget());
    }

    @Override
    public void setRefreshing(final boolean refreshing) {
        if (refreshing) {
            layout.addStyleName("refreshing");
        } else {
            layout.removeStyleName("refreshing");
        }
    }

    @Override
    public void setPaused(final boolean paused) {
        if (paused) {
            layout.addStyleName("paused");
        } else {
            layout.removeStyleName("paused");
        }
    }

    public interface Binder extends UiBinder<Widget, TableViewImpl> {

    }
}
