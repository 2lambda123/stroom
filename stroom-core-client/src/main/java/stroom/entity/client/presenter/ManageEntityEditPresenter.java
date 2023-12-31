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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityServiceLoadAction;
import stroom.entity.shared.NamedEntity;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.Set;

public abstract class ManageEntityEditPresenter<V extends View, E extends NamedEntity> extends MyPresenterWidget<V> {
    private final ClientDispatchAsync dispatcher;
    private final ClientSecurityContext securityContext;
    private E entity;

    @Inject
    public ManageEntityEditPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher, final V view,
                                     final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.dispatcher = dispatcher;
        this.securityContext = securityContext;
    }

    protected ClientSecurityContext getSecurityContext() {
        return securityContext;
    }

//    protected boolean isCurrentUserUpdate() {
//        return getSecurityContext().hasAppPermission(getEntityType(), DocumentPermissionNames.UPDATE);
//    }

    public void showEntity(final E entity, final PopupUiHandlers popupUiHandlers) {
        final String caption = getEntityDisplayType() + " - " + entity.getName();

        final PopupUiHandlers internalPopupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    write(true);
                } else {
                    hide();
                }

                popupUiHandlers.onHideRequest(autoClose, ok);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                popupUiHandlers.onHide(autoClose, ok);
            }
        };

        //final PopupType popupType = isCurrentUserUpdate() ? PopupType.OK_CANCEL_DIALOG : PopupType.CLOSE_DIALOG;
        final PopupType popupType = PopupType.OK_CANCEL_DIALOG;

        if (entity.isPersistent()) {
            // Reload it so we always have the latest version
            final EntityServiceLoadAction<E> action = new EntityServiceLoadAction<E>(DocRef.create(entity),
                    getEntityFetchSet());
            dispatcher.exec(action).onSuccess(result -> {
                setEntity(result);
                read();
                ShowPopupEvent.fire(ManageEntityEditPresenter.this, ManageEntityEditPresenter.this, popupType,
                        getPopupSize(), caption, internalPopupUiHandlers);
            });
        } else {
            // new entity
            setEntity(entity);
            read();
            ShowPopupEvent.fire(ManageEntityEditPresenter.this, ManageEntityEditPresenter.this, popupType,
                    getPopupSize(), caption, internalPopupUiHandlers);
        }
    }

    protected abstract void read();

    protected abstract void write(final boolean hideOnSave);

    protected abstract PopupSize getPopupSize();

    protected abstract String getEntityType();

    protected abstract String getEntityDisplayType();

    protected Set<String> getEntityFetchSet() {
        return null;
    }

    protected void hide() {
        HidePopupEvent.fire(ManageEntityEditPresenter.this, ManageEntityEditPresenter.this);
    }

    public E getEntity() {
        return entity;
    }

    public void setEntity(final E entity) {
        this.entity = entity;
    }
}
