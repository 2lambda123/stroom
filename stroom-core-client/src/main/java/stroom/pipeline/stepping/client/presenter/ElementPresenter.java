/*
 *
 *  * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.pipeline.stepping.client.presenter;

import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.entity.shared.EntityServiceLoadAction;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.entity.shared.HasData;
import stroom.entity.shared.StringCriteria;
import stroom.pipeline.shared.FindTextConverterCriteria;
import stroom.pipeline.shared.FindXSLTCriteria;
import stroom.pipeline.shared.PipelineStepAction;
import stroom.pipeline.shared.SteppingFilterSettings;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.stepping.client.event.ShowSteppingFilterSettingsEvent;
import stroom.pipeline.stepping.client.presenter.ElementPresenter.ElementView;
import stroom.util.shared.Indicators;
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;

public class ElementPresenter extends MyPresenterWidget<ElementView> implements HasDirtyHandlers {
    public interface ElementView extends View {
        void setCodeView(View view);

        void setInputView(View view);

        void setOutputView(View view);
    }

    private String elementId;
    private PipelineElementType elementType;
    private DocRef entityRef;
    private DocRef fuzzyEntityRef;
    private PipelineStepAction pipelineStepAction;
    private boolean refreshRequired = true;
    private boolean loaded;
    private boolean dirtyCode;

    private final ClientDispatchAsync dispatcher;

    private Entity entity;
    private Indicators codeIndicators;

    private final Provider<EditorPresenter> editorProvider;

    private EditorPresenter codePresenter;
    private EditorPresenter inputPresenter;
    private EditorPresenter outputPresenter;

    @Inject
    public ElementPresenter(final EventBus eventBus, final ElementView view,
                            final Provider<EditorPresenter> editorProvider, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.editorProvider = editorProvider;
        this.dispatcher = dispatcher;
    }

    public Future<Boolean> load() {
        final FutureImpl<Boolean> future = new FutureImpl<>();

        if (!loaded) {
            loaded = true;
            boolean loading = false;

            if (elementType != null && elementType.hasRole(PipelineElementType.ROLE_HAS_CODE)) {
                getView().setCodeView(getCodePresenter().getView());

                try {
                    if (fuzzyEntityRef != null && fuzzyEntityRef.getName() != null && fuzzyEntityRef.getName().length() > 0) {
                        loadFuzzyEntityRef(future);
                        loading = true;
                    } else {
                        loadEntityRef(future);
                        loading = true;
                    }
                } catch (final Exception e) {
                    AlertEvent.fireErrorFromException(this, e, null);
                }
            }

            // We only care about seeing input if the element mutates the input
            // some how.
            if (elementType != null && elementType.hasRole(PipelineElementType.ROLE_MUTATOR)) {
                getView().setInputView(getInputPresenter().getView());
            }

            // We always want to see the output of the element.
            getView().setOutputView(getOutputPresenter().getView());

            if (!loading) {
                Scheduler.get().scheduleDeferred(() -> future.setResult(true));
            }
        } else {
            future.setResult(true);
        }

        return future;
    }

    private void loadFuzzyEntityRef(final FutureImpl<Boolean> future) {
        if (TextConverter.ENTITY_TYPE.equals(fuzzyEntityRef.getType())) {
            final FindTextConverterCriteria criteria = new FindTextConverterCriteria();
            criteria.setName(new StringCriteria(fuzzyEntityRef.getName()));
            criteria.setSort(FindXSLTCriteria.FIELD_ID);
            final EntityServiceFindAction<FindTextConverterCriteria, TextConverter> findAction = new EntityServiceFindAction<>(criteria);
            dispatcher.exec(findAction)
                    .onSuccess(result -> {
                        if (result != null && result.size() > 0) {
                            entity = result.get(0);
                            dirtyCode = false;
                            read();
                            future.setResult(true);
                        } else {
                            // Try and load by entity ref if there is one.
                            loadEntityRef(future);
                        }
                    })
                    .onFailure(caught -> future.setResult(false));
        } else if (XSLT.ENTITY_TYPE.equals(fuzzyEntityRef.getType())) {
            final FindXSLTCriteria criteria = new FindXSLTCriteria();
            criteria.setName(new StringCriteria(fuzzyEntityRef.getName()));
            criteria.setSort(FindXSLTCriteria.FIELD_ID);
            final EntityServiceFindAction<FindXSLTCriteria, XSLT> findAction = new EntityServiceFindAction<>(criteria);
            dispatcher.exec(findAction)
                    .onSuccess(result -> {
                        if (result != null && result.size() > 0) {
                            entity = result.get(0);
                            dirtyCode = false;
                            read();
                            future.setResult(true);
                        } else {
                            // Try and load by entity ref if there is one.
                            loadEntityRef(future);
                        }
                    })
                    .onFailure(caught -> future.setResult(false));
        } else {
            Scheduler.get().scheduleDeferred(() -> future.setResult(true));
        }
    }

    private void loadEntityRef(final FutureImpl<Boolean> future) {
        if (entityRef != null) {
            dispatcher.exec(new EntityServiceLoadAction<>(entityRef, null))
                    .onSuccess(result -> {
                        entity = result;
                        dirtyCode = false;
                        read();

                        future.setResult(true);
                    })
                    .onFailure(caught -> future.setResult(false));
        } else {
            Scheduler.get().scheduleDeferred(() -> future.setResult(true));
        }
    }

    public void save() {
        if (loaded && entity != null && dirtyCode) {
            write();
            dispatcher.exec(new EntityServiceSaveAction<>(entity)).onSuccess(result -> {
                entity = result;
                dirtyCode = false;
            });
        }
    }

    private void read() {
        if (entity != null && entity instanceof HasData) {
            final HasData hasData = (HasData) entity;
            setCode(hasData.getData(), codeIndicators);
        } else {
            setCode("", codeIndicators);
        }
    }

    private void write() {
        if (entity instanceof HasData) {
            final HasData hasData = (HasData) entity;
            hasData.setData(getCode());
        }
    }

    public String getCode() {
        if (codePresenter == null) {
            return null;
        }
        return codePresenter.getText();
    }

    public void setCode(final String code, final Indicators codeIndicators) {
        if (codePresenter != null) {
            this.codeIndicators = codeIndicators;

            if (!codePresenter.getText().equals(code)) {
                codePresenter.setText(code);
            }

            codePresenter.setIndicators(codeIndicators);
        }
    }

    public void setCodeIndicators(final Indicators codeIndicators) {
        if (codePresenter != null) {
            this.codeIndicators = codeIndicators;
            codePresenter.setIndicators(codeIndicators);
        }
    }

    public void setInput(final String input, final int inputStartLineNo, final boolean formatInput,
                         final Indicators inputIndicators) {
        if (inputPresenter != null) {
            inputPresenter.getStylesOption().setOn(formatInput);

            if (!inputPresenter.getText().equals(input)) {
                inputPresenter.setText(input);
                if (formatInput) {
                    inputPresenter.format();
                }
            }

            inputPresenter.setFirstLineNumber(inputStartLineNo);
            inputPresenter.setIndicators(inputIndicators);
        }
    }

    public void setOutput(final String output, final int outputStartLineNo, final boolean formatOutput,
                          final Indicators outputIndicators) {
        if (outputPresenter != null) {
            outputPresenter.getStylesOption().setOn(formatOutput);

            if (!outputPresenter.getText().equals(output)) {
                outputPresenter.setText(output);
                if (formatOutput) {
                    outputPresenter.format();
                }
            }

            outputPresenter.setFirstLineNumber(outputStartLineNo);
            outputPresenter.setIndicators(outputIndicators);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public void setElementId(final String elementId) {
        this.elementId = elementId;
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementType(final PipelineElementType elementType) {
        this.elementType = elementType;
    }

    public PipelineElementType getElementType() {
        return elementType;
    }

    public void setEntityRef(final DocRef entityRef) {
        this.entityRef = entityRef;
    }

    public void setFuzzyEntityRef(final DocRef fuzzyEntityRef) {
        this.fuzzyEntityRef = fuzzyEntityRef;
    }

    public void setPipelineStepAction(final PipelineStepAction pipelineStepAction) {
        this.pipelineStepAction = pipelineStepAction;
    }

    public boolean isRefreshRequired() {
        return refreshRequired;
    }

    public void setRefreshRequired(final boolean refreshRequired) {
        this.refreshRequired = refreshRequired;
    }

    public boolean isDirtyCode() {
        return dirtyCode;
    }

    private EditorPresenter getCodePresenter() {
        if (codePresenter == null) {
            codePresenter = editorProvider.get();
            setOptions(codePresenter);
            codePresenter.getLineNumbersOption().setOn(true);

            registerHandler(codePresenter.addValueChangeHandler(event -> {
                dirtyCode = true;
                DirtyEvent.fire(ElementPresenter.this, true);
            }));
            registerHandler(codePresenter.addFormatHandler(event -> {
                dirtyCode = true;
                DirtyEvent.fire(ElementPresenter.this, true);
            }));
        }
        return codePresenter;
    }

    private EditorPresenter getInputPresenter() {
        if (inputPresenter == null) {
            inputPresenter = editorProvider.get();
            inputPresenter.setReadOnly(true);
            setOptions(inputPresenter);

            inputPresenter.setShowFilterSettings(false);
            inputPresenter.setInput(true);
        }
        return inputPresenter;
    }

    private EditorPresenter getOutputPresenter() {
        if (outputPresenter == null) {
            outputPresenter = editorProvider.get();
            outputPresenter.setReadOnly(true);
            setOptions(outputPresenter);

            // Turn on line numbers for the output presenter if this is a validation step as the output needs to show
            // validation errors in the gutter.
            if (elementType != null && elementType.hasRole(PipelineElementType.ROLE_VALIDATOR)) {
                outputPresenter.getLineNumbersOption().setOn(true);
            }

            outputPresenter.setShowFilterSettings(true);
            outputPresenter.setInput(false);

            registerHandler(outputPresenter.addChangeFilterHandler(event -> {
                final SteppingFilterSettings settings = pipelineStepAction.getStepFilter(elementId);
                ShowSteppingFilterSettingsEvent.fire(ElementPresenter.this, outputPresenter, false, elementId,
                        settings);
            }));
        }
        return outputPresenter;
    }

    private void setOptions(final EditorPresenter editorPresenter) {
        editorPresenter.getIndicatorsOption().setAvailable(true);
        editorPresenter.getIndicatorsOption().setOn(true);
        editorPresenter.getLineNumbersOption().setAvailable(true);
        editorPresenter.getLineNumbersOption().setOn(false);
    }
}
