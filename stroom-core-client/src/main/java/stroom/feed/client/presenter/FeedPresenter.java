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

package stroom.feed.client.presenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.EntityEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.feed.shared.Feed;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.client.presenter.ClassificationWrappedStreamPresenter;
import stroom.streamstore.client.presenter.StreamTaskPresenter;
import stroom.streamstore.shared.Stream;
import stroom.streamtask.shared.StreamProcessor;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

public class FeedPresenter extends EntityEditTabPresenter<LinkTabPanelView, Feed> {
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DATA = new TabDataImpl("Data");
    // private static final Tab MONITORING = new Tab("Monitoring");
    private static final TabData TASKS = new TabDataImpl("Active Tasks");

    private final TabContentProvider<Feed> tabContentProvider = new TabContentProvider<Feed>();

    @Inject
    public FeedPresenter(final EventBus eventBus, final ClientSecurityContext securityContext,
                         final LinkTabPanelView view,
                         final Provider<FeedSettingsPresenter> settingsPresenterProvider,
                         final Provider<ClassificationWrappedStreamPresenter> streamPresenterProvider,
                         final Provider<StreamTaskPresenter> streamTaskPresenterProvider) {
        super(eventBus, view, securityContext);

        tabContentProvider.setDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(SETTINGS);
        tabContentProvider.add(SETTINGS, settingsPresenterProvider);

        if (securityContext.hasAppPermission(Stream.VIEW_DATA_PERMISSION)) {
            addTab(DATA);
            tabContentProvider.add(DATA, streamPresenterProvider);
        }

        if (securityContext.hasAppPermission(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)) {
            addTab(TASKS);
            tabContentProvider.add(TASKS, streamTaskPresenterProvider);
        }

        selectTab(SETTINGS);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab));
    }

    @Override
    public void onRead(final Feed feed) {
        tabContentProvider.read(feed);
    }

    @Override
    protected void onWrite(final Feed feed) {
        tabContentProvider.write(feed);
    }

    @Override
    public void onPermissionsCheck(final boolean readOnly) {
        super.onPermissionsCheck(readOnly);
        tabContentProvider.onPermissionsCheck(readOnly);
    }

    @Override
    public String getType() {
        return Feed.ENTITY_TYPE;
    }
}
