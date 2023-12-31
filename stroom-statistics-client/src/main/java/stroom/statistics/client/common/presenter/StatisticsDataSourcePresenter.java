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
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.EntityEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.security.client.ClientSecurityContext;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

public class StatisticsDataSourcePresenter extends EntityEditTabPresenter<LinkTabPanelView, StatisticStoreEntity> {
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData CUSTOM_ROLLUPS = new TabDataImpl("Custom Roll-ups");

    private final TabContentProvider<StatisticStoreEntity> tabContentProvider = new TabContentProvider<StatisticStoreEntity>();

    @Inject
    public StatisticsDataSourcePresenter(final EventBus eventBus, final LinkTabPanelView view,
                                         final Provider<StatisticsDataSourceSettingsPresenter> statisticsDataSourceSettingsPresenter,
                                         final Provider<StatisticsFieldListPresenter> statisticsFieldListPresenter,
                                         final Provider<StatisticsCustomMaskListPresenter> statisticsCustomMaskListPresenter,
                                         final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);

        tabContentProvider.setDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        tabContentProvider.add(SETTINGS, statisticsDataSourceSettingsPresenter);
        tabContentProvider.add(FIELDS, statisticsFieldListPresenter);
        tabContentProvider.add(CUSTOM_ROLLUPS, statisticsCustomMaskListPresenter);

        addTab(SETTINGS);
        addTab(FIELDS);
        addTab(CUSTOM_ROLLUPS);

        selectTab(SETTINGS);

    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab));
    }

    @Override
    public void onRead(final StatisticStoreEntity statisticsDataSource) {
        if (statisticsDataSource != null) {
            if (statisticsDataSource.getStatisticDataSourceDataObject() == null) {
                statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData());
            }
        }

        tabContentProvider.read(statisticsDataSource);

        // the field and rollup presenters need to know about each other as
        // changes in one affect the other
        ((StatisticsFieldListPresenter) tabContentProvider.getPresenter(FIELDS)).setCustomMaskListPresenter(
                (StatisticsCustomMaskListPresenter) tabContentProvider.getPresenter(CUSTOM_ROLLUPS));
    }

    @Override
    protected void onWrite(final StatisticStoreEntity statisticsDataSource) {
        tabContentProvider.write(statisticsDataSource);
    }

    @Override
    public void onPermissionsCheck(final boolean readOnly) {
        super.onPermissionsCheck(readOnly);
        tabContentProvider.onPermissionsCheck(readOnly);
    }

    @Override
    public String getType() {
        return StatisticStoreEntity.ENTITY_TYPE;
    }
}
