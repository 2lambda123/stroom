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

package stroom.document.client;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.core.client.HasSave;
import stroom.core.client.HasSaveRegistry;
import stroom.core.client.UrlConstants;
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docref.HasDisplayValue;
import stroom.document.client.event.CopyDocumentEvent;
import stroom.document.client.event.CreateDocumentEvent;
import stroom.document.client.event.DeleteDocumentEvent;
import stroom.document.client.event.MoveDocumentEvent;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.document.client.event.RenameDocumentEvent;
import stroom.document.client.event.ResultCallback;
import stroom.document.client.event.SaveAsDocumentEvent;
import stroom.document.client.event.SetDocumentAsFavouriteEvent;
import stroom.document.client.event.ShowCopyDocumentDialogEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.document.client.event.ShowInfoDocumentDialogEvent;
import stroom.document.client.event.ShowMoveDocumentDialogEvent;
import stroom.document.client.event.ShowPermissionsDialogEvent;
import stroom.document.client.event.ShowRenameDocumentDialogEvent;
import stroom.document.client.event.WriteDocumentEvent;
import stroom.explorer.client.event.ExplorerTreeDeleteEvent;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.event.HighlightExplorerNodeEvent;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.client.event.ShowExplorerMenuEvent;
import stroom.explorer.client.event.ShowFindEvent;
import stroom.explorer.client.event.ShowNewMenuEvent;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypeGroup;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerFavouriteResource;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodePermissions;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerServiceCopyRequest;
import stroom.explorer.shared.ExplorerServiceCreateRequest;
import stroom.explorer.shared.ExplorerServiceDeleteRequest;
import stroom.explorer.shared.ExplorerServiceMoveRequest;
import stroom.explorer.shared.ExplorerServiceRenameRequest;
import stroom.explorer.shared.PermissionInheritance;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.client.event.ExportConfigEvent;
import stroom.importexport.client.event.ImportConfigEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.client.ClipboardUtil;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.menu.client.presenter.Separator;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.tab.client.event.RequestCloseAllTabsEvent;
import stroom.widget.tab.client.event.RequestCloseOtherTabsEvent;
import stroom.widget.tab.client.event.RequestCloseSavedTabsEvent;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
import stroom.widget.tab.client.event.ShowTabMenuEvent;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class DocumentPluginEventManager extends Plugin {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    private static final ExplorerFavouriteResource EXPLORER_FAV_RESOURCE = GWT.create(ExplorerFavouriteResource.class);

    private final HasSaveRegistry hasSaveRegistry;
    private final RestFactory restFactory;
    private final DocumentTypeCache documentTypeCache;
    private final DocumentPluginRegistry documentPluginRegistry;
    private final ClientSecurityContext securityContext;
    private TabData selectedTab;
    private MultiSelectionModel<ExplorerNode> selectionModel;

    @Inject
    public DocumentPluginEventManager(final EventBus eventBus,
                                      final HasSaveRegistry hasSaveRegistry,
                                      final RestFactory restFactory,
                                      final DocumentTypeCache documentTypeCache,
                                      final DocumentPluginRegistry documentPluginRegistry,
                                      final ClientSecurityContext securityContext) {
        super(eventBus);
        this.hasSaveRegistry = hasSaveRegistry;
        this.restFactory = restFactory;
        this.documentTypeCache = documentTypeCache;
        this.documentPluginRegistry = documentPluginRegistry;
        this.securityContext = securityContext;

        KeyBinding.addCommand(Action.ITEM_CLOSE, () -> {
            if (isTabItemSelected(selectedTab)) {
                RequestCloseTabEvent.fire(DocumentPluginEventManager.this, selectedTab);
            }
        });
        KeyBinding.addCommand(Action.ITEM_CLOSE_ALL, () -> RequestCloseAllTabsEvent.fire(this));

        KeyBinding.addCommand(Action.ITEM_SAVE, () -> {
            if (isDirty(selectedTab)) {
                final HasSave hasSave = (HasSave) selectedTab;
                hasSave.save();
            }
        });
        KeyBinding.addCommand(Action.ITEM_SAVE_ALL, hasSaveRegistry::save);

        KeyBinding.addCommand(Action.FIND, () -> ShowFindEvent.fire(this));
    }

    @Override
    protected void onBind() {
        super.onBind();

        // track the currently selected content tab.
        registerHandler(getEventBus().addHandler(ContentTabSelectionChangeEvent.getType(),
                e -> selectedTab = e.getTabData()));

        // // 2. Handle requests to close tabs.
        // registerHandler(getEventBus().addHandler(
        // RequestCloseTabEvent.getType(), new RequestCloseTabHandler() {
        // @Override
        // public void onCloseTab(final RequestCloseTabEvent event) {
        // final TabData tabData = event.getTabData();
        // if (tabData instanceof EntityTabData) {
        // final EntityTabData entityTabData = (EntityTabData) tabData;
        // final DocumentPlugin<?> plugin = pluginMap
        // .get(entityTabData.getType());
        // if (plugin != null) {
        // plugin.close(entityTabData, false);
        // }
        // }
        // }
        // }));
        //
        // // 3. Handle requests to close all tabs.
        // registerHandler(getEventBus().addHandler(
        // RequestCloseAllTabsEvent.getType(), new CloseAllTabsHandler() {
        // @Override
        // public void onCloseAllTabs(
        // final RequestCloseAllTabsEvent event) {
        // for (final DocumentPlugin<?> plugin : pluginMap.values()) {
        // plugin.closeAll(event.isLogoffAfterClose());
        // }
        // }
        // }));

        // 4. Handle explorer events and open items as required.
        registerHandler(
                getEventBus().addHandler(ExplorerTreeSelectEvent.getType(), event -> {
                    // Remember the selection model.
                    if (event.getSelectionModel() != null) {
                        selectionModel = event.getSelectionModel();
                    }

                    if (!event.getSelectionType().isRightClick() && !event.getSelectionType().isMultiSelect()) {
                        final ExplorerNode explorerNode = event.getSelectionModel().getSelected();
                        if (explorerNode != null) {
                            final DocumentPlugin<?> plugin = documentPluginRegistry.get(explorerNode.getType());
                            if (plugin != null) {
                                plugin.open(explorerNode.getDocRef(), event.getSelectionType().isDoubleSelect());
                            }
                        }
                    }
                }));

//        clientPropertyCache.get()
//                .onSuccess(uiConfig -> {
//                    registerHandler(
//                            getEventBus().addHandler(ShowPermissionsDialogEvent.getType(), event -> {
//                                final Hyperlink hyperlink = new Hyperlink.Builder()
//                                        .text("Permissions")
//                                        .href(uiConfig.getUrl().getDocumentPermissions() +
//                                        event.getExplorerNode().getUuid())
//                                        .type(HyperlinkType.TAB + "|Document Permissions")
//                                        .icon(SvgPresets.PERMISSIONS)
//                                        .build();
//                                HyperlinkEvent.fire(this, hyperlink);
//                            }));
//                });


        // 11. Handle entity reload events.
        registerHandler(getEventBus().addHandler(RefreshDocumentEvent.getType(), event -> {
            final DocumentPlugin<?> plugin = documentPluginRegistry.get(event.getDocRef().getType());
            if (plugin != null) {
                plugin.reload(event.getDocRef());
            }
        }));

        // 5. Handle save events.
        registerHandler(getEventBus().addHandler(WriteDocumentEvent.getType(), event -> {
            if (isDirty(event.getTabData())) {
                final DocumentTabData entityTabData = event.getTabData();
                final DocumentPlugin<?> plugin = documentPluginRegistry.get(entityTabData.getType());
                if (plugin != null) {
                    plugin.save(entityTabData);
                }
            }
        }));

        // 6. Handle save as events.
        registerHandler(getEventBus().addHandler(SaveAsDocumentEvent.getType(), event -> {
            // First get the explorer node for the docref.
            final Rest<ExplorerNode> rest = restFactory.create();
            rest
                    .onSuccess(explorerNode -> {
                        // Now we have the explorer node proceed with the save as.
                        final DocumentPlugin<?> plugin = documentPluginRegistry.get(explorerNode.getType());
                        if (plugin != null) {
                            plugin.saveAs(explorerNode);
                        }
                    })
                    .call(EXPLORER_RESOURCE)
                    .getFromDocRef(event.getDocRef());
        }));

        //////////////////////////////
        // START EXPLORER EVENTS
        ///////////////////////////////

        // 1. Handle entity creation events.
        registerHandler(getEventBus().addHandler(CreateDocumentEvent.getType(), event ->
                create(event.getDocType(),
                        event.getDocName(),
                        event.getDestinationFolder(),
                        event.getPermissionInheritance(),
                        explorerNode -> {
                            // Hide the create document presenter.
                            HidePopupEvent.builder(event.getPresenter()).fire();

                            highlight(explorerNode);

                            // The initiator of this event can now do what they want with the docref.
                            event.getNewDocConsumer().accept(explorerNode);
                        })));

        // 8.1. Handle entity open events.
        registerHandler(getEventBus().addHandler(OpenDocumentEvent.getType(), event ->
                open(event.getDocRef(), event.getForceOpen())));

        // 8.2. Handle entity copy events.
        registerHandler(getEventBus().addHandler(CopyDocumentEvent.getType(), event -> copy(
                event.getExplorerNodes(), event.getDestinationFolder(), event.getPermissionInheritance(), result -> {
                    // Hide the copy document presenter.
                    HidePopupEvent.builder(event.getPresenter()).fire();

                    if (result.getMessage().length() > 0) {
                        AlertEvent.fireInfo(DocumentPluginEventManager.this,
                                "Unable to copy some items",
                                result.getMessage(),
                                null);
                    }

                    if (result.getExplorerNodes().size() > 0) {
                        highlight(result.getExplorerNodes().get(0));
                    }
                })));

        // 8.3. Handle entity move events.
        registerHandler(getEventBus().addHandler(MoveDocumentEvent.getType(), event -> move(
                event.getExplorerNodes(), event.getDestinationFolder(), event.getPermissionInheritance(), result -> {
                    // Hide the move document presenter.
                    HidePopupEvent.builder(event.getPresenter()).fire();

                    if (result.getMessage().length() > 0) {
                        AlertEvent.fireInfo(DocumentPluginEventManager.this,
                                "Unable to move some items",
                                result.getMessage(),
                                null);
                    }

                    if (result.getExplorerNodes().size() > 0) {
                        highlight(result.getExplorerNodes().get(0));
                    }
                })));

        // 8.4. Handle entity delete events.
        registerHandler(getEventBus().addHandler(DeleteDocumentEvent.getType(), event -> {
            if (event.getConfirm()) {
                ConfirmEvent.fire(
                        DocumentPluginEventManager.this,
                        "Are you sure you want to delete this item?",
                        ok -> {
                            if (ok) {
                                delete(event.getDocRefs(), result -> handleDeleteResult(result, event.getCallback()));
                            }
                        });
            } else {
                delete(event.getDocRefs(), result -> handleDeleteResult(result, event.getCallback()));
            }
        }));

        // 9. Handle entity rename events.
        registerHandler(getEventBus().addHandler(RenameDocumentEvent.getType(), event -> {
            // Hide the rename document presenter.
            HidePopupEvent.builder(event.getPresenter()).fire();

            rename(event.getExplorerNode(), event.getDocName(), explorerNode -> {
                highlight(explorerNode);
                RefreshDocumentEvent.fire(this, explorerNode.getDocRef());
            });
        }));

        // 10. Handle entity delete events.
        registerHandler(getEventBus().addHandler(ExplorerTreeDeleteEvent.getType(), event -> {
            if (getSelectedItems().size() > 0) {
                fetchPermissions(getSelectedItems(), documentPermissionMap ->
                        documentTypeCache.fetch(documentTypes -> {
                            final List<ExplorerNode> deletableItems = getExplorerNodeListWithPermission(
                                    documentPermissionMap,
                                    DocumentPermissionNames.DELETE,
                                    false);
                            if (deletableItems.size() > 0) {
                                deleteItems(deletableItems);
                            }
                        }));
            }
        }));

        // Handle setting document as Favourite events
        registerHandler(getEventBus().addHandler(SetDocumentAsFavouriteEvent.getType(), event -> {
            setAsFavourite(event.getDocRef(), event.getSetFavourite());
        }));

        //////////////////////////////
        // END EXPLORER EVENTS
        ///////////////////////////////


        // Handle the display of the `New` item menu
        registerHandler(getEventBus().addHandler(ShowNewMenuEvent.getType(), event -> {
            if (getSelectedItems().size() == 1) {
                final ExplorerNode primarySelection = getPrimarySelection();
                getNewMenuItems(primarySelection).onSuccess(children ->
                        ShowMenuEvent
                                .builder()
                                .items(children)
                                .popupPosition(event.getPopupPosition())
                                .addAutoHidePartner(event.getElement())
                                .fire(this));
            }
        }));

        // Handle the display of the explorer item context menu
        registerHandler(getEventBus().addHandler(ShowExplorerMenuEvent.getType(), event -> {
            final List<ExplorerNode> selectedItems = getSelectedItems();
            final boolean singleSelection = selectedItems.size() == 1;
            final ExplorerNode primarySelection = getPrimarySelection();

            if (selectedItems.size() > 0 && !ExplorerConstants.isFavouritesNode(primarySelection)) {
                showItemContextMenu(event, selectedItems, singleSelection, primarySelection);
            }
        }));

        // Handle the context menu for open tabs
        registerHandler(getEventBus().addHandler(ShowTabMenuEvent.getType(), event -> {
            final List<Item> menuItems = new ArrayList<>();

            menuItems.add(createCloseMenuItem(1, event.getTabData()));
            menuItems.add(createCloseOthersMenuItem(2, event.getTabData()));
            menuItems.add(createCloseSavedMenuItem(3, event.getTabData()));
            menuItems.add(createCloseAllMenuItem(4, event.getTabData()));
            menuItems.add(new Separator(5));
            menuItems.add(createSaveMenuItem(6, event.getTabData()));
            menuItems.add(createSaveAllMenuItem(8));

            ShowMenuEvent
                    .builder()
                    .items(menuItems)
                    .popupPosition(event.getPopupPosition())
                    .fire(this);
        }));
    }

    private void showItemContextMenu(final ShowExplorerMenuEvent event,
                                     final List<ExplorerNode> selectedItems,
                                     final boolean singleSelection,
                                     final ExplorerNode primarySelection) {
        fetchPermissions(selectedItems, documentPermissionMap ->
                documentTypeCache.fetch(documentTypes -> {
                    final List<Item> menuItems = new ArrayList<>();

                    // Only allow the new menu to appear if we have a single selection.
                    addNewMenuItem(menuItems,
                            singleSelection,
                            documentPermissionMap,
                            primarySelection,
                            documentTypes);

                    addModifyMenuItems(menuItems, singleSelection, documentPermissionMap);

                    ShowMenuEvent
                            .builder()
                            .items(menuItems)
                            .popupPosition(event.getPopupPosition())
                            .fire(this);
                })
        );
    }

    private void renameItems(final List<ExplorerNode> explorerNodeList) {
        final List<ExplorerNode> dirtyList = new ArrayList<>();
        final List<ExplorerNode> cleanList = new ArrayList<>();

        explorerNodeList.forEach(node -> {
            final DocRef docRef = node.getDocRef();
            final DocumentPlugin<?> plugin = documentPluginRegistry.get(docRef.getType());
            if (plugin != null && plugin.isDirty(docRef)) {
                dirtyList.add(node);
            } else {
                cleanList.add(node);
            }
        });

        if (dirtyList.size() > 0) {
            final DocRef docRef = dirtyList.get(0).getDocRef();
            AlertEvent.fireWarn(this, "You must save changes to " + docRef.getType() + " '"
                    + docRef.getDisplayValue()
                    + "' before it can be renamed.", null);
        } else if (cleanList.size() > 0) {
            ShowRenameDocumentDialogEvent.fire(DocumentPluginEventManager.this, cleanList);
        }
    }

    private void deleteItems(final List<ExplorerNode> explorerNodeList) {
        if (explorerNodeList != null && explorerNodeList.size() > 0) {
            final List<DocRef> docRefs = explorerNodeList
                    .stream()
                    .map(ExplorerNode::getDocRef)
                    .collect(Collectors.toList());
            DeleteDocumentEvent.fire(
                    DocumentPluginEventManager.this,
                    docRefs,
                    true);
        }
    }

    private void handleDeleteResult(final BulkActionResult result, ResultCallback callback) {
        boolean success = true;
        if (result.getMessage().length() > 0) {
            AlertEvent.fireInfo(DocumentPluginEventManager.this,
                    "Unable to delete some items",
                    result.getMessage(),
                    null);

            success = false;
        }

        // Refresh the tree
        RefreshExplorerTreeEvent.fire(DocumentPluginEventManager.this);

        if (callback != null) {
            callback.onResult(success);
        }
    }

//    private void deleteDocument(final DocRef document, final DocumentTabData tabData) {
//        delete(document).onSuccess(e -> {
//            if (tabData != null) {
//                // Cleanup reference to this tab data.
//                removeTabData(tabData);
//                contentManager.forceClose(tabData);
//            }
//            // Refresh the explorer tree so the document is marked as deleted.
//            RefreshExplorerTreeEvent.fire(DocumentPlugin.this);
//        });
//    }


//    /**
//     * 8.1. This method will copy documents.
//     */
//    void copyDocument(final PresenterWidget<?> popup, final DocRef document, final DocRef folder,
//                      final PermissionInheritance permissionInheritance) {
//        copy(document, folder, permissionInheritance).onSuccess(newDocRef -> {
//            // Hide the copy document presenter.
//            HidePopupEvent.fire(DocumentPluginEventManager.this, popup);
//
//            // Select it in the explorer tree.
//            highlight(newDocRef);
//        });
//    }


    public void create(final String docType,
                       final String docName,
                       final ExplorerNode destinationFolder,
                       final PermissionInheritance permissionInheritance,
                       final Consumer<ExplorerNode> consumer) {
        final Rest<ExplorerNode> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(EXPLORER_RESOURCE)
                .create(new ExplorerServiceCreateRequest(docType,
                        docName,
                        destinationFolder,
                        permissionInheritance));
    }

    private void copy(final List<ExplorerNode> explorerNodes,
                      final ExplorerNode destinationFolder,
                      final PermissionInheritance permissionInheritance,
                      final Consumer<BulkActionResult> consumer) {
        final Rest<BulkActionResult> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(EXPLORER_RESOURCE)
                .copy(new ExplorerServiceCopyRequest(explorerNodes, destinationFolder, permissionInheritance));
    }

    private void move(final List<ExplorerNode> explorerNodes,
                      final ExplorerNode destinationFolder,
                      final PermissionInheritance permissionInheritance,
                      final Consumer<BulkActionResult> consumer) {
        final Rest<BulkActionResult> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(EXPLORER_RESOURCE)
                .move(new ExplorerServiceMoveRequest(explorerNodes, destinationFolder, permissionInheritance));
    }

    private void rename(final ExplorerNode explorerNode, final String docName, final Consumer<ExplorerNode> consumer) {
        final Rest<ExplorerNode> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(EXPLORER_RESOURCE)
                .rename(new ExplorerServiceRenameRequest(explorerNode, docName));
    }

    public void delete(final List<DocRef> docRefs, final Consumer<BulkActionResult> consumer) {
        final Rest<BulkActionResult> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(EXPLORER_RESOURCE)
                .delete(new ExplorerServiceDeleteRequest(docRefs));
    }

    private void setAsFavourite(final DocRef docRef, final boolean setFavourite) {
        final Rest<Void> rest = restFactory.create();
        rest.onSuccess(result -> RefreshExplorerTreeEvent.fire(DocumentPluginEventManager.this));
        if (setFavourite) {
            rest.call(EXPLORER_FAV_RESOURCE).createUserFavourite(docRef);
        } else {
            rest.call(EXPLORER_FAV_RESOURCE).deleteUserFavourite(docRef);
        }
    }

    public void open(final DocRef docRef, final boolean forceOpen) {
        final DocumentPlugin<?> documentPlugin = documentPluginRegistry.get(docRef.getType());
        if (documentPlugin != null) {
            // Decorate the DocRef with its name from the info service (required by the doc presenter)
            restFactory.create()
                    .onSuccess(result -> {
                        if (result != null) {
                            final DocRefInfo docRefInfo = (DocRefInfo) result;
                            if (docRefInfo.getDocRef() != null) {
                                docRef.setName(docRefInfo.getDocRef().getName());
                            }
                        }
                    })
                    .call(EXPLORER_RESOURCE)
                    .info(docRef);
            documentPlugin.open(docRef, forceOpen);
        } else {
            throw new IllegalArgumentException("Document type '" + docRef.getType() + "' not registered");
        }
    }

    /**
     * Highlights the supplied document item in the explorer tree.
     */
    public void highlight(final ExplorerNode explorerNode) {
        HighlightExplorerNodeEvent.fire(DocumentPluginEventManager.this, explorerNode);
    }

    public void highlight(final DocRef docRef) {
        // Obtain the Explorer node for the provided DocRef
        restFactory.create()
                .onSuccess(explorerNode -> highlight((ExplorerNode) explorerNode))
                .call(EXPLORER_RESOURCE)
                .getFromDocRef(docRef);
    }

    private List<ExplorerNode> getExplorerNodeListWithPermission(
            final Map<ExplorerNode, ExplorerNodePermissions> documentPermissionMap,
            final String permission,
            final boolean includeSystemNodes) {
        final List<ExplorerNode> list = new ArrayList<>();
        for (final Map.Entry<ExplorerNode, ExplorerNodePermissions> entry : documentPermissionMap.entrySet()) {
            if ((includeSystemNodes || !DocumentTypes.isSystem(entry.getKey().getType()))
                    && entry.getValue().hasDocumentPermission(permission)) {
                list.add(entry.getKey());
            }
        }

        list.sort(Comparator.comparing(HasDisplayValue::getDisplayValue));
        return list;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);

//        final FutureImpl<List<Item>> future = new FutureImpl<>();
//        final List<ExplorerNode> selectedItems = getSelectedItems();
//        final boolean singleSelection = selectedItems.size() == 1;
//        final ExplorerNode primarySelection = getPrimarySelection();
//
//        fetchPermissions(selectedItems,
//                documentPermissionMap -> documentTypeCache.fetch(documentTypes -> {
//                    final List<Item> menuItems = new ArrayList<>();
//
////                    // Only allow the new menu to appear if we have a single selection.
////                    addNewMenuItem(menuItems,
////                            singleSelection,
////                            documentPermissionMap,
////                            primarySelection,
////                            documentTypes);
////                    menuItems.add(createCloseMenuItem(isTabItemSelected(selectedTab)));
//                    menuItems.add(createCloseAllMenuItem(isTabItemSelected(selectedTab)));
////                    menuItems.add(new Separator(5));
////                    menuItems.add(createSaveMenuItem(6, isDirty(selectedTab)));
//                    menuItems.add(createSaveAllMenuItem(8, hasSaveRegistry.isDirty()));
////                    menuItems.add(new Separator(9));
////                    addModifyMenuItems(menuItems, singleSelection, documentPermissionMap);
//
//                    future.setResult(menuItems);
//                }));
//
//        // Add menu bar item menu.
//        event.getMenuItems()
//                .addMenuItem(MenuKeys.MAIN_MENU, new IconParentMenuItem.Builder()
//                        .priority(11)
//                        .text("Item")
//                        .children(future)
//                        .build());
    }

    private Future<List<Item>> getNewMenuItems(final ExplorerNode explorerNode) {
        final FutureImpl<List<Item>> future = new FutureImpl<>();

        List<ExplorerNode> explorerNodes = Collections.emptyList();
        if (explorerNode != null) {
            explorerNodes = Collections.singletonList(explorerNode);
        }

        fetchPermissions(explorerNodes, documentPermissions ->
                documentTypeCache.fetch(documentTypes -> {
                    if (documentPermissions.containsKey(explorerNode)) {
                        future.setResult(createNewMenuItems(explorerNode,
                                documentPermissions.get(explorerNode),
                                documentTypes));
                    } else {
                        future.setResult(Collections.emptyList());
                    }
                }));
        return future;
    }

    private void fetchPermissions(final List<ExplorerNode> explorerNodes,
                                  final Consumer<Map<ExplorerNode, ExplorerNodePermissions>> consumer) {
        final Rest<Set<ExplorerNodePermissions>> rest = restFactory.create();
        rest
                .onSuccess(response -> {
                    final Map<ExplorerNode, ExplorerNodePermissions> map = response.stream().collect(Collectors.toMap(
                            ExplorerNodePermissions::getExplorerNode,
                            Function.identity()));
                    consumer.accept(map);
                })
                .call(EXPLORER_RESOURCE)
                .fetchExplorerPermissions(explorerNodes);
    }

//    private DocRef getDocRef(final ExplorerNode explorerNode) {
//        DocRef docRef = null;
//        if (explorerNode != null && explorerNode instanceof EntityData) {
//            final EntityData entityData = (EntityData) explorerNode;
//            docRef = entityData.getDocRef();
//        }
//        return docRef;
//    }

    private void addFavouritesMenuItem(final List<Item> menuItems, final boolean singleSelection, final int priority) {
        final ExplorerNode primarySelection = getPrimarySelection();

        // Add the favourites menu item if an item is selected, and it's not a root-level node or a favourite folder
        // item
        if (singleSelection && primarySelection != null && primarySelection.getDepth() > 0) {
            final boolean isFavourite = primarySelection.getIsFavourite();
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority)
                    .icon(isFavourite
                            ? SvgImage.FAVOURITES_OUTLINE
                            : SvgImage.FAVOURITES)
                    .text(isFavourite
                            ? "Remove from Favourites"
                            : "Add to Favourites")
                    .command(() -> {
                        toggleFavourite(primarySelection.getDocRef(), isFavourite);
                        selectionModel.clear();
                    })
                    .build());
        }
    }

    private void toggleFavourite(final DocRef docRef, final boolean isFavourite) {
        SetDocumentAsFavouriteEvent.fire(DocumentPluginEventManager.this, docRef, !isFavourite);
    }

    private void addNewMenuItem(final List<Item> menuItems,
                                final boolean singleSelection,
                                final Map<ExplorerNode, ExplorerNodePermissions> documentPermissionMap,
                                final ExplorerNode primarySelection,
                                final DocumentTypes documentTypes) {
        boolean enabled = false;
        List<Item> children = null;

        // Only allow the new menu to appear if we have a single selection.
        if (singleSelection && primarySelection != null) {
            // Add 'New' menu item.
            final ExplorerNodePermissions documentPermissions = documentPermissionMap.get(primarySelection);
            children = createNewMenuItems(primarySelection, documentPermissions,
                    documentTypes);
            enabled = children.size() > 0;
        }

        final Item newItem = new IconParentMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.ADD)
                .text("New")
                .children(children)
                .enabled(enabled)
                .build();
        menuItems.add(newItem);
        menuItems.add(new Separator(2));
    }

    private List<Item> createNewMenuItems(final ExplorerNode explorerNode,
                                          final ExplorerNodePermissions documentPermissions,
                                          final DocumentTypes documentTypes) {
        final List<Item> children = new ArrayList<>();
        //noinspection SimplifyStreamApiCallChains
        final List<DocumentType> availableTypes = documentTypes.getTypes()
                .stream()
                .filter(documentPermissions::hasCreatePermission)
                .collect(Collectors.toList());

        // Group all document types
        final Map<DocumentTypeGroup, List<DocumentType>> groupedTypes = availableTypes.stream()
                .collect(Collectors.groupingBy(DocumentType::getGroup, Collectors.toList()));

        // Add each type group as a sorted list of menu items
        groupedTypes
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getPriority()))
                .forEach(entry -> {
                    final DocumentTypeGroup group = entry.getKey();
                    final List<DocumentType> types = entry.getValue();
                    if (DocumentTypeGroup.STRUCTURE.equals(group) && types != null && types.size() == 1) {
                        children.add(createIconMenuItemFromDocumentType(types.get(0), explorerNode));
                        if (groupedTypes.keySet().size() > 1) {
                            children.add(new Separator(1));
                        }
                    } else if (types != null && !types.isEmpty()) {
                        final List<Item> grandChildren = types.stream()
                                .sorted(Comparator.comparing(DocumentType::getDisplayType))
                                .map(type -> (Item) createIconMenuItemFromDocumentType(type, explorerNode))
                                .collect(Collectors.toList());

                        // Add the group level item with its children
                        children.add(new IconParentMenuItem.Builder()
                                .text(group.getName())
                                .children(grandChildren)
                                .build());
                    }
                });
        return children;
    }

    private IconMenuItem createIconMenuItemFromDocumentType(
            final DocumentType documentType,
            final ExplorerNode explorerNode
    ) {
        final Consumer<ExplorerNode> newDocumentConsumer = newDocNode -> {
            final DocRef docRef = newDocNode.getDocRef();
            // Open the document in the content pane.
            final DocumentPlugin<?> plugin = documentPluginRegistry.get(docRef.getType());
            if (plugin != null) {
                plugin.open(docRef, true);
            }
        };

        return new IconMenuItem.Builder()
                .priority(1)
                .icon(documentType.getIcon())
                .text(documentType.getDisplayType())
                .command(() ->
                        ShowCreateDocumentDialogEvent.fire(
                                DocumentPluginEventManager.this,
                                "New " + documentType.getDisplayType(),
                                explorerNode,
                                documentType.getType(),
                                "",
                                true,
                                newDocumentConsumer))
                .build();
    }

    private void addModifyMenuItems(final List<Item> menuItems,
                                    final boolean singleSelection,
                                    final Map<ExplorerNode, ExplorerNodePermissions> documentPermissionMap) {
        final List<ExplorerNode> readableItems = getExplorerNodeListWithPermission(documentPermissionMap,
                DocumentPermissionNames.READ,
                false);
        final List<ExplorerNode> updatableItems = getExplorerNodeListWithPermission(documentPermissionMap,
                DocumentPermissionNames.UPDATE,
                false);
        final List<ExplorerNode> deletableItems = getExplorerNodeListWithPermission(documentPermissionMap,
                DocumentPermissionNames.DELETE,
                false);

        // Actions allowed based on permissions of selection
        final boolean allowRead = readableItems.size() > 0;
        final boolean allowUpdate = updatableItems.size() > 0;
        final boolean allowDelete = deletableItems.size() > 0;
        final boolean isInfoEnabled = singleSelection & allowRead;

        // Feeds are a special case so can't be renamed, see https://github.com/gchq/stroom/issues/2912
        final boolean isRenameEnabled = singleSelection
                && allowUpdate
                && !FeedDoc.DOCUMENT_TYPE.equals(updatableItems.get(0).getType());

        // Feeds are a special case so can't be copied, see https://github.com/gchq/stroom/issues/3048
        final boolean isCopyEnabled = singleSelection
                && allowRead
                && !FeedDoc.DOCUMENT_TYPE.equals(readableItems.get(0).getType());

        addFavouritesMenuItem(menuItems, singleSelection, 10);
        if (singleSelection && getPrimarySelection() != null &&
                !DocumentTypes.isSystem(getPrimarySelection().getType())) {
            menuItems.add(createCopyLinkMenuItem(getPrimarySelection(), 11));
            menuItems.add(new Separator(12));
        }

        menuItems.add(createInfoMenuItem(readableItems, 20, isInfoEnabled));
        menuItems.add(createCopyMenuItem(readableItems, 21, isCopyEnabled));
        menuItems.add(createMoveMenuItem(updatableItems, 22, allowUpdate));
        menuItems.add(createRenameMenuItem(updatableItems, 23, isRenameEnabled));
        menuItems.add(createDeleteMenuItem(deletableItems, 24, allowDelete));

        if (securityContext.hasAppPermission(PermissionNames.IMPORT_CONFIGURATION)) {
            menuItems.add(createImportMenuItem(25));
        }
        if (securityContext.hasAppPermission(PermissionNames.EXPORT_CONFIGURATION)) {
            menuItems.add(createExportMenuItem(26, readableItems));
        }

        // Only allow users to change permissions if they have a single item selected.
        if (singleSelection) {
            final List<ExplorerNode> ownedItems = getExplorerNodeListWithPermission(documentPermissionMap,
                    DocumentPermissionNames.OWNER,
                    true);
            if (ownedItems.size() == 1) {
                menuItems.add(new Separator(30));
                menuItems.add(createShowDependenciesMenuItem(ownedItems.get(0), 31));
                menuItems.add(new Separator(32));
                menuItems.add(createPermissionsMenuItem(ownedItems.get(0), 33, true));
            }
        }
    }

    private MenuItem createCloseMenuItem(final int priority, final TabData selectedTab) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.CLOSE)
                .iconColour(IconColour.RED)
                .text("Close")
                .action(Action.ITEM_CLOSE)
                .enabled(isTabItemSelected(selectedTab))
                .command(() -> RequestCloseTabEvent.fire(DocumentPluginEventManager.this, selectedTab))
                .build();
    }

    private MenuItem createCloseOthersMenuItem(final int priority, final TabData selectedTab) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.CLOSE)
                .iconColour(IconColour.RED)
                .text("Close Others")
                .enabled(isTabItemSelected(selectedTab))
                .command(() -> RequestCloseOtherTabsEvent.fire(DocumentPluginEventManager.this, selectedTab))
                .build();
    }

    private MenuItem createCloseSavedMenuItem(final int priority, final TabData selectedTab) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.CLOSE)
                .iconColour(IconColour.RED)
                .text("Close Saved")
                .enabled(isTabItemSelected(selectedTab))
                .command(() -> RequestCloseSavedTabsEvent.fire(DocumentPluginEventManager.this))
                .build();
    }

    private MenuItem createCloseAllMenuItem(final int priority, final TabData selectedTab) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.CLOSE)
                .iconColour(IconColour.RED)
                .text("Close All")
                .action(Action.ITEM_CLOSE_ALL)
                .enabled(isTabItemSelected(selectedTab))
                .command(() -> RequestCloseAllTabsEvent.fire(DocumentPluginEventManager.this))
                .build();
    }

    private MenuItem createSaveMenuItem(final int priority, final TabData selectedTab) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.SAVE)
                .text("Save")
                .action(Action.ITEM_SAVE)
                .enabled(isDirty(selectedTab))
                .command(() -> {
                    if (isDirty(selectedTab)) {
                        final HasSave hasSave = (HasSave) selectedTab;
                        hasSave.save();
                    }
                })
                .build();
    }

    private MenuItem createSaveAllMenuItem(final int priority) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.SAVE)
                .text("Save All")
                .action(Action.ITEM_SAVE_ALL)
                .enabled(hasSaveRegistry.isDirty())
                .command(hasSaveRegistry::save)
                .build();
    }

    private MenuItem createInfoMenuItem(final List<ExplorerNode> explorerNodeList,
                                        final int priority,
                                        final boolean enabled) {
        final Command command = () ->
                explorerNodeList.forEach(explorerNode -> {
                    final Rest<DocRefInfo> rest = restFactory.create();
                    rest
                            .onSuccess(s ->
                                    ShowInfoDocumentDialogEvent.fire(DocumentPluginEventManager.this, s))
                            .onFailure(t ->
                                    AlertEvent.fireError(
                                            DocumentPluginEventManager.this,
                                            t.getMessage(),
                                            null))
                            .call(EXPLORER_RESOURCE)
                            .info(explorerNode.getDocRef());
                });

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.INFO)
                .text("Info")
                .enabled(enabled)
                .command(command)
                .build();
    }

    private MenuItem createCopyLinkMenuItem(final ExplorerNode explorerNode, final int priority) {
        // Generate a URL that can be used to open a new Stroom window with the target document loaded
        final String docUrl = Window.Location.createUrlBuilder()
                .setPath("/")
                .setParameter(UrlConstants.ACTION, ExplorerConstants.OPEN_DOC_ACTION)
                .setParameter(ExplorerConstants.DOC_TYPE_QUERY_PARAM, explorerNode.getType())
                .setParameter(ExplorerConstants.DOC_UUID_QUERY_PARAM, explorerNode.getUuid())
                .buildString();

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.SHARE)
                .text("Copy Link to Clipboard")
                .command(() -> ClipboardUtil.copy(docUrl))
                .build();
    }

    private MenuItem createCopyMenuItem(final List<ExplorerNode> explorerNodeList,
                                        final int priority,
                                        final boolean enabled) {
        final Command command = () -> ShowCopyDocumentDialogEvent.fire(DocumentPluginEventManager.this,
                explorerNodeList);

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.COPY)
                .text("Copy")
                .enabled(enabled)
                .command(command)
                .build();
    }

    private MenuItem createMoveMenuItem(final List<ExplorerNode> explorerNodeList,
                                        final int priority,
                                        final boolean enabled) {
        final Command command = () -> ShowMoveDocumentDialogEvent.fire(DocumentPluginEventManager.this,
                explorerNodeList);

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.MOVE)
                .text("Move")
                .enabled(enabled)
                .command(command)
                .build();
    }

    private MenuItem createRenameMenuItem(final List<ExplorerNode> explorerNodeList,
                                          final int priority,
                                          final boolean enabled) {
        final Command command = () ->
                renameItems(explorerNodeList);

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.EDIT)
                .text("Rename")
                .enabled(enabled)
                .command(command)
                .build();
    }

    private MenuItem createDeleteMenuItem(final List<ExplorerNode> explorerNodeList,
                                          final int priority,
                                          final boolean enabled) {
        final Command command = () ->
                deleteItems(explorerNodeList);

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.DELETE)
                .text("Delete")
                .enabled(enabled)
                .command(command)
                .build();
    }

    private MenuItem createPermissionsMenuItem(final ExplorerNode explorerNode,
                                               final int priority,
                                               final boolean enabled) {
        final Command command = () -> {
            if (explorerNode != null) {
                ShowPermissionsDialogEvent.fire(DocumentPluginEventManager.this, explorerNode);
            }
        };

        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.LOCKED)
                .text("Permissions")
                .enabled(enabled)
                .command(command)
                .build();
    }

    private MenuItem createImportMenuItem(final int priority) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.UPLOAD)
                .text("Import")
                .command(() -> ImportConfigEvent.fire(DocumentPluginEventManager.this))
                .build();
    }

    private MenuItem createExportMenuItem(final int priority,
                                          final List<ExplorerNode> readableItems) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.DOWNLOAD)
                .text("Export")
                .command(() -> ExportConfigEvent.fire(DocumentPluginEventManager.this, readableItems))
                .build();
    }

    private MenuItem createShowDependenciesMenuItem(final ExplorerNode explorerNode, final int priority) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(SvgImage.DEPENDENCIES)
                .text("Dependencies")
                .command(() -> ShowDocRefDependenciesEvent.fire(DocumentPluginEventManager.this,
                        explorerNode.getDocRef()))
                .build();
    }

    void registerPlugin(final String entityType, final DocumentPlugin<?> plugin) {
        documentPluginRegistry.register(entityType, plugin);
        hasSaveRegistry.register(plugin);
    }

    private boolean isTabItemSelected(final TabData tabData) {
        return tabData != null;
    }

    public boolean isTabSelected() {
        return selectedTab != null;
    }

    private boolean isDirty(final TabData tabData) {
        if (tabData instanceof HasSave) {
            final HasSave hasSave = (HasSave) tabData;
            return hasSave.isDirty();
        }

        return false;
    }

    private List<ExplorerNode> getSelectedItems() {
        if (selectionModel == null) {
            return Collections.emptyList();
        }

        return selectionModel.getSelectedItems();
    }

    private ExplorerNode getPrimarySelection() {
        if (selectionModel == null) {
            return null;
        }

        return selectionModel.getSelected();
    }
}
