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

package stroom.security.client.presenter;

import com.google.gwt.user.client.ui.Button;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.item.client.ItemListBox;
import stroom.security.shared.ChangeDocumentPermissionsAction;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.CopyPermissionsFromParentAction;
import stroom.security.shared.FetchAllDocumentPermissionsAction;
import stroom.security.shared.UserPermission;
import stroom.security.shared.UserRef;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView;
import stroom.widget.tab.client.presenter.LinkTabsPresenter;
import stroom.widget.tab.client.presenter.TabData;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DocumentPermissionsPresenter
        extends MyPresenterWidget<DocumentPermissionsPresenter.DocumentPermissionsView> {
    private final LinkTabsPresenter tabPresenter;
    private final ClientDispatchAsync dispatcher;
    private final Provider<DocumentPermissionsTabPresenter> documentPermissionsListPresenterProvider;
    private final Provider<FolderPermissionsTabPresenter> folderPermissionsListPresenterProvider;
    private final ChangeSet<UserPermission> changeSet = new ChangeSet<>();

    @Inject
    public DocumentPermissionsPresenter(final EventBus eventBus, final DocumentPermissionsView view, final LinkTabsPresenter tabPresenter,
                                        final ClientDispatchAsync dispatcher, final Provider<DocumentPermissionsTabPresenter> documentPermissionsListPresenterProvider, final Provider<FolderPermissionsTabPresenter> folderPermissionsListPresenterProvider) {
        super(eventBus, view);
        this.tabPresenter = tabPresenter;
        this.dispatcher = dispatcher;
        this.documentPermissionsListPresenterProvider = documentPermissionsListPresenterProvider;
        this.folderPermissionsListPresenterProvider = folderPermissionsListPresenterProvider;

        view.setTabsView(tabPresenter.getView());
    }

    public void show(final ExplorerNode explorerNode) {
        getView().setCascadeVisible(DocumentTypes.isFolder(explorerNode.getType()));
        final DocumentPermissionsTabPresenter usersPresenter = getTabPresenter(explorerNode);
        final DocumentPermissionsTabPresenter groupsPresenter = getTabPresenter(explorerNode);

        final TabData groups = tabPresenter.addTab("Groups", groupsPresenter);
        final TabData users = tabPresenter.addTab("Users", usersPresenter);

        tabPresenter.changeSelectedTab(groups);

        getView().getCopyPermissionsFromParentButton().addClickHandler(event -> {
            dispatcher.exec(new CopyPermissionsFromParentAction(explorerNode.getDocRef()))
                    .onSuccess(documentPermissions -> {
                        // We want to wipe existing permissions, which means updating the removeSet on the changeSet.
                        Map<UserRef, Set<String>> permissionsToRemove = new HashMap<>();
                        permissionsToRemove.putAll(usersPresenter.getDocumentPermissions().getUserPermissions());
                        permissionsToRemove.putAll(groupsPresenter.getDocumentPermissions().getUserPermissions());
                        for (UserRef userRef : permissionsToRemove.keySet()) {
                            for (String permission : permissionsToRemove.get(userRef)) {
                                changeSet.remove(new UserPermission(userRef, permission));
                            }
                        }
                        // We need to update the changeSet with all the new permissions.
                        for (UserRef userRef : documentPermissions.getUserPermissions().keySet()) {
                            for (String permission : documentPermissions.getPermissionsForUser(userRef)) {
                                changeSet.add(new UserPermission(userRef, permission));
                            }
                        }
                        // We need to set the document permissions so that what's been changed is visible.
                        usersPresenter.setDocumentPermissions(documentPermissions, false, changeSet);
                        groupsPresenter.setDocumentPermissions(documentPermissions, true, changeSet);
                    });
        });
        // If we're looking at the root node then we can't copy from the parent because there isn't one.
        if (DocumentTypes.isSystem(explorerNode.getType())) {
            getView().getCopyPermissionsFromParentButton().setEnabled(false);
        }

        final FetchAllDocumentPermissionsAction fetchAllDocumentPermissionsAction = new FetchAllDocumentPermissionsAction(explorerNode.getDocRef());
        dispatcher.exec(fetchAllDocumentPermissionsAction).onSuccess(documentPermissions -> {
            usersPresenter.setDocumentPermissions(documentPermissions, false, changeSet);
            groupsPresenter.setDocumentPermissions(documentPermissions, true, changeSet);

            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        dispatcher.exec(new ChangeDocumentPermissionsAction(documentPermissions.getDocument(), changeSet, getView().getCascade().getSelectedItem()))
                                .onSuccess(res -> hide(autoClose, ok));
                    } else {
                        hide(autoClose, ok);
                    }
                }

                @Override
                public void onHide(boolean autoClose, boolean ok) {
                }
            };

            PopupSize popupSize;
            if (DocumentTypes.isFolder(explorerNode.getType())) {
                popupSize = new PopupSize(384, 664, 384, 664, true);
            } else {
                popupSize = new PopupSize(384, 500, 384, 500, true);
            }

            ShowPopupEvent.fire(DocumentPermissionsPresenter.this, DocumentPermissionsPresenter.this, PopupView.PopupType.OK_CANCEL_DIALOG, popupSize, "Set " + explorerNode.getType() + " Permissions", popupUiHandlers);
        });
    }

    private DocumentPermissionsTabPresenter getTabPresenter(final ExplorerNode entity) {
        if (DocumentTypes.isFolder(entity.getType())) {
            return folderPermissionsListPresenterProvider.get();
        }

        return documentPermissionsListPresenterProvider.get();
    }

    private void hide(boolean autoClose, boolean ok) {
        HidePopupEvent.fire(this, this, autoClose, ok);
    }

    public interface DocumentPermissionsView extends View {
        void setTabsView(View view);

        ItemListBox<ChangeDocumentPermissionsAction.Cascade> getCascade();

        void setCascadeVisible(boolean visible);

        Button getCopyPermissionsFromParentButton();
    }
}