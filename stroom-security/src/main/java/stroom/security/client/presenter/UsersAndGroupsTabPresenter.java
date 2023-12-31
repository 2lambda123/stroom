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

package stroom.security.client.presenter;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.ManageNewEntityPresenter;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.CreateUserAction;
import stroom.security.shared.DeleteUserAction;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.UserRef;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

import javax.inject.Inject;

public class UsersAndGroupsTabPresenter extends
        MyPresenterWidget<UserListView> implements HasHandlers {
    public static final String LIST = "LIST";
    private final AdvancedUserListPresenter listPresenter;
    private final Provider<UserEditPresenter> userEditPresenterProvider;
    private final Provider<GroupEditPresenter> groupEditPresenterProvider;
    private final ManageNewEntityPresenter newPresenter;
    private final ClientDispatchAsync dispatcher;
    private final GlyphButtonView newButton;
    private final GlyphButtonView openButton;
    private final GlyphButtonView deleteButton;
    private final FindUserCriteria criteria = new FindUserCriteria();

    @Inject
    public UsersAndGroupsTabPresenter(final EventBus eventBus,
                                      final AdvancedUserListPresenter listPresenter, final Provider<UserEditPresenter> userEditPresenterProvider, final Provider<GroupEditPresenter> groupEditPresenterProvider,
                                      final ManageNewEntityPresenter newPresenter,
                                      final ClientDispatchAsync dispatcher, final ClientSecurityContext securityContext) {
        super(eventBus, listPresenter.getView());
        this.listPresenter = listPresenter;
        this.userEditPresenterProvider = userEditPresenterProvider;
        this.groupEditPresenterProvider = groupEditPresenterProvider;
        this.dispatcher = dispatcher;
        this.newPresenter = newPresenter;

        setInSlot(LIST, listPresenter);

        newButton = listPresenter.addButton(GlyphIcons.NEW_ITEM);
        openButton = listPresenter.addButton(GlyphIcons.OPEN);
        deleteButton = listPresenter.addButton(GlyphIcons.DELETE);

        final boolean updatePerm = securityContext.hasAppPermission(FindUserCriteria.MANAGE_USERS_PERMISSION);

        if (!updatePerm) {
            deleteButton.setVisible(false);
        }
        if (!updatePerm) {
            newButton.setVisible(false);
        }
    }

    @Override
    protected void onBind() {
        registerHandler(listPresenter.getDataGridView().getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onOpen();
            }
        }));
        registerHandler(newButton.addClickHandler(event -> {
            if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                onNew();
            }
        }));
        registerHandler(openButton.addClickHandler(event -> {
            if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                onOpen();
            }
        }));
        registerHandler(deleteButton.addClickHandler(event -> {
            if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
                onDelete();
            }
        }));

        super.onBind();
    }

    public void setGroup(final boolean group) {
        criteria.setGroup(group);
        listPresenter.setup(criteria);
    }

    private void enableButtons() {
        final boolean enabled = listPresenter.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    private void onOpen() {
        final UserRef e = listPresenter.getSelectionModel().getSelected();
        onEdit(e);
    }

    @SuppressWarnings("unchecked")
    public void onEdit(final UserRef userRef) {
        if (userRef != null) {
            edit(userRef);
        }
    }

    private void onDelete() {
        final UserRef userRef = listPresenter.getSelectionModel().getSelected();
        if (userRef != null) {
            ConfirmEvent.fire(UsersAndGroupsTabPresenter.this, "Are you sure you want to delete the selected " + getTypeName() + "?",
                    ok -> {
                        if (ok) {
                            dispatcher.exec(new DeleteUserAction(userRef)).onSuccess(result -> {
                                listPresenter.refresh();
                                listPresenter.getSelectionModel().clear();
                            });
                        }
                    });
        }
    }

    private void onNew() {
        final PopupUiHandlers hidePopupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    dispatcher.exec(new CreateUserAction(newPresenter.getName(), criteria.getGroup())).onSuccess(result -> {
                        newPresenter.hide();
                        edit(result);
                    });
                } else {
                    newPresenter.hide();
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Ignore hide.
            }
        };

        newPresenter.show(hidePopupUiHandlers);
    }

    private void edit(final UserRef userRef) {
        final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                listPresenter.refresh();
            }
        };

        if (userRef.isGroup()) {
            if (groupEditPresenterProvider != null) {
                final GroupEditPresenter groupEditPresenter = groupEditPresenterProvider.get();
                groupEditPresenter.show(userRef, popupUiHandlers);
            }
        } else {
            if (userEditPresenterProvider != null) {
                final UserEditPresenter userEditPresenter = userEditPresenterProvider.get();
                userEditPresenter.show(userRef, popupUiHandlers);
            }
        }
    }

    private String getTypeName() {
        if (criteria.getGroup()) {
            return "group";
        }

        return "user";
    }
}
