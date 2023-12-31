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

package stroom.login.client.view;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import stroom.login.client.presenter.LoginPresenter;
import stroom.login.client.presenter.LoginUiHandlers;
import stroom.util.shared.EqualsUtil;

public class LoginViewImpl extends ViewWithUiHandlers<LoginUiHandlers>implements LoginPresenter.LoginView {
    public interface Binder extends UiBinder<Widget, LoginViewImpl> {
    }

    private final Widget widget;

    @UiField
    SimplePanel banner;
    @UiField
    FlowPanel main;
    @UiField
    SimplePanel html;
    @UiField
    Label buildVersion;
    @UiField
    Label buildDate;
    @UiField
    Label upDate;
    @UiField
    Label nodeName;
    @UiField
    TextBox userName;
    @UiField
    PasswordTextBox password;
    @UiField
    Button login;
    @UiField
    Label error;
    @UiField
    Anchor reset;

    private String currentBanner;

    @Inject
    public LoginViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        banner.setVisible(false);
        widget.sinkEvents(Event.KEYEVENTS);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setHTML(final String html) {
        this.html.getElement().setInnerHTML(html);
    }

    @Override
    public void setBuildVersion(final String buildVersion) {
        this.buildVersion.setText(buildVersion);
    }

    @Override
    public void setBuildDate(final String buildDate) {
        this.buildDate.setText(buildDate);
    }

    @Override
    public void setUpDate(final String upDate) {
        this.upDate.setText(upDate);
    }

    @Override
    public void setNodeName(final String nodeName) {
        this.nodeName.setText(nodeName);
    }

    @Override
    public String getUserName() {
        return userName.getText();
    }

    @Override
    public String getPassword() {
        return password.getText();
    }

    @Override
    public void setPassword(final String password) {
        this.password.setText(password);
    }

    @Override
    public void setError(final String error) {
        this.error.setText(error);
    }

    @Override
    public void setBanner(final String text) {
        if (!EqualsUtil.isEquals(currentBanner, text)) {
            currentBanner = text;
            if (text == null || text.trim().length() == 0) {
                main.getElement().getStyle().setTop(0, Unit.PX);
                Document.get().getElementById("logo").getStyle().setTop(0, Unit.PX);
                banner.setVisible(false);
                banner.getElement().setInnerText("");
            } else {
                main.getElement().getStyle().setTop(20, Unit.PX);
                Document.get().getElementById("logo").getStyle().setTop(20, Unit.PX);
                banner.setVisible(true);
                banner.getElement().setInnerText(text);
            }
        }
    }

    @UiHandler("login")
    void login(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().login();
        }
    }

    @UiHandler("reset")
    void reset(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().emailResetPassword();
        }
    }

    @UiHandler("userName")
    void onUserNameKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            if (getUiHandlers() != null) {
                getUiHandlers().login();
            }
        }
    }

    @UiHandler("password")
    void onPasswordKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            if (getUiHandlers() != null) {
                getUiHandlers().login();
            }
        }
    }
}
