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

package stroom.node.client;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.app.client.MenuKeys;
import stroom.app.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.menu.client.presenter.KeyedParentMenuItem;

public abstract class NodeToolsPlugin extends Plugin {
    private final ClientSecurityContext securityContext;

    @Inject
    public NodeToolsPlugin(final EventBus eventBus, final ClientSecurityContext securityContext) {
        super(eventBus);
        this.securityContext = securityContext;
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        super.onReveal(event);

        event.getMenuItems().addMenuItem(MenuKeys.MAIN_MENU,
                new KeyedParentMenuItem(2, "Tools", event.getMenuItems(), MenuKeys.TOOLS_MENU));

        addChildItems(event);
    }

    protected ClientSecurityContext getSecurityContext() {
        return securityContext;
    }

    protected abstract void addChildItems(BeforeRevealMenubarEvent event);
}
