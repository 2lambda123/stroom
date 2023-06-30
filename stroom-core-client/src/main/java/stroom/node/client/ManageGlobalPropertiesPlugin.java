package stroom.node.client;

import stroom.config.global.client.presenter.GlobalPropertyTabPresenter;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ManageGlobalPropertiesPlugin extends NodeToolsContentPlugin<GlobalPropertyTabPresenter> {

    @Inject
    ManageGlobalPropertiesPlugin(final EventBus eventBus,
                                 final ContentManager contentManager,
                                 final Provider<GlobalPropertyTabPresenter> presenterProvider,
                                 final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, presenterProvider, securityContext);
    }

    @Override
    protected void addChildItems(final BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_PROPERTIES_PERMISSION)) {
            event.getMenuItems().addMenuItem(
                    MenuKeys.TOOLS_MENU,
                    new IconMenuItem.Builder()
                            .priority(90)
                            .icon(SvgImage.PROPERTIES)
                            .text("Properties")
                            .command(super::open)
                            .build());
        }

    }
}
