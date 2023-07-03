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

package stroom.dashboard.client.flexlayout;

import stroom.dashboard.client.main.Component;
import stroom.dashboard.shared.DashboardConfig.TabVisibility;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.LayerContainerImpl;
import stroom.widget.tab.client.view.LinkTabBar;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HandlerRegistrations;
import com.gwtplatform.mvp.client.LayerContainer;

public class TabLayout extends Composite implements RequiresResize, ProvidesResize {

    private final EventBus eventBus;
    private final TabLayoutConfig tabLayoutConfig;
    private final FlexLayoutChangeHandler changeHandler;
    private final InlineSvgButton settings;
    private final InlineSvgButton close;
    private final LinkTabBar tabBar;
    private final LayerContainer layerContainer;
    private final HandlerRegistrations handlerRegistrations = new HandlerRegistrations();

    private TabVisibility tabVisibility = TabVisibility.SHOW_ALL;
    private boolean tabsVisible = true;

    public TabLayout(final EventBus eventBus,
                     final TabLayoutConfig tabLayoutConfig,
                     final FlexLayoutChangeHandler changeHandler) {
        this.eventBus = eventBus;

        this.tabLayoutConfig = tabLayoutConfig;
        this.changeHandler = changeHandler;

        tabBar = new LinkTabBar();
        tabBar.addStyleName("dock-max tabLayout-tabBar");

        final FlowPanel tabContainer = new FlowPanel();
        tabContainer.setStyleName("tabLayout-tabContainer");
        tabContainer.add(tabBar);

        final FlowPanel barOuter = new FlowPanel();
        barOuter.setStyleName("tabLayout-barOuter");
        barOuter.add(tabContainer);

        final FlowPanel contentInner = new FlowPanel();
        contentInner.setStyleName("tabLayout-contentInner");
        contentInner.add(barOuter);

        final FlowPanel contentOuter = new FlowPanel();
        contentOuter.setStyleName("tabLayout-contentOuter dashboard-panel");
        contentOuter.add(contentInner);

        final FlowPanel panel = new FlowPanel();
        panel.addStyleName("tabLayout");
        panel.add(contentOuter);
        initWidget(panel);

        final FlowPanel buttons = new FlowPanel();
        buttons.setStyleName("dock-min button-container icon-button-group tabLayout-buttons icon-colour__grey");
        barOuter.add(buttons);

        settings = new InlineSvgButton();
        settings.addStyleName("tabLayout-settingsButton");
        settings.setSvg(SvgImage.SETTINGS);
        settings.setTitle("Settings");
        buttons.add(settings);

        close = new InlineSvgButton();
        close.addStyleName("tabLayout-closeButton");
        close.setSvg(SvgImage.CLOSE);
        close.setTitle("Close");
        buttons.add(close);

        final LayerContainerImpl layerContainerImpl = new LayerContainerImpl();
        layerContainerImpl.setFade(true);
        layerContainerImpl.setStyleName("tabLayout-content");
        contentInner.add(layerContainerImpl);

        layerContainer = layerContainerImpl;

        bind();
    }

    public void bind() {
        handlerRegistrations.add(tabBar.addSelectionHandler(event -> {
            final TabData selected = event.getSelectedItem();
            selectTab(selected);
            final int index = tabBar.getTabs().indexOf(selected);
            getTabLayoutConfig().setSelected(index);
            changeHandler.onDirty();
        }));
        handlerRegistrations.add(tabBar.addShowMenuHandler(eventBus::fireEvent));
        handlerRegistrations.add(settings.addDomHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                final TabData selectedTab = tabBar.getSelectedTab();
                if (selectedTab instanceof Component) {
                    final Component component = (Component) selectedTab;
                    component.showSettings();
                }
            }
        }, ClickEvent.getType()));

        handlerRegistrations.add(close.addDomHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                final TabData selectedTab = tabBar.getSelectedTab();
                if (selectedTab instanceof Component) {
                    final Component component = (Component) selectedTab;
                    changeHandler.removeTab(tabLayoutConfig, component.getTabConfig());
                }
            }
        }, ClickEvent.getType()));
    }

    public void unbind() {
        handlerRegistrations.removeHandler();
    }

    public void addTab(final TabConfig tabConfig, final Component component) {
        tabBar.addTab(component);

        component.setTabLayout(this);
        component.setTabConfig(tabConfig);

        checkTabVisibility();

        layerContainer.show(component);
    }

    public void selectTab(final int index) {
        if (index >= 0 && getTabBar().getTabs().size() > index) {
            final TabData tabData = getTabBar().getTabs().get(index);
            selectTab(tabData);
        }
    }

    public void selectTab(final TabData tabData) {
        tabBar.selectTab(tabData);
        Component component = null;
        if (tabData instanceof Component) {
            component = (Component) tabData;
        }

        layerContainer.show(component);
    }

    public void refresh() {
        tabBar.refresh();
        onResize();
    }

    @Override
    public void onResize() {
        tabBar.onResize();
        layerContainer.onResize();
    }

    public void clear() {
        layerContainer.clear();
    }

    public LinkTabBar getTabBar() {
        return tabBar;
    }

    public TabLayoutConfig getTabLayoutConfig() {
        return tabLayoutConfig;
    }

    public void setTabVisibility(final TabVisibility tabVisibility) {
        this.tabVisibility = tabVisibility;
        checkTabVisibility();
    }

    private void checkTabVisibility() {
        if (tabVisibility == TabVisibility.SHOW_ALL) {
            setTabsVisibile(true);
        } else if (tabVisibility == TabVisibility.HIDE_ALL) {
            setTabsVisibile(false);
        } else if (tabVisibility == TabVisibility.HIDE_SINGLE) {
            setTabsVisibile(tabBar.getTabs().size() > 1);
        }
    }

    private void setTabsVisibile(final boolean tabsVisible) {
        if (this.tabsVisible != tabsVisible) {
            this.tabsVisible = tabsVisible;
        }
    }
}
