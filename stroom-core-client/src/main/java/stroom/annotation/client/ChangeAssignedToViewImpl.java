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

package stroom.annotation.client;

import stroom.annotation.client.ChangeAssignedToPresenter.ChangeAssignedToView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ChangeAssignedToViewImpl extends ViewWithUiHandlers<ChangeAssignedToUiHandlers>
        implements ChangeAssignedToView {

    private final Widget widget;

    @UiField
    Label assignedToLabel;
    @UiField
    InlineSvgButton assignedToIcon;
    @UiField
    Label assignedTo;
    @UiField
    Label assignYourself;

    @Inject
    public ChangeAssignedToViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        assignedToIcon.setSvg(SvgImage.ARROW_DOWN);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        assignedToIcon.setFocus(true);
    }

    @Override
    public void setAssignedTo(final String assignedTo) {
        if (assignedTo == null || assignedTo.trim().isEmpty()) {
            this.assignedTo.setText("Nobody");
            this.assignedTo.getElement().getStyle().setOpacity(0.5);
        } else {
            this.assignedTo.setText(assignedTo);
            this.assignedTo.getElement().getStyle().setOpacity(1);
        }
    }

    @UiHandler("assignedToLabel")
    public void onAssignedToLabel(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showAssignedToChooser(assignedToLabel.getElement());
        }
    }

    @UiHandler("assignedTo")
    public void onAssignedTo(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showAssignedToChooser(assignedToLabel.getElement());
        }
    }

    @UiHandler("assignedToIcon")
    public void onAssignedToIcon(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().showAssignedToChooser(assignedToLabel.getElement());
        }
    }

    @UiHandler("assignYourself")
    public void onAssignYourself(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().assignYourself();
        }
    }

    public interface Binder extends UiBinder<Widget, ChangeAssignedToViewImpl> {

    }
}
