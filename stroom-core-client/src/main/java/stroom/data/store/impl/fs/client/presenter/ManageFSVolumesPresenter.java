/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.store.impl.fs.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.grid.client.WrapperView;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.svg.client.IconColour;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class ManageFSVolumesPresenter extends ContentTabPresenter<WrapperView> {

    private static final FsVolumeResource FS_VOLUME_RESOURCE = GWT.create(FsVolumeResource.class);

    private final FSVolumeStatusListPresenter volumeStatusListPresenter;
    private final Provider<FSVolumeEditPresenter> editProvider;
    private final RestFactory restFactory;

    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;
    private final ButtonView rescanButton;

    @Inject
    public ManageFSVolumesPresenter(final EventBus eventBus,
                                    final WrapperView view,
                                    final FSVolumeStatusListPresenter volumeStatusListPresenter,
                                    final Provider<FSVolumeEditPresenter> editProvider,
                                    final RestFactory restFactory) {
        super(eventBus, view);
        this.volumeStatusListPresenter = volumeStatusListPresenter;
        this.editProvider = editProvider;
        this.restFactory = restFactory;

        newButton = volumeStatusListPresenter.getView().addButton(SvgPresets.NEW_ITEM);
        openButton = volumeStatusListPresenter.getView().addButton(SvgPresets.EDIT);
        deleteButton = volumeStatusListPresenter.getView().addButton(SvgPresets.DELETE);
        rescanButton = volumeStatusListPresenter.getView().addButton(SvgPresets.REFRESH_GREEN);
        rescanButton.setTitle("Rescan Public Volumes");

        view.setView(volumeStatusListPresenter.getView());
    }

    @Override
    protected void onBind() {
        registerHandler(volumeStatusListPresenter.getSelectionModel().addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
        registerHandler(newButton.addClickHandler(event -> add()));
        registerHandler(openButton.addClickHandler(event -> edit()));
        registerHandler(deleteButton.addClickHandler(event -> delete()));
        registerHandler(rescanButton.addClickHandler(event -> {
            final Rest<Boolean> rest = restFactory.create();
            rest.onSuccess(response -> refresh()).call(FS_VOLUME_RESOURCE).rescan();
        }));
    }

    private void add() {
        final FSVolumeEditPresenter editor = editProvider.get();
        editor.show(new FsVolume(), "Add Volume", added -> {
            if (added != null) {
                refresh();
            }
            editor.hide();
        });
    }

    private void edit() {
        final FsVolume volume = volumeStatusListPresenter.getSelectionModel().getSelected();
        if (volume != null) {
            final Rest<FsVolume> rest = restFactory.create();
            rest
                    .onSuccess(this::edit)
                    .call(FS_VOLUME_RESOURCE)
                    .fetch(volume.getId());
        }
    }

    private void edit(final FsVolume volume) {
        final FSVolumeEditPresenter editor = editProvider.get();
        editor.show(volume, "Edit Volume", result -> {
            if (result != null) {
                refresh();
            }
            editor.hide();
        });
    }

    private void delete() {
        final List<FsVolume> list = volumeStatusListPresenter.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected volume?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected volumes?";
            }
            ConfirmEvent.fire(ManageFSVolumesPresenter.this, message,
                    result -> {
                        if (result) {
                            volumeStatusListPresenter.getSelectionModel().clear();
                            for (final FsVolume volume : list) {
                                final Rest<Boolean> rest = restFactory.create();
                                rest.onSuccess(response -> refresh()).call(FS_VOLUME_RESOURCE).delete(volume.getId());
                            }
                        }
                    });
        }
    }

//    @Override
//    protected void revealInParent() {
//        final PopupSize popupSize = PopupSize.resizable(1000, 600);
//        ShowPopupEvent.fire(this, this, PopupType.CLOSE_DIALOG, null, popupSize, "Manage Volumes", null, null);
//    }

    private void enableButtons() {
        final boolean enabled = volumeStatusListPresenter.getSelectionModel().getSelected() != null;
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    public void refresh() {
        volumeStatusListPresenter.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.VOLUMES;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Data Volumes";
    }
}
