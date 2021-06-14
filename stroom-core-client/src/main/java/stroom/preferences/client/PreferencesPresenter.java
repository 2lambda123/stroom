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

package stroom.preferences.client;

import stroom.alert.client.event.ConfirmEvent;
import stroom.editor.client.presenter.ChangeThemeEvent;
import stroom.preferences.client.PreferencesPresenter.PreferencesView;
import stroom.query.api.v2.TimeZone;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;

public final class PreferencesPresenter
        extends MyPresenterWidget<PreferencesView>
        implements PreferencesUiHandlers {

    private final PreferencesManager preferencesManager;
    private UserPreferences originalPreferences;

    @Inject
    public PreferencesPresenter(
            final EventBus eventBus,
            final PreferencesView view,
            final PreferencesManager preferencesManager,
            final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.preferencesManager = preferencesManager;

        view.setUiHandlers(this);
        view.setAsDefaultVisible(clientSecurityContext.hasAppPermission(PermissionNames.MANAGE_PROPERTIES_PERMISSION));
    }

    @Override
    protected void onBind() {
    }

    @Override
    public void onChange() {
        final UserPreferences userPreferences = write();
        preferencesManager.setCurrentPreferences(userPreferences);
        final HasHandlers handlers = event -> getEventBus().fireEvent(event);
        ChangeThemeEvent.fire(handlers, userPreferences.getTheme());
    }

    @Override
    public void onSetAsDefault() {
        ConfirmEvent.fire(this,
                "Are you sure you want to set the current preferences for all users?",
                (ok) -> {
                    if (ok) {
                        final UserPreferences userPreferences = write();
                        preferencesManager.setDefaultUserPreferences(userPreferences, this::reset);
                    }
                });
    }

    @Override
    public void onRevertToDefault() {
        preferencesManager.resetToDefaultUserPreferences(this::reset);
    }

    private void reset(final UserPreferences userPreferences) {
        originalPreferences = userPreferences;
        read(userPreferences);
        preferencesManager.setCurrentPreferences(userPreferences);
    }

    public void show() {
        final String caption = "User Preferences";
        final PopupType popupType = PopupType.OK_CANCEL_DIALOG;
        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final UserPreferences userPreferences = write();
                    preferencesManager.setCurrentPreferences(userPreferences);
                    if (!Objects.equals(userPreferences, originalPreferences)) {
                        preferencesManager.update(userPreferences, (result) -> hide());
                    } else {
                        hide();
                    }
                } else {
                    preferencesManager.setCurrentPreferences(originalPreferences);
                    hide();
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        };

        preferencesManager.fetch(userPreferences -> {
            originalPreferences = userPreferences;
            read(userPreferences);
            ShowPopupEvent.fire(
                    PreferencesPresenter.this,
                    PreferencesPresenter.this,
                    popupType,
                    getPopupSize(),
                    caption,
                    popupUiHandlers);
        });
    }

    private PopupSize getPopupSize() {
        return new PopupSize(
                700, 556,
                700, 556,
                1024, 556,
                true);
    }

    protected void hide() {
        HidePopupEvent.fire(
                PreferencesPresenter.this,
                PreferencesPresenter.this);
    }


    private void read(final UserPreferences userPreferences) {
        final TimeZone timeZone = userPreferences.getTimeZone();

        getView().setThemes(preferencesManager.getThemes());
        getView().setTheme(userPreferences.getTheme());
        getView().setFont(userPreferences.getFont());
        getView().setFontSize(userPreferences.getFontSize());
        getView().setPattern(userPreferences.getDateTimePattern());

        if (timeZone != null) {
            getView().setTimeZoneUse(timeZone.getUse());
            getView().setTimeZoneId(timeZone.getId());
            getView().setTimeZoneOffsetHours(timeZone.getOffsetHours());
            getView().setTimeZoneOffsetMinutes(timeZone.getOffsetMinutes());
        }
    }

    private UserPreferences write() {
        final TimeZone timeZone = TimeZone.builder()
                .use(getView().getTimeZoneUse())
                .id(getView().getTimeZoneId())
                .offsetHours(getView().getTimeZoneOffsetHours())
                .offsetMinutes(getView().getTimeZoneOffsetMinutes())
                .build();

        return UserPreferences.builder()
                .theme(getView().getTheme())
                .font(getView().getFont())
                .fontSize(getView().getFontSize())
                .dateTimePattern(getView().getPattern())
                .timeZone(timeZone)
                .build();
    }

    public interface PreferencesView extends View, HasUiHandlers<PreferencesUiHandlers> {

        String getTheme();

        void setTheme(String theme);

        void setThemes(List<String> themes);

        String getFont();

        void setFont(String font);

        String getFontSize();

        void setFontSize(String fontSize);

        String getPattern();

        void setPattern(String pattern);

        TimeZone.Use getTimeZoneUse();

        void setTimeZoneUse(TimeZone.Use use);

        String getTimeZoneId();

        void setTimeZoneId(String timeZoneId);

        Integer getTimeZoneOffsetHours();

        void setTimeZoneOffsetHours(Integer timeZoneOffsetHours);

        Integer getTimeZoneOffsetMinutes();

        void setTimeZoneOffsetMinutes(Integer timeZoneOffsetMinutes);

        void setAsDefaultVisible(boolean visible);
    }
}