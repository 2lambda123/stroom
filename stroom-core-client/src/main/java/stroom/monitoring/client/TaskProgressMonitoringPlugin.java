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

package stroom.monitoring.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.app.client.ContentManager;
import stroom.app.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.monitoring.client.presenter.TaskProgressMonitoringPresenter;
import stroom.security.client.ClientSecurityContext;
import stroom.task.shared.FindTaskProgressAction;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class TaskProgressMonitoringPlugin extends MonitoringPlugin<TaskProgressMonitoringPresenter> {
    @Inject
    public TaskProgressMonitoringPlugin(final EventBus eventBus, final ContentManager eventManager,
                                        final Provider<TaskProgressMonitoringPresenter> presenterProvider,
                                        final ClientSecurityContext securityContext) {
        super(eventBus, eventManager, presenterProvider, securityContext);
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(FindTaskProgressAction.MANAGE_TASKS_PERMISSION)) {
            event.getMenuItems().addMenuItem(MenuKeys.MONITORING_MENU,
                    new IconMenuItem(13, GlyphIcons.JOBS, GlyphIcons.JOBS, "Server Tasks", null, true, () -> open()));
        }
    }
}
