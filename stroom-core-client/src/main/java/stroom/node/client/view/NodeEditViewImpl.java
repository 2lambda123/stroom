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

package stroom.node.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.node.client.presenter.NodeEditPresenter.NodeEditView;

public class NodeEditViewImpl extends ViewImpl implements NodeEditView {
    public interface Binder extends UiBinder<Widget, NodeEditViewImpl> {
    }

    private final Widget widget;

    @UiField
    Label name;
    @UiField
    TextBox clusterUrl;

    @Inject
    public NodeEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public void setClusterUrl(final String clusterUrl) {
        this.clusterUrl.setText(clusterUrl);
    }

    @Override
    public String getClusterUrl() {
        return clusterUrl.getText();
    }
}