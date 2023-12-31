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

package stroom.dashboard.client.unknown;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ResultComponent;
import stroom.dashboard.shared.ComponentConfig;
import stroom.query.shared.ComponentResultRequest;
import stroom.query.shared.ComponentSettings;
import stroom.util.shared.SharedObject;

public class UnknownComponentPresenter extends AbstractComponentPresenter<HTMLView>implements ResultComponent {
    private static final ComponentType TYPE = new ComponentType(99, "Unknown", "Unknown");

    @Inject
    public UnknownComponentPresenter(final EventBus eventBus, final HTMLView view) {
        super(eventBus, view, null);
        view.setHTML("<div style=\"padding:5px\">Unknown component</div>");
    }

    @Override
    public ComponentType getType() {
        return TYPE;
    }

    @Override
    public void link() {
    }

    @Override
    public ComponentSettings getSettings() {
        return null;
    }

    @Override
    public void read(final ComponentConfig componentData) {
        super.read(componentData);
        getView().setHTML("<div style=\"padding:5px\">Unknown component type: " + componentData.getType() + "</div>");
    }

    @Override
    public void changeSettings() {
        super.changeSettings();

    }

    @Override
    public ComponentResultRequest getResultRequest() {
        return null;
    }

    @Override
    public void reset() {
    }

    @Override
    public void startSearch() {
    }

    @Override
    public void endSearch() {
    }

    @Override
    public void setWantsData(final boolean wantsData) {
    }

    @Override
    public void setData(final SharedObject result) {
    }
}
