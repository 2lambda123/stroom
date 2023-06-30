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

package stroom.explorer.client.presenter;

import stroom.activity.client.ActivityChangedEvent;
import stroom.activity.client.CurrentActivity;
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.activity.shared.Activity.Prop;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPluginEventManager;
import stroom.explorer.client.event.ExplorerTreeDeleteEvent;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.event.HighlightExplorerNodeEvent;
import stroom.explorer.client.event.OpenExplorerTabEvent;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.client.event.ShowNewMenuEvent;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.main.client.event.UrlQueryParameterChangeEvent;
import stroom.main.client.event.UrlQueryParameterChangeEvent.UrlQueryParameterChangeHandler;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.security.client.api.event.CurrentUserChangedEvent.CurrentUserChangedHandler;
import stroom.security.shared.DocumentPermissionNames;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ActivityConfig;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.Map;

public class ExplorerTreePresenter
        extends MyPresenter<ExplorerTreePresenter.ExplorerTreeView, ExplorerTreePresenter.ExplorerTreeProxy>
        implements ExplorerTreeUiHandlers, RefreshExplorerTreeEvent.Handler, HighlightExplorerNodeEvent.Handler,
        CurrentUserChangedHandler, UrlQueryParameterChangeHandler, TabData {

    private static final String EXPLORER = "Explorer";

    private final DocumentTypeCache documentTypeCache;
    private final TypeFilterPresenter typeFilterPresenter;
    private final CurrentActivity currentActivity;
    private final DocumentPluginEventManager entityPluginEventManager;
    private final ExplorerTree explorerTree;
    private final Button activityContainer = new Button();

    @Inject
    public ExplorerTreePresenter(final EventBus eventBus,
                                 final ExplorerTreeView view,
                                 final ExplorerTreeProxy proxy,
                                 final RestFactory restFactory,
                                 final DocumentTypeCache documentTypeCache,
                                 final TypeFilterPresenter typeFilterPresenter,
                                 final CurrentActivity currentActivity,
                                 final UiConfigCache uiConfigCache,
                                 final DocumentPluginEventManager entityPluginEventManager) {
        super(eventBus, view, proxy);
        this.documentTypeCache = documentTypeCache;
        this.typeFilterPresenter = typeFilterPresenter;
        this.currentActivity = currentActivity;
        this.entityPluginEventManager = entityPluginEventManager;

        view.setUiHandlers(this);

        explorerTree = new ExplorerTree(restFactory, true) {
            @Override
            protected void doSelect(final ExplorerNode row, final SelectionType selectionType) {
                super.doSelect(row, selectionType);
                getView().setDeleteEnabled(explorerTree.getSelectionModel().getSelectedItems().size() > 0 &&
                        !ExplorerConstants.isSystemNode(row) &&
                        !ExplorerConstants.isFavouritesNode(row));
            }
        };

        // Add views.
        uiConfigCache.get().onSuccess(uiConfig -> {
            final ActivityConfig activityConfig = uiConfig.getActivity();
            if (activityConfig.isEnabled()) {
                activityContainer.setStyleName("activityContainer");

                final SimplePanel activityOuter = new SimplePanel();
                activityOuter.setStyleName("dock-min activityOuter");
                activityOuter.setWidget(activityContainer);

                final SimplePanel treeContainer = new SimplePanel();
                treeContainer.setStyleName("dock-max stroom-content");
                treeContainer.setWidget(explorerTree);

                final FlowPanel explorerWrapper = new FlowPanel();
                explorerWrapper.setStyleName("dock-container-vertical explorerWrapper");
                explorerWrapper.add(treeContainer);
                explorerWrapper.add(activityOuter);

                view.setCellTree(explorerWrapper);

                updateActivitySummary();

            } else {
                view.setCellTree(explorerTree);
            }
        });
    }

    @Override
    protected void onBind() {
        super.onBind();

        // Register for refresh events.
        registerHandler(getEventBus().addHandler(RefreshExplorerTreeEvent.getType(), this));

        // Register for changes to the current activity.
        registerHandler(getEventBus().addHandler(ActivityChangedEvent.getType(), event -> updateActivitySummary()));

        // Register for highlight events.
        registerHandler(getEventBus().addHandler(HighlightExplorerNodeEvent.getType(), this));

        registerHandler(typeFilterPresenter.addDataSelectionHandler(event -> explorerTree.setIncludedTypeSet(
                typeFilterPresenter.getIncludedTypes())));

        // Fire events from the explorer tree globally.
        registerHandler(explorerTree.getSelectionModel().addSelectionHandler(event ->
                getEventBus().fireEvent(new ExplorerTreeSelectEvent(
                        explorerTree.getSelectionModel(),
                        event.getSelectionType()))));
        registerHandler(explorerTree.addContextMenuHandler(event -> getEventBus().fireEvent(event)));

        registerHandler(activityContainer.addClickHandler(event -> currentActivity.showActivityChooser()));
    }

    private void updateActivitySummary() {
        currentActivity.getActivity(activity -> {
            final StringBuilder sb = new StringBuilder("<h2>Current Activity</h2>");

            if (activity != null) {
                final ActivityDetails activityDetails = activity.getDetails();
                for (final Prop prop : activityDetails.getProperties()) {
                    if (prop.isShowInSelection()) {
                        sb.append("<b>");
                        sb.append(prop.getName());
                        sb.append(": </b>");
                        sb.append(prop.getValue());
                        sb.append("</br>");
                    }
                }
            } else {
                sb.append("<b>");
                sb.append("none");
            }

            activityContainer.setHTML(sb.toString());
        });
    }

    @Override
    public void newItem(final NativeEvent event) {
        final Element element = event.getEventTarget().cast();
        final int x = element.getAbsoluteLeft() - 1;
        final int y = element.getAbsoluteTop() + element.getOffsetHeight() + 1;
        final PopupPosition popupPosition = new PopupPosition(x, y);
        ShowNewMenuEvent.fire(this, element, popupPosition);
    }

    @Override
    public void deleteItem() {
        if (explorerTree.getSelectionModel().getSelectedItems().size() > 0) {
            ExplorerTreeDeleteEvent.fire(this);
        }
    }

    @Override
    public void changeQuickFilter(final String name) {
        explorerTree.changeNameFilter(name);
    }

    @Override
    public void showTypeFilter(final NativeEvent event) {
        final Element target = event.getEventTarget().cast();
        typeFilterPresenter.show(target);
    }

    @ProxyEvent
    @Override
    public void onCurrentUserChanged(final CurrentUserChangedEvent event) {
        documentTypeCache.clear();
        // Set the data for the type filter.
        documentTypeCache.fetch(typeFilterPresenter::setDocumentTypes);

        explorerTree.getTreeModel().reset();
        explorerTree.getTreeModel().setRequiredPermissions(DocumentPermissionNames.READ);
        explorerTree.getTreeModel().setIncludedTypeSet(null);

        // Show the tree.
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        explorerTree.getTreeModel().refresh();
        OpenExplorerTabEvent.fire(this, this, this);
    }

    @Override
    public void onHighlight(final HighlightExplorerNodeEvent event) {
        explorerTree.setSelectedItem(event.getExplorerNode());
        explorerTree.getTreeModel().setEnsureVisible(event.getExplorerNode());
        explorerTree.getTreeModel().refresh();
    }

    @Override
    public void onRefresh(final RefreshExplorerTreeEvent event) {
        explorerTree.getTreeModel().refresh();
    }

    public void refresh() {
        explorerTree.getTreeModel().refresh();
    }

    @Override
    public String getLabel() {
        return EXPLORER;
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.FOLDER_TREE;
    }

    @Override
    public boolean isCloseable() {
        return false;
    }

    @ProxyEvent
    @Override
    public void onChange(final UrlQueryParameterChangeEvent event) {
        final Map<String, String> queryParams = event.getQueryParams();
        final DocRef docRef = DocRef.builder()
                .type(queryParams.get(ExplorerConstants.DOC_TYPE_QUERY_PARAM))
                .uuid(queryParams.get(ExplorerConstants.DOC_UUID_QUERY_PARAM))
                .build();

        if (ExplorerConstants.OPEN_DOC_ACTION.equals(event.getAction()) &&
                docRef.getType() != null &&
                docRef.getUuid() != null) {
            entityPluginEventManager.open(docRef, true);
            entityPluginEventManager.highlight(docRef);
        }
    }

    public interface ExplorerTreeView extends View, HasUiHandlers<ExplorerTreeUiHandlers> {

        void setCellTree(Widget widget);

        void setDeleteEnabled(boolean enable);
    }

    @ProxyCodeSplit
    public interface ExplorerTreeProxy extends Proxy<ExplorerTreePresenter> {

    }
}
