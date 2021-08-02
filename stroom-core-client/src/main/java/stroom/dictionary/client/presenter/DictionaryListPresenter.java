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

package stroom.dictionary.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.data.grid.client.WrapperView;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.explorer.client.presenter.EntityChooser;
import stroom.security.shared.DocumentPermissionNames;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;

public class DictionaryListPresenter extends MyPresenterWidget<WrapperView>
        implements HasDocumentRead<DictionaryDoc>, HasWrite<DictionaryDoc>, HasDirtyHandlers, ReadOnlyChangeHandler {

    private final DocRefListPresenter docRefListPresenter;
    private final Provider<EntityChooser> dictionarySelection;
    private final ButtonView addButton;
    private final ButtonView removeButton;

    private List<DocRef> imports;
    private DocRef currentDoc;
    private boolean readOnly = true;

    @Inject
    public DictionaryListPresenter(final EventBus eventBus,
                                   final WrapperView view,
                                   final DocRefListPresenter docRefListPresenter,
                                   final Provider<EntityChooser> dictionarySelection) {
        super(eventBus, view);
        this.docRefListPresenter = docRefListPresenter;
        this.dictionarySelection = dictionarySelection;

        view.setView(docRefListPresenter.getView());

        addButton = docRefListPresenter.getView().addButton(SvgPresets.ADD);
        removeButton = docRefListPresenter.getView().addButton(SvgPresets.DELETE);

        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(addButton.addClickHandler(this::onAdd));
        registerHandler(removeButton.addClickHandler(this::onRemove));
        registerHandler(docRefListPresenter.getSelectionModel().addSelectionHandler(event -> enableButtons()));
    }

    private void onAdd(final ClickEvent event) {
        final EntityChooser chooser = dictionarySelection.get();
        chooser.setCaption("Import a dictionary");
        chooser.setIncludedTypes(DictionaryDoc.DOCUMENT_TYPE);
        chooser.setRequiredPermissions(DocumentPermissionNames.USE);
        chooser.addDataSelectionHandler(e -> {
            final DocRef docRef = chooser.getSelectedEntityReference();
            if (docRef != null && !docRef.equals(currentDoc) && !imports.contains(docRef)) {
                imports.add(docRef);
                DirtyEvent.fire(DictionaryListPresenter.this, true);
                refresh();
            }
        });

        chooser.show();
    }

    public void onRemove(final ClickEvent event) {
        final MultiSelectionModel<DocRef> selectionModel = docRefListPresenter.getSelectionModel();
        final List<DocRef> selected = selectionModel.getSelectedItems();
        if (selected != null && selected.size() > 0) {
            String message = "Are you sure you want to remove this imported dictionary?";
            if (selected.size() > 1) {
                message = "Are you sure you want to remove these imported dictionaries?";
            }

            ConfirmEvent.fire(this,
                    message,
                    result -> {
                        if (result) {
                            imports.removeAll(selected);
                            selectionModel.clear();
                            DirtyEvent.fire(DictionaryListPresenter.this, true);
                            refresh();
                        }
                    });
        }
    }

    @Override
    public void read(final DocRef docRef, final DictionaryDoc dictionary) {
        currentDoc = docRef;
        imports = new ArrayList<>();
        if (dictionary != null) {
            if (dictionary.getImports() != null) {
                imports.addAll(dictionary.getImports());
            }
        }
        refresh();
    }

    @Override
    public void write(final DictionaryDoc dictionary) {
        if (imports.size() == 0) {
            dictionary.setImports(null);
        } else {
            dictionary.setImports(imports);
        }
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();
    }

    private void enableButtons() {
        final MultiSelectionModel<DocRef> selectionModel = docRefListPresenter.getSelectionModel();
        addButton.setEnabled(!readOnly);
        removeButton.setEnabled(!readOnly && selectionModel.getSelectedItems().size() > 0);

        if (readOnly) {
            addButton.setTitle("Add import disabled as this dictionary is read only");
            removeButton.setTitle("Remove import disabled as this dictionary is read only");
        } else {
            addButton.setTitle("Add Import");
            removeButton.setTitle("Remove Import");
        }
    }

    private void refresh() {
        docRefListPresenter.setData(imports);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
