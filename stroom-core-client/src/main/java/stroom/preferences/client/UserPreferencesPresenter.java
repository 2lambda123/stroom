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
import stroom.editor.client.presenter.ChangeCurrentPreferencesEvent;
import stroom.editor.client.presenter.CurrentPreferences;
import stroom.preferences.client.UserPreferencesPresenter.UserPreferencesView;
import stroom.query.api.v2.TimeZone;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.ui.config.shared.Themes;
import stroom.ui.config.shared.Themes.ThemeType;
import stroom.ui.config.shared.UserPreferences;
import stroom.ui.config.shared.UserPreferences.EditorKeyBindings;
import stroom.ui.config.shared.UserPreferences.Toggle;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorTheme;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorTheme.AceThemeType;

import java.util.List;
import java.util.Objects;

public final class UserPreferencesPresenter
        extends MyPresenterWidget<UserPreferencesView>
        implements UserPreferencesUiHandlers {

    private final UserPreferencesManager userPreferencesManager;
    private UserPreferences originalPreferences;

    @Inject
    public UserPreferencesPresenter(
            final EventBus eventBus,
            final UserPreferencesView view,
            final UserPreferencesManager userPreferencesManager,
            final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.userPreferencesManager = userPreferencesManager;
        view.setUiHandlers(this);
        view.setAsDefaultVisible(clientSecurityContext.hasAppPermission(PermissionNames.MANAGE_PROPERTIES_PERMISSION));
    }

    @Override
    protected void onBind() {
    }

    @Override
    public void onChange() {
        final UserPreferences before = userPreferencesManager.getCurrentUserPreferences();
        UserPreferences after = write();

        if (!Objects.equals(before.getTheme(), after.getTheme())) {
            // Theme changed
            final ThemeType themeTypeBefore = Themes.getThemeType(before.getTheme());
            final ThemeType themeTypeAfter = Themes.getThemeType(after.getTheme());
            if (!Objects.equals(themeTypeBefore, themeTypeAfter)) {
                // Different theme type so change the list of editor themes
                final List<String> editorThemes = userPreferencesManager.getEditorThemes(themeTypeAfter);
                getView().setEditorThemes(editorThemes);
                // As we have changed theme type, we need to set an editor theme for that new type
                final String defaultEditorTheme = userPreferencesManager.getDefaultEditorTheme(themeTypeAfter);
//                GWT.log("defaultEditorTheme: " + defaultEditorTheme);
                getView().setEditorTheme(defaultEditorTheme);
                // Update the prefs with the change
                after = write();
            }
        }
        userPreferencesManager.setCurrentPreferences(after);

//        GWT.log("theme: " + userPreferencesManager.getCurrentPreferences().getTheme()
//        + " editorTheme: " + userPreferencesManager.getCurrentPreferences().getEditorTheme());
        triggerThemeChange(userPreferencesManager.getCurrentPreferences());
    }

    private void triggerThemeChange(final CurrentPreferences currentPreferences) {
        final HasHandlers handlers = event -> getEventBus().fireEvent(event);
        ChangeCurrentPreferencesEvent.fire(handlers, currentPreferences);
    }

    @Override
    public void onSetAsDefault() {
        ConfirmEvent.fire(this,
                "Are you sure you want to set the current preferences as the defaults for ALL users?" +
                        "\nThis will not change individual users' saved preferences.",
                (ok) -> {
                    if (ok) {
                        final UserPreferences userPreferences = write();
                        userPreferencesManager.setDefaultUserPreferences(userPreferences, this::reset);
                    }
                });
    }

    @Override
    public void onRevertToDefault() {
        userPreferencesManager.resetToDefaultUserPreferences(this::reset);
    }

    private void reset(final UserPreferences userPreferences) {
        originalPreferences = userPreferences;
        read(userPreferences);
        userPreferencesManager.setCurrentPreferences(userPreferences);
//        final String editorTheme = selectEditorTheme(originalPreferences, userPreferences);
//        triggerThemeChange(userPreferences.getTheme(), editorTheme, userPreferences.getEditorKeyBindings());
        triggerThemeChange(userPreferencesManager.getCurrentPreferences());
    }

    public void show() {
        final String caption = "User Preferences";

        userPreferencesManager.fetch(userPreferences -> {
            originalPreferences = userPreferences;
            read(userPreferences);
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(PopupSize.resizableX())
                    .caption(caption)
                    .onShow(e -> getView().focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            final UserPreferences newUserPreferences = write();
                            userPreferencesManager.setCurrentPreferences(newUserPreferences);
                            if (!Objects.equals(newUserPreferences, originalPreferences)) {
                                userPreferencesManager.update(newUserPreferences, (result) -> e.hide());
                            } else {
                                e.hide();
                            }
                        } else {
                            userPreferencesManager.setCurrentPreferences(originalPreferences);
                            // Ensure screens revert to the old prefs
                            triggerThemeChange(userPreferencesManager.getCurrentPreferences());
                            e.hide();
                        }
                    })
                    .fire();
        });
    }

    private void read(final UserPreferences userPreferences) {
        final String themeName = userPreferences.getTheme();
        final ThemeType themeType = Themes.getThemeType(themeName);
        final AceThemeType aceThemeType = ThemeType.LIGHT.equals(themeType)
                ? AceThemeType.LIGHT
                : AceThemeType.DARK;

        String editorThemeName = userPreferences.getEditorTheme();
        if (AceEditorTheme.isValidThemeName(editorThemeName)) {
            if (!AceEditorTheme.matchesThemeType(themeName, aceThemeType)) {
                // e.g. light editor theme with a dark stroom theme, so use an appropriate one
                editorThemeName = getDefaultEditorTheme(aceThemeType).getName();
            }
        } else {
            editorThemeName = getDefaultEditorTheme(aceThemeType).getName();
        }

        getView().setThemes(userPreferencesManager.getThemes());
        getView().setTheme(themeName);
        // Get applicable editor themes for the stroom theme type
        getView().setEditorThemes(userPreferencesManager.getEditorThemes(themeType));
        getView().setEditorTheme(editorThemeName);
        getView().setEditorKeyBindings(userPreferences.getEditorKeyBindings());
        getView().setEditorLiveAutoCompletion(userPreferences.getEditorLiveAutoCompletion());
        getView().setDensity(userPreferences.getDensity());
        getView().setFonts(userPreferencesManager.getFonts());
        getView().setFont(userPreferences.getFont());
        getView().setFontSize(userPreferences.getFontSize());
        getView().setPattern(userPreferences.getDateTimePattern());

        final TimeZone timeZone = userPreferences.getTimeZone();
        if (timeZone != null) {
            getView().setTimeZoneUse(timeZone.getUse());
            getView().setTimeZoneId(timeZone.getId());
            getView().setTimeZoneOffsetHours(timeZone.getOffsetHours());
            getView().setTimeZoneOffsetMinutes(timeZone.getOffsetMinutes());
        }
    }

    private static AceEditorTheme getDefaultEditorTheme(final AceThemeType aceThemeType) {
        return aceThemeType.isLight()
                ? AceEditorTheme.DEFAULT_LIGHT_THEME
                : AceEditorTheme.DEFAULT_DARK_THEME;
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
                .editorTheme(getView().getEditorTheme())
                .editorKeyBindings(getView().getEditorKeyBindings())
                .editorLiveAutoCompletion(getView().getEditorLiveAutoCompletion())
                .density(getView().getDensity())
                .font(getView().getFont())
                .fontSize(getView().getFontSize())
                .dateTimePattern(getView().getPattern())
                .timeZone(timeZone)
                .build();
    }


    // --------------------------------------------------------------------------------


    public interface UserPreferencesView extends View, Focus, HasUiHandlers<UserPreferencesUiHandlers> {

        String getTheme();

        void setTheme(String theme);

        void setThemes(List<String> themes);

        String getEditorTheme();

        void setEditorTheme(String editorTheme);

        void setEditorThemes(List<String> editorThemes);

        EditorKeyBindings getEditorKeyBindings();

        void setEditorKeyBindings(EditorKeyBindings editorKeyBindings);

        Toggle getEditorLiveAutoCompletion();

        void setEditorLiveAutoCompletion(Toggle editorLiveAutoCompletion);

        String getDensity();

        void setDensity(String density);

        String getFont();

        void setFont(String font);

        void setFonts(List<String> themes);

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
