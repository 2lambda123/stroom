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

package stroom.security.client;

import stroom.core.client.MenuKeys;
import stroom.document.client.event.ShowPermissionsDialogEvent;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.NodeToolsPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.DocumentPermissionsPresenter;
import stroom.security.client.presenter.UsersAndGroupsPresenter;
import stroom.security.shared.PermissionNames;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ManageUserPlugin extends NodeToolsPlugin {

    private final AsyncProvider<UsersAndGroupsPresenter> usersAndGroupsPresenterProvider;

    @Inject
    public ManageUserPlugin(final EventBus eventBus, final ClientSecurityContext securityContext,
                            final AsyncProvider<UsersAndGroupsPresenter> usersAndGroupsPresenterProvider,
                            final AsyncProvider<DocumentPermissionsPresenter> documentPermissionsPresenterProvider) {
        super(eventBus, securityContext);
        this.usersAndGroupsPresenterProvider = usersAndGroupsPresenterProvider;

        eventBus.addHandler(ShowPermissionsDialogEvent.getType(),
                event -> documentPermissionsPresenterProvider.get(new AsyncCallback<DocumentPermissionsPresenter>() {
                    @Override
                    public void onSuccess(final DocumentPermissionsPresenter presenter) {
                        presenter.show(event.getExplorerNode());
                    }

                    @Override
                    public void onFailure(final Throwable caught) {
                    }
                }));
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            final Command command = () ->
                    usersAndGroupsPresenterProvider.get(new AsyncCallback<UsersAndGroupsPresenter>() {
                        @Override
                        public void onSuccess(final UsersAndGroupsPresenter presenter) {
                            final PopupSize popupSize = PopupSize.resizable(800, 600);
                            ShowPopupEvent.builder(presenter)
                                    .popupType(PopupType.CLOSE_DIALOG)
                                    .popupSize(popupSize)
                                    .caption("User Permissions")
                                    .onShow(e -> presenter.focus())
                                    .fire();
                        }

                        @Override
                        public void onFailure(final Throwable caught) {
                        }
                    });
            event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
                    new IconMenuItem.Builder()
                            .priority(1)
                            .icon(SvgImage.USER)
                            .text("User Permissions")
                            .command(command)
                            .build());
        }
    }
}
