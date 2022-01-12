/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.FlatResult;
import stroom.query.api.v2.Format.Type;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.format.FieldFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FlatResultCreator implements ResultCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlatResultCreator.class);

    private final FieldFormatter fieldFormatter;
    private final List<Mapper> mappers;
    private final List<Field> fields;

    private final ErrorConsumer errorConsumer = new ErrorConsumerImpl();

    public FlatResultCreator(final DataStoreFactory dataStoreFactory,
                             final QueryKey queryKey,
                             final String componentId,
                             final ResultRequest resultRequest,
                             final Map<String, String> paramMap,
                             final FieldFormatter fieldFormatter,
                             final Sizes defaultMaxResultsSizes) {
        this.fieldFormatter = fieldFormatter;

        final List<TableSettings> tableSettings = resultRequest.getMappings();

        if (tableSettings.size() > 1) {
            mappers = new ArrayList<>(tableSettings.size() - 1);
            for (int i = 0; i < tableSettings.size() - 1; i++) {
                final TableSettings parent = tableSettings.get(i);
                final TableSettings child = tableSettings.get(i + 1);

                // Create a set of sizes that are the minimum values for the combination of user provided sizes for the
                // parent table and the default maximum sizes.
                final Sizes sizes = Sizes.min(Sizes.create(parent.getMaxResults()), defaultMaxResultsSizes);
                final int maxItems = sizes.size(0);
                mappers.add(new Mapper(
                        dataStoreFactory,
                        queryKey,
                        componentId,
                        parent,
                        child,
                        paramMap,
                        maxItems,
                        errorConsumer));
            }
        } else {
            mappers = Collections.emptyList();
        }

        final TableSettings child = tableSettings.get(tableSettings.size() - 1);

        fields = child.getFields();
    }

    private List<Object> toNodeKey(final Map<Integer, List<Field>> groupFields, final Key key) {
        if (key == null || key.size() == 0) {
            return null;
        }

        if (!key.isGrouped()) {
            return null;
        }

        int depth = 0;
        final List<Object> result = new ArrayList<>(key.size());
        for (final KeyPart keyPart : key) {
            final Val[] values = keyPart.getGroupValues();

            if (values.length == 0) {
                result.add(null);
            } else if (values.length == 1) {
                final Val val = values[0];
                if (val == null) {
                    result.add(null);
                } else {
                    Field field = null;

                    final List<Field> fields = groupFields.get(depth);
                    if (fields != null) {
                        field = fields.get(0);
                    }

                    result.add(convert(field, val));
                }

            } else {
                final StringBuilder sb = new StringBuilder();
                for (Val val : values) {
                    if (val != null) {
                        sb.append(val);
                    }
                    sb.append("|");
                }
                sb.setLength(sb.length() - 1);
                result.add(sb.toString());
            }

            depth++;
        }

        return result;
    }

    @Override
    public Result create(final DataStore data, final ResultRequest resultRequest) {
        if (!errorConsumer.hasErrors()) {
            try {
                // Map data.
                DataStore mappedData = data;
                for (final Mapper mapper : mappers) {
                    mappedData = mapper.map(mappedData);
                }

                long totalResults = 0;

                // Get top level items.
                final Items items = mappedData.get();
                final List<List<Object>> results = new ArrayList<>(items.size());
                if (items.size() > 0) {
                    final RangeChecker rangeChecker = RangeCheckerFactory.create(resultRequest.getRequestedRange());
                    final OpenGroups openGroups =
                            OpenGroupsFactory.create(OpenGroupsConverter.convertSet(resultRequest.getOpenGroups()));

                    // Extract the maxResults settings from the last TableSettings object in the chain.
                    // Do not constrain the max results with the default max results as the result size will have
                    // already been constrained by the previous table mapping.
                    final List<TableSettings> mappings = resultRequest.getMappings();
                    final TableSettings tableSettings = mappings.get(mappings.size() - 1);
                    // Create a set of max result sizes that are determined by the supplied max results or default to
                    // integer max value.
                    final Sizes maxResults = Sizes.create(tableSettings.getMaxResults(), Integer.MAX_VALUE);

                    final Map<Integer, List<Field>> groupFields = new HashMap<>();
                    for (final Field field : fields) {
                        if (field.getGroup() != null) {
                            groupFields.computeIfAbsent(field.getGroup(), k ->
                                            new ArrayList<>())
                                    .add(field);
                        }
                    }

                    totalResults = addResults(
                            mappedData,
                            rangeChecker,
                            openGroups,
                            items,
                            results,
                            0,
                            0,
                            maxResults,
                            groupFields);
                }

                final List<Field> structure = new ArrayList<>();
                structure.add(Field.builder().name(":ParentKey").build());
                structure.add(Field.builder().name(":Key").build());
                structure.add(Field.builder().name(":Depth").build());
                structure.addAll(this.fields);

                return FlatResult
                        .builder()
                        .componentId(resultRequest.getComponentId())
                        .size(totalResults)
                        .errors(errorConsumer.getErrors())
                        .structure(structure)
                        .values(results)
                        .build();

            } catch (final Exception e) {
                LOGGER.error("Error creating result for resultRequest {}", resultRequest.getComponentId(), e);
                errorConsumer.add(e);
            }
        }

        return new FlatResult(resultRequest.getComponentId(), null, null, 0L,
                errorConsumer.getErrors());
    }

    private int addResults(final DataStore data,
                           final RangeChecker rangeChecker,
                           final OpenGroups openGroups,
                           final Items items,
                           final List<List<Object>> results,
                           final int depth,
                           final int parentCount,
                           final Sizes maxResults,
                           final Map<Integer, List<Field>> groupFields) {
        int count = parentCount;
        int maxResultsAtThisDepth = maxResults.size(depth);
        int resultCountAtThisLevel = 0;

        for (final Item item : items) {
            if (rangeChecker.check(count)) {
                final List<Object> resultList = new ArrayList<>(fields.size() + 3);

                if (item.getKey() != null) {
                    final Key key = item.getKey();
                    resultList.add(toNodeKey(groupFields, key.getParent()));
                    resultList.add(toNodeKey(groupFields, key));
                } else {
                    resultList.add(null);
                    resultList.add(null);
                }
                resultList.add(depth);

                // Convert all list into fully resolved objects evaluating
                // functions where necessary.
                int i = 0;
                for (final Field field : fields) {
                    final Val val = item.getValue(i);
                    Object result = null;
                    if (val != null) {
                        // Convert all list into fully resolved
                        // objects evaluating functions where necessary.
                        if (fieldFormatter != null) {
                            result = fieldFormatter.format(field, val);
                        } else {
                            result = convert(field, val);
                        }
                    }

                    resultList.add(result);
                    i++;
                }

                // Add the values.
                results.add(resultList);
                resultCountAtThisLevel++;

                // Add child results if a node is open.
                if (item.getKey() != null &&
                        item.getKey().isGrouped() &&
                        openGroups.isOpen(item.getKey())) {
                    final Items childItems = data.get(item.getKey());
                    if (childItems.size() > 0) {
                        count = addResults(
                                data,
                                rangeChecker,
                                openGroups,
                                childItems,
                                results,
                                depth + 1,
                                count,
                                maxResults,
                                groupFields);
                    }
                }
            }

            // Increment the position.
            count++;

            if (resultCountAtThisLevel >= maxResultsAtThisDepth) {
                break;
            }
        }

        return count;
    }

    // TODO : Replace this with conversion at the item level.
    private Object convert(final Field field, final Val val) {
        if (field != null && field.getFormat() != null && field.getFormat().getType() != null) {
            final Type type = field.getFormat().getType();
            if (Type.NUMBER.equals(type) || Type.DATE_TIME.equals(type)) {
                return val.toDouble();
            }
        }

        return val.toString();
    }

    @FunctionalInterface
    private interface RangeChecker {

        boolean check(long count);
    }

    @FunctionalInterface
    private interface OpenGroups {

        boolean isOpen(Key key);
    }

    private static class Mapper {

        private final int[] parentFieldIndices;
        private final DataStore dataStore;
        private final int maxItems;

        Mapper(final DataStoreFactory dataStoreFactory,
               final QueryKey queryKey,
               final String componentId,
               final TableSettings parent,
               final TableSettings child,
               final Map<String, String> paramMap,
               final int maxItems,
               final ErrorConsumer errorConsumer) {
            this.maxItems = maxItems;

            final FieldIndex parentFieldIndex = new FieldIndex();

            // Parent fields are now table column names.
            for (final Field field : parent.getFields()) {
                parentFieldIndex.create(field.getName());
            }

            // Extract child fields from expressions.
            final FieldIndex childFieldIndex = new FieldIndex();
            CompiledFields.create(child.getFields(), childFieldIndex, paramMap);

            // Create the index mapping.
            parentFieldIndices = new int[childFieldIndex.size()];
            for (int i = 0; i < childFieldIndex.size(); i++) {
                final String childField = childFieldIndex.getField(i);
                final Integer parentIndex = parentFieldIndex.getPos(childField);
                parentFieldIndices[i] = Objects.requireNonNullElse(parentIndex, -1);
            }

            // Create a set of max result sizes that are determined by the supplied max results or default to integer
            // max value.
            final Sizes maxResults = Sizes.create(child.getMaxResults(), Integer.MAX_VALUE);
            dataStore = dataStoreFactory.create(
                    queryKey,
                    componentId,
                    child,
                    childFieldIndex,
                    paramMap,
                    maxResults,
                    Sizes.create(Integer.MAX_VALUE),
                    false,
                    errorConsumer);
        }

        public DataStore map(final DataStore data) {
            // Get top level items.
            // TODO : Add an option to get detail level items rather than root level items.
            final Items items = data.get();

            dataStore.clear();
            if (items.size() > 0) {
                int itemCount = 0;
                for (final Item item : items) {
                    final Val[] values = new Val[parentFieldIndices.length];
                    for (int i = 0; i < parentFieldIndices.length; i++) {
                        final int index = parentFieldIndices[i];
                        if (index != -1) {
                            // TODO : @66 Currently evaluating more values than will be needed.
                            final Val val = item.getValue(index);
                            values[i] = val;
                        }
                    }
                    dataStore.add(values);

                    // Trim the data to the parent first level result size.
                    itemCount++;
                    if (itemCount >= maxItems) {
                        break;
                    }
                }
            }

            return dataStore;
        }
    }

    private static class RangeCheckerFactory {

        public static RangeChecker create(final OffsetRange range) {
            if (range == null) {
                return count -> true;
            }

            final long start = range.getOffset();
            final long end = range.getOffset() + range.getLength();
            return count -> count >= start && count < end;
        }
    }

    private static class OpenGroupsFactory {

        public static OpenGroups create(final Set<Key> openGroups) {
            if (openGroups == null || openGroups.size() == 0) {
                return group -> true;
            }

            final Set<Key> set = new HashSet<>(openGroups);
            return key -> key != null && set.contains(key);
        }
    }
}
