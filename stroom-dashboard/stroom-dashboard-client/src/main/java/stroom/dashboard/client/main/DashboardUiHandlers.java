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

package stroom.dashboard.client.main;

import stroom.query.api.v2.TimeRange;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.gwtplatform.mvp.client.UiHandlers;

public interface DashboardUiHandlers extends UiHandlers {
    void onAddPanel(ClickEvent event);

    void onAddInput(ClickEvent event);

    void onConstraints(ClickEvent event);

    void onDesign(ClickEvent event);

    void onTimeRange(TimeRange timeRange);
}
