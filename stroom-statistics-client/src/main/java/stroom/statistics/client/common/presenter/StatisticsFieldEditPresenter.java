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

package stroom.statistics.client.common.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.statistics.shared.StatisticField;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;

public class StatisticsFieldEditPresenter
        extends MyPresenterWidget<StatisticsFieldEditPresenter.StatisticsFieldEditView> {
    public interface StatisticsFieldEditView extends View {
        String getFieldName();

        void setFieldName(final String fieldName);
    }

    private String fieldNamePattern;

    private List<StatisticField> otherFields;

    @Inject
    public StatisticsFieldEditPresenter(final EventBus eventBus, final StatisticsFieldEditView view,
                                        final ClientPropertyCache clientPropertyCache) {
        super(eventBus, view);

        final StatisticsFieldEditPresenter thisPresenter = this;

        clientPropertyCache.get()
                .onSuccess(result -> {
                    String fieldNamePattern = result.get(ClientProperties.NAME_PATTERN);

                    if (fieldNamePattern == null || fieldNamePattern.isEmpty()) {
                        fieldNamePattern = StatisticStoreEntity.DEFAULT_NAME_PATTERN_VALUE;
                    }

                    thisPresenter.setFieldNamePattern(fieldNamePattern);
                })
                .onFailure(caught -> AlertEvent.fireError(StatisticsFieldEditPresenter.this, caught.getMessage(), null));
    }

    public void read(final StatisticField field, final List<StatisticField> otherFields) {
        this.otherFields = otherFields;
        getView().setFieldName(field.getFieldName());

    }

    public boolean write(final StatisticField field) {
        String name = getView().getFieldName();
        name = name.trim();

        field.setFieldName(name);

        if (name.length() == 0) {
            AlertEvent.fireWarn(this, "An index field must have a name", null);
            return false;
        }
        if (otherFields.contains(field)) {
            AlertEvent.fireWarn(this, "Another field with this name already exists", null);
            return false;
        }
        if (fieldNamePattern != null && fieldNamePattern.length() > 0) {
            if (name == null || !name.matches(fieldNamePattern)) {
                AlertEvent.fireWarn(this,
                        "Invalid name \"" + name + "\" (valid name pattern: " + fieldNamePattern + ")", null);
                return false;
            }
        }
        return true;
    }

    public void show(final String caption, final PopupUiHandlers uiHandlers) {
        final PopupSize popupSize = new PopupSize(305, 78, 305, 78, 800, 78, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, uiHandlers);
    }

    public void hide() {
        HidePopupEvent.fire(this, this);
    }

    public void setFieldNamePattern(final String fieldNamePattern) {
        this.fieldNamePattern = fieldNamePattern;
    }
}
