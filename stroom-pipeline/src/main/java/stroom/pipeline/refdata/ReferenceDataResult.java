/*
 * Copyright 2018 Crown Copyright
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

package stroom.pipeline.refdata;

import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public class ReferenceDataResult implements ErrorReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataResult.class);

    private RefDataValueProxy refDataValueProxy;

    private LookupIdentifier currentLookupIdentifier;

    private List<LazyMessage> messages = new ArrayList<>();

    // 0-1 stream(s) per ref loader based on the effective datetime of the stream and the lookup datetime
    private final List<RefStreamDefinition> effectiveStreams = new ArrayList<>();

    // 0-1 stream(s) per ref loader. Contains those 'effectiveStreams' that are known to contain the map
    // being looked up or where the content of the stream is currently unknown. A sub-set of effectiveStreams
    // or equal to it.
//    private final List<RefStreamDefinition> qualifyingStreams = new ArrayList<>();

//    private Set<String> missingMaps = null;


    public ReferenceDataResult(final LookupIdentifier lookupIdentifier) {
        this.currentLookupIdentifier = Objects.requireNonNull(lookupIdentifier);
    }

    public Optional<RefDataValueProxy> getRefDataValueProxy() {
        return Optional.ofNullable(refDataValueProxy);
    }

    void addRefDataValueProxy(final RefDataValueProxy refDataValueProxy) {
        Objects.requireNonNull(refDataValueProxy);
        if (this.refDataValueProxy == null) {
            LOGGER.trace("Setting refDataValueProxy to {}", refDataValueProxy);
            this.refDataValueProxy = refDataValueProxy;
        } else {
            final RefDataValueProxy combinedRefDataValueProxy = this.refDataValueProxy.merge(refDataValueProxy);
            LOGGER.trace("Setting refDataValueProxy to {}", combinedRefDataValueProxy);
            this.refDataValueProxy = combinedRefDataValueProxy;
        }
    }

    /**
     * The stream that has been chosen from a feed whose effective time matches the time
     * on the lookup.
     */
    public void addEffectiveStream(final RefStreamDefinition refStreamDefinition) {
        Objects.requireNonNull(refStreamDefinition);
        LOGGER.trace("Adding effectiveStream {}", refStreamDefinition);
        effectiveStreams.add(refStreamDefinition);
    }

    /**
     * An effective stream that is known to contain the map being looked up against or where
     * the content of the effective stream is currently unknown.
     */
//    public void addQualifyingStream(final RefStreamDefinition refStreamDefinition) {
//        Objects.requireNonNull(refStreamDefinition);
//        // Doing contains on a list not ideal but there are probably only a handful of items in there.
//        if (effectiveStreams.contains(refStreamDefinition)) {
//            LOGGER.trace("Adding qualifyingStream {}", refStreamDefinition);
//            qualifyingStreams.add(refStreamDefinition);
//        } else {
//            throw new RuntimeException("Adding a qualifying stream that is not an effective stream "
//                    + refStreamDefinition);
//        }
//    }

    /**
     * Call this when it is conclusively known that the stream does not contain the required map.
     */
//    public void removeQualifyingStream(final RefStreamDefinition refStreamDefinition) {
//        Objects.requireNonNull(refStreamDefinition);
//        qualifyingStreams.remove(refStreamDefinition);
//    }

//    public void addMissingMap(final String mapName) {
//        if (missingMaps == null) {
//            missingMaps = new HashSet<>();
//        }
//        missingMaps.add(mapName);
//    }

    /**
     * 0-1 stream(s) per ref loader based on the effective datetime of the stream and the lookup datetime
     */
    public List<RefStreamDefinition> getEffectiveStreams() {
        return effectiveStreams;
    }

    /**
     * 0-1 stream(s) per ref loader. Contains those streams from {@link ReferenceDataResult#getEffectiveStreams()}
     * that are known to contain the map being looked up or where the content of the stream is currently unknown.
     * A sub-set of effectiveStreams or equal to it.
     */
    public List<RefStreamDefinition> getQualifyingStreams() {
        return refDataValueProxy == null
                ? Collections.emptyList()
                : refDataValueProxy.getMapDefinitions()
                        .stream()
                        .map(MapDefinition::getRefStreamDefinition)
                        .collect(Collectors.toList());
    }

//    public Set<String> getMissingMaps() {
//        return NullSafe.nonNullSet(missingMaps);
//    }


    public LookupIdentifier getCurrentLookupIdentifier() {
        return currentLookupIdentifier;
    }

    /**
     * Clears out all effective/qualifying streams and any {@link RefDataValueProxy}
     */
    public void setCurrentLookupIdentifier(final LookupIdentifier lookupIdentifier) {
        LOGGER.trace("Changing identifier from {} to {}", currentLookupIdentifier, lookupIdentifier);
        this.currentLookupIdentifier = lookupIdentifier;
        // Switched identifier so clear out the existing state

        effectiveStreams.clear();
//        qualifyingStreams.clear();
//        NullSafe.consume(missingMaps, Set::clear);
        refDataValueProxy = null;
    }

    /**
     * Log a message using a template. SLF style templating.
     */
    // Different name to avoid confusion with varargs
    public void logSimpleTemplate(final Severity severity,
                                  final String messageTemplate,
                                  final Object... messageArguments) {
        // Use trace level as we don't know what level this func is called from
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(messageTemplate, (Object[]) messageArguments);
        }

        if (messageTemplate != null && !messageTemplate.isBlank()) {
            messages.add(new LazyMessage(
                    severity,
                    null,
                    null,
                    messageTemplate,
                    messageArguments));
        }
    }

    /**
     * Log a message using a template with lazily provided template arguments.
     * SLF style templating.
     */
    public void logLazyTemplate(final Severity severity,
                                final String messageTemplate,
                                final Supplier<List<Object>> messageArgumentsSupplier) {
        // Use trace level as we don't know what level this func is called from
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(messageTemplate, LazyMessage.convertMessageArgs(messageArgumentsSupplier));
        }

        if (messageTemplate != null && !messageTemplate.isBlank()) {
            messages.add(new LazyMessage(
                    severity,
                    null,
                    null,
                    messageTemplate,
                    messageArgumentsSupplier));
        }
    }

    @Override
    public void logTemplate(final Severity severity,
                            final Location location,
                            final String elementId,
                            final String messageTemplate,
                            final Throwable e,
                            final Object... messageArgs) {
        // Use trace level as we don't know what level this func is called from
        if (LOGGER.isTraceEnabled()) {
            if ((Object[]) messageArgs == null || messageArgs.length == 0) {
                if (e == null) {
                    LOGGER.trace(messageTemplate);
                } else {
                    LOGGER.trace(messageTemplate, e);
                }
            } else {
                if (e == null) {
                    LOGGER.trace(messageTemplate, messageArgs);
                } else {
                    // Add the ex on the end
                    final Object[] args = Arrays.copyOf(messageArgs, messageArgs.length + 1);
                    args[messageArgs.length] = e;
                    LOGGER.trace(messageTemplate, args);
                }
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(e.getMessage(), e);
            }
        }

        if (messageTemplate != null && !messageTemplate.isBlank()) {
            messages.add(new LazyMessage(
                    severity,
                    location,
                    elementId,
                    messageTemplate,
                    messageArgs));
        }
    }

    @Override
    public void log(final Severity severity,
                    final Location location,
                    final String elementId,
                    final String message,
                    final Throwable e) {

        logTemplate(severity, location, elementId, message, e, (Object[]) null);
    }

    public void append(final StringBuilder sb) {
        if (messages != null) {
            messages.stream()
                    .filter(Objects::nonNull)
                    .forEach(message -> {
                        sb.append(message);
                        sb.append("\n");
                    });
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }

    public List<LazyMessage> getMessages() {
        return messages;
    }


    // --------------------------------------------------------------------------------


    public static class LazyMessage {

        private static final String SPACE = " ";
        private static final String CLOSE_BRACKET = "] ";
        private static final String COLON = ":";
        private static final String OPEN_BRACKET = "[";

        private final Severity severity;
        private final Location location;
        private final String elementId;
        // Hold template and args separately to save memory, e.g. if we have loads of messages of a similar
        // type.
        private final String messageTemplate;
        private final Object[] messageArgs;
        private final Supplier<List<Object>> messageArgsSupplier;
        private final boolean useSupplierForArgs;

        private LazyMessage(final Severity severity,
                            final Location location,
                            final String elementId,
                            final String messageTemplate,
                            final Object... messageArgs) {
            this.severity = severity;
            this.location = location;
            this.elementId = elementId;
            this.messageTemplate = messageTemplate;
            this.messageArgs = messageArgs;
            this.messageArgsSupplier = null;
            this.useSupplierForArgs = false;
        }

        private LazyMessage(final Severity severity,
                            final Location location,
                            final String elementId,
                            final String messageTemplate,
                            final Supplier<List<Object>> messageArgsSupplier) {
            this.severity = severity;
            this.location = location;
            this.elementId = elementId;
            this.messageTemplate = messageTemplate;
            this.messageArgs = null;
            this.messageArgsSupplier = messageArgsSupplier;
            this.useSupplierForArgs = true;
        }

        public Severity getSeverity() {
            return severity;
        }

        public Location getLocation() {
            return location;
        }

        public String getElementId() {
            return elementId;
        }

        public String getMessage() {
            final Object[] messageArgs = getMessageArgs();
            if (messageArgs == null || messageArgs.length == 0) {
                return messageTemplate;
            } else {
                return LogUtil.message(messageTemplate, messageArgs);
            }
        }

        private Object[] getMessageArgs() {
            return useSupplierForArgs
                    ? convertMessageArgs(messageArgsSupplier)
                    : messageArgs;
        }

        private static Object[] convertMessageArgs(final Supplier<List<Object>> messageArgsSupplier) {
            final List<Object> argsList = NullSafe.getOrElseGet(
                    messageArgsSupplier,
                    Supplier::get,
                    Collections::emptyList);
            return argsList != null
                    ? argsList.toArray(new Object[0])
                    : null;
        }

        private void append(final StringBuilder sb) {
            if (elementId != null) {
                sb.append(elementId);
                sb.append(SPACE);
            }
            if (location != null) {
                sb.append(OPEN_BRACKET);
                sb.append(location);
                sb.append(CLOSE_BRACKET);
            }
            if (severity != null) {
                sb.append(severity.getDisplayValue());
                sb.append(COLON);
                sb.append(SPACE);
            }
            NullSafe.consume(getMessage(), sb::append);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            append(sb);
            return sb.toString();
        }
    }
}
