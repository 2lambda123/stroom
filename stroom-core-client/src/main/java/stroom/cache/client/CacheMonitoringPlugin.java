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

package stroom.cache.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.app.client.ContentManager;
import stroom.app.client.MenuKeys;
import stroom.cache.client.presenter.CachePresenter;
import stroom.cache.shared.CacheRow;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.monitoring.client.MonitoringPlugin;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class CacheMonitoringPlugin extends MonitoringPlugin<CachePresenter> {
    @Inject
    public CacheMonitoringPlugin(final EventBus eventBus, final ContentManager eventManager,
                                 final Provider<CachePresenter> presenterProvider, final ClientSecurityContext securityContext) {
        super(eventBus, eventManager, presenterProvider, securityContext);
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(CacheRow.MANAGE_CACHE_PERMISSION)) {
            event.getMenuItems().addMenuItem(MenuKeys.MONITORING_MENU,
                    new IconMenuItem(12, GlyphIcons.MONITORING, GlyphIcons.MONITORING, "Caches", null, true, () -> open()));
        }
    }
}
