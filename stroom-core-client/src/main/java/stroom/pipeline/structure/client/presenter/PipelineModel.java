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

package stroom.pipeline.structure.client.presenter;

import com.google.gwt.event.shared.GwtEvent;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.google.web.bindery.event.shared.SimpleEventBus;
import stroom.pipeline.client.event.ChangeDataEvent;
import stroom.pipeline.client.event.ChangeDataEvent.ChangeDataHandler;
import stroom.pipeline.client.event.HasChangeDataHandlers;
import stroom.pipeline.server.factory.ElementIcons;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelineReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PipelineModel implements HasChangeDataHandlers<PipelineModel> {
    private static final String SOURCE = "Source";
    private static final PipelineElementType SOURCE_ELEMENT_TYPE = new PipelineElementType(SOURCE, null,
            new String[]{PipelineElementType.ROLE_SOURCE, PipelineElementType.ROLE_HAS_TARGETS,
                    PipelineElementType.VISABILITY_SIMPLE},
            ElementIcons.STREAM);
    public static final PipelineElement SOURCE_ELEMENT = new PipelineElement(SOURCE, SOURCE);

    static {
        SOURCE_ELEMENT.setElementType(SOURCE_ELEMENT_TYPE);
    }

    private Map<PipelineElement, List<PipelineElement>> childMap;
    private Map<PipelineElement, PipelineElement> parentMap;
    private PipelineData pipelineData;
    private List<PipelineData> baseStack;

    private PipelineDataMerger baseData;
    private PipelineDataMerger combinedData;

    private final EventBus eventBus = new SimpleEventBus();

    public PipelineModel() {
        baseData = new PipelineDataMerger();
        combinedData = new PipelineDataMerger();
    }

    public void setPipelineData(final PipelineData pipelineData) {
        this.pipelineData = pipelineData;
    }

    public void setBaseStack(final List<PipelineData> baseStack) {
        this.baseStack = baseStack;
    }

    public void build() throws PipelineModelException {
        buildBaseData();
        buildCombinedData();
        refresh();
    }

    private void buildCombinedData() throws PipelineModelException {
        // Merge pipeline data together.
        List<PipelineData> combined;
        if (baseStack != null) {
            combined = new ArrayList<>(baseStack.size() + 1);
        } else {
            combined = new ArrayList<>(1);
        }

        if (baseStack != null) {
            combined.addAll(baseStack);
        }
        if (pipelineData != null) {
            combined.add(pipelineData);
        }

        final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
        pipelineDataMerger.merge(combined);
        combinedData = pipelineDataMerger;
    }

    private void buildBaseData() throws PipelineModelException {
        final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
        if (baseStack != null) {
            pipelineDataMerger.merge(baseStack);
        }

        baseData = pipelineDataMerger;
    }

    public PipelineData diff() {
        final PipelineData result = new PipelineData();

        if (pipelineData != null) {
            // Get a set of valid (used/linked) elements.
            final Set<String> validElements = new HashSet<>();
            for (final List<PipelineLink> list : combinedData.getLinks().values()) {
                for (final PipelineLink link : list) {
                    validElements.add(link.getFrom());
                    validElements.add(link.getTo());
                }
            }

            // Add elements that exist in combined data but not in base.
            for (final PipelineElement combinedElement : combinedData.getElements().values()) {
                final PipelineElement baseElement = baseData.getElements().get(combinedElement.getId());
                if (baseElement == null) {
                    // Only add the element if there are links from/to it.
                    if (validElements.contains(combinedElement.getId())) {
                        result.addElement(combinedElement);
                    }
                }
            }

            // Remove elements that exist in base but not combined.
            for (final PipelineElement baseElement : baseData.getElements().values()) {
                final PipelineElement combinedElement = combinedData.getElements().get(baseElement.getId());
                if (combinedElement == null) {
                    result.removeElement(baseElement);
                }
            }

            // We only need to worry about properties, pipeline references and
            // links that are related to valid elements.
            for (final String id : validElements) {
                if (id != null) {
                    copyProperties(id, pipelineData.getAddedProperties(), result.getAddedProperties(),
                            pipelineData.getRemovedProperties());
                    copyProperties(id, pipelineData.getRemovedProperties(), result.getRemovedProperties(), null);

                    copyReferences(id, pipelineData.getAddedPipelineReferences(), result.getAddedPipelineReferences(),
                            pipelineData.getRemovedPipelineReferences());
                    copyReferences(id, pipelineData.getRemovedPipelineReferences(),
                            result.getRemovedPipelineReferences(), null);

                    final List<PipelineLink> combinedLinks = combinedData.getLinks().get(id);
                    final List<PipelineLink> baseLinks = baseData.getLinks().get(id);

                    // Add links that exist in combined data but not in base.
                    if (combinedLinks != null) {
                        for (final PipelineLink combinedLink : combinedLinks) {
                            if (baseLinks == null || !baseLinks.contains(combinedLink)) {
                                result.addLink(combinedLink);
                            }
                        }
                    }

                    // Remove links that exist in base data but not in combined.
                    if (baseLinks != null) {
                        for (final PipelineLink baseLink : baseLinks) {
                            if (combinedLinks == null || !combinedLinks.contains(baseLink)) {
                                result.removeLink(baseLink);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    private void copyProperties(final String id, final List<PipelineProperty> source, final List<PipelineProperty> dest,
                                final List<PipelineProperty> ignore) {
        final Set<PipelineProperty> set = new HashSet<>();

        if (ignore != null) {
            set.addAll(ignore);
        }

        for (final PipelineProperty property : source) {
            if (id.equals(property.getElement()) && !set.contains(property)) {
                set.add(property);
                dest.add(property);
            }
        }
    }

    private void copyReferences(final String id, final List<PipelineReference> source,
                                final List<PipelineReference> dest, final List<PipelineReference> ignore) {
        final Set<PipelineReference> set = new HashSet<>();

        if (ignore != null) {
            set.addAll(ignore);
        }

        for (final PipelineReference pipelineReference : source) {
            if (id.equals(pipelineReference.getElement()) && !set.contains(pipelineReference)) {
                set.add(pipelineReference);
                dest.add(pipelineReference);
            }
        }
    }

    public List<PipelineElement> getRemovedElements() {
        return pipelineData.getElements().getRemove();
    }

    private void refresh() {
        childMap = new HashMap<>();
        parentMap = new HashMap<>();

        for (final List<PipelineLink> list : combinedData.getLinks().values()) {
            for (final PipelineLink link : list) {
                final PipelineElement linkFrom = combinedData.getElements().get(link.getFrom());
                final PipelineElement linkTo = combinedData.getElements().get(link.getTo());

                parentMap.put(linkTo, linkFrom);
                childMap.computeIfAbsent(linkFrom, k -> new ArrayList<>()).add(linkTo);
            }
        }

        // Sort all child lists.
        for (final List<PipelineElement> children : childMap.values()) {
            Collections.sort(children);
        }

        // Let anybody that cares know that the model has changed.
        ChangeDataEvent.fire(this, this);
    }

    public Map<PipelineElement, List<PipelineElement>> getChildMap() {
        return childMap;
    }

    public Map<PipelineElement, PipelineElement> getParentMap() {
        return parentMap;
    }

    public PipelineElement addElement(final PipelineElement parent, final PipelineElementType elementType,
                                      final String id) throws PipelineModelException {
        PipelineElement element;

        if (id == null || id.length() == 0) {
            throw new PipelineModelException("No id has been set for this element");
        } else if (elementType == null) {
            throw new PipelineModelException("No element type has been chosen");
        } else if (parent == null) {
            throw new PipelineModelException("No parent element has been selected");
        } else {
            if (combinedData.getElements().containsKey(id)) {
                throw new PipelineModelException("An element with this id already exists");
            }

            element = PipelineDataUtil.createElement(id, elementType.getType());
            element.setElementType(elementType);
            if (pipelineData.getRemovedElements().contains(element)) {
                throw new PipelineModelException(
                        "Attempt to add an element with an id that matches a hidden element. Restore the existing element if required or change the element id.");
            }

            pipelineData.addElement(element);
            pipelineData.addLink(PipelineDataUtil.createLink(parent.getId(), id));

            buildCombinedData();
            refresh();
        }

        return element;
    }

    public void addExistingElement(final PipelineElement parent, final PipelineElement existingElement)
            throws PipelineModelException {
        final String id = existingElement.getId();

        if (combinedData.getElements().containsKey(id)) {
            throw new PipelineModelException("An element with this id already exists");
        } else if (parent == null) {
            throw new PipelineModelException("No parent element has been selected");
        }

        // Make sure this element isn't shadowed anymore.
        if (pipelineData.getRemovedElements().contains(existingElement)) {
            pipelineData.getRemovedElements().remove(existingElement);
        }

        pipelineData.addLink(PipelineDataUtil.createLink(parent.getId(), id));

        buildCombinedData();
        refresh();
    }

    public void removeElement(final PipelineElement element) throws PipelineModelException {
        final String id = element.getId();

        // Remove the element.
        pipelineData.removeElement(element);
        // Remove all links from/to this element.
        removeLinks(pipelineData.getLinks().getAdd(), id);
        removeLinks(pipelineData.getLinks().getRemove(), id);

        // Ensure links don't come back if we restore this element.
        for (final List<PipelineLink> links : baseData.getLinks().values()) {
            for (final PipelineLink link : links) {
                if (link.getTo().equals(id)) {
                    pipelineData.removeLink(link);
                }
            }
        }

        // Update the tree.
        buildCombinedData();
        refresh();
    }

    public List<PipelineProperty> getProperties(final PipelineElement element) {
        final List<PipelineProperty> properties = new ArrayList<>();

        if (element != null) {
            final Map<String, PipelineProperty> map = combinedData.getProperties().get(element.getId());
            if (map != null) {
                for (final PipelineProperty property : map.values()) {
                    final PipelineProperty newProperty = new PipelineProperty();
                    newProperty.copyFrom(property);
                    properties.add(newProperty);
                }
            }
        }

        return properties;
    }

    public PipelineDataMerger getBaseData() {
        return baseData;
    }

    public PipelineData getPipelineData() {
        return pipelineData;
    }

    private void removeLinks(final List<PipelineLink> list, final String element) {
        final Iterator<PipelineLink> iter = list.iterator();
        while (iter.hasNext()) {
            final PipelineLink link = iter.next();
            if (link.getTo().equals(element)) {
                iter.remove();
            }
        }
    }

    @Override
    public HandlerRegistration addChangeDataHandler(final ChangeDataHandler<PipelineModel> handler) {
        return eventBus.addHandler(ChangeDataEvent.getType(), handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEventFromSource(event, this);
    }
}
