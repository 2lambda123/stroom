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
 */

package stroom.dashboard.client.table;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.main.UniqueUtil;
import stroom.data.grid.client.Heading;
import stroom.data.grid.client.HeadingListener;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.HideMenuEvent;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.VerticalLocation;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Timer;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class FieldsManager implements HeadingListener {

    private final TablePresenter tablePresenter;
    private final Provider<RenameFieldPresenter> renameFieldPresenterProvider;
    private final Provider<ColumnFunctionEditorPresenter> expressionPresenterProvider;
    private final FormatPresenter formatPresenter;
    private final FilterPresenter filterPresenter;
    private int fieldsStartIndex;
    private int currentColIndex = -1;
    private boolean ignoreNext;

    public FieldsManager(final TablePresenter tablePresenter,
                         final Provider<RenameFieldPresenter> renameFieldPresenterProvider,
                         final Provider<ColumnFunctionEditorPresenter> expressionPresenterProvider,
                         final FormatPresenter formatPresenter,
                         final FilterPresenter filterPresenter) {
        this.tablePresenter = tablePresenter;
        this.renameFieldPresenterProvider = renameFieldPresenterProvider;
        this.expressionPresenterProvider = expressionPresenterProvider;
        this.formatPresenter = formatPresenter;
        this.filterPresenter = filterPresenter;
    }

    @Override
    public void onMouseDown(NativeEvent event, Heading heading) {
        int colIndex = -1;
        if (heading != null) {
            colIndex = heading.getColIndex();
        }

        ignoreNext = currentColIndex == colIndex;
        HideMenuEvent
                .builder()
                .fire(tablePresenter);
    }

    @Override
    public void onMouseUp(final NativeEvent event, final Heading heading) {
        if (heading != null && heading.getColIndex() >= fieldsStartIndex) {
            final int colIndex = heading.getColIndex();

            final Field field = getField(colIndex);
            if (field != null && !ignoreNext) {
                new Timer() {
                    @Override
                    public void run() {
                        if (currentColIndex == colIndex) {
                            HideMenuEvent
                                    .builder()
                                    .fire(tablePresenter);

                        } else {
                            currentColIndex = colIndex;
                            final Element target = heading.getElement();

                            final List<Item> menuItems = getMenuItems(field);

                            Element element = event.getEventTarget().cast();
                            while (!element.getTagName().equalsIgnoreCase("th")) {
                                element = element.getParentElement();
                            }

                            final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft(),
                                    target.getAbsoluteRight(),
                                    target.getAbsoluteTop(),
                                    target.getAbsoluteBottom(),
                                    null,
                                    VerticalLocation.BELOW);

                            ShowMenuEvent
                                    .builder()
                                    .items(menuItems)
                                    .popupPosition(popupPosition)
                                    .addAutoHidePartner(element)
                                    .onHide(e2 -> currentColIndex = -1)
                                    .fire(tablePresenter);
                        }
                    }
                }.schedule(0);
            }
        }

        ignoreNext = false;
    }

    @Override
    public void moveColumn(final int fromIndex, final int toIndex) {
        final Field field = getField(fromIndex);
        if (field != null) {
            final List<Field> fields = tablePresenter.getTableSettings().getFields();
            fields.remove(field);

            final int destIndex = toIndex - fieldsStartIndex;
            if (fields.size() <= destIndex) {
                fields.add(field);
            } else if (destIndex > fromIndex) {
                fields.add(destIndex - 1, field);
            } else {
                fields.add(destIndex, field);
            }

            tablePresenter.setDirty(true);
        }
    }

    @Override
    public void resizeColumn(final int colIndex, final int size) {
        final Field field = getField(colIndex);
        if (field != null) {
            replaceField(field, field.copy().width(size).build());
            tablePresenter.setDirty(true);
        }
    }

    private void changeSort(final Field field, final SortDirection direction) {
        final List<Field> fields = tablePresenter.getTableSettings().getFields();
        boolean change = false;

        if (direction == null) {
            if (field.getSort() != null) {
                final int order = field.getSort().getOrder();
                replaceField(field, field.copy().sort(null).build());
                increaseSortOrder(fields, order);
                change = true;
            }
        } else {
            final Sort sort = field.getSort();
            if (sort == null) {
                final int lowestSortOrder = getLowestSortOrder(fields);
                final Sort newSort = new Sort(lowestSortOrder + 1, direction);
                replaceField(field, field.copy().sort(newSort).build());
                change = true;
            } else {
                final int lowestSortOrder = getLowestSortOrder(fields);
                if (sort.getDirection() != direction || sort.getOrder() != lowestSortOrder) {
                    // Increase sort order on all fields where the sort order is
                    // lower than this fields sort order as this field will now
                    // have the lowest order.
                    increaseSortOrder(fields, field.getSort().getOrder());

                    final Sort newSort = new Sort(lowestSortOrder, direction);
                    replaceField(field, field.copy().sort(newSort).build());

                    change = true;
                }
            }
        }

        if (change) {
            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
            tablePresenter.reset();
            tablePresenter.clear();
        }
    }

    private int getLowestSortOrder(final List<Field> fields) {
        int lowestOrder = -1;
        for (final Field field : fields) {
            if (field.getSort() != null) {
                if (lowestOrder < field.getSort().getOrder()) {
                    lowestOrder = field.getSort().getOrder();
                }
            }
        }

        return lowestOrder;
    }

    private void increaseSortOrder(final List<Field> fields, final int order) {
        for (final Field field : fields) {
            final Sort sort = field.getSort();
            if (sort != null && sort.getOrder() > order) {
                final Sort newSort = new Sort(sort.getOrder() - 1, sort.getDirection());
                replaceField(field, field.copy().sort(newSort).build());
            }
        }
    }

    public void showRename(final Field field) {
        renameFieldPresenterProvider.get().show(tablePresenter, field, (oldField, newField) -> {
            replaceField(oldField, newField);
            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
        });
    }

    public void showExpression(final Field field) {
        expressionPresenterProvider.get().show(tablePresenter, field, (oldField, newField) -> {
            replaceField(oldField, newField);
            tablePresenter.setDirty(true);
            tablePresenter.clear();
        });
    }

    public void showFormat(final Field field) {
        formatPresenter.show(field, (oldField, newField) -> {
            replaceField(oldField, newField);
            tablePresenter.setDirty(true);
            tablePresenter.clear();
        });
    }

    private void filterField(final Field field) {
        filterPresenter.show(tablePresenter, field, (oldField, newField) -> {
            replaceField(oldField, newField);
            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
            tablePresenter.clear();
        });
    }

    public void addField(final Field templateField) {
        addField(getFields().size(), templateField);
    }

    public void addField(final int index, final Field templateField) {
        final String fieldName = makeUniqueFieldName(templateField.getName());
        final Field newField = templateField.copy()
                .name(fieldName)
                .id(createRandomFieldId())
                .build();

        final List<Field> fields = getFields();
        fields.add(index, newField);
        updateFields(fields);

        tablePresenter.setDirty(true);
        tablePresenter.updateColumns();
    }

    private void duplicateField(final Field field) {
        final List<Field> fields = getFields();
        final int index = fields.indexOf(field);
        addField(index + 1, field);
    }

    private void moveFirst(final Field field) {
        final List<Field> fields = getFields();
        fields.remove(field);
        fields.add(0, field);
        updateFields(fields);
        tablePresenter.setDirty(true);
        tablePresenter.updateColumns();
    }

    private void moveLast(final Field field) {
        final List<Field> fields = getFields();
        fields.remove(field);
        fields.add(field);
        updateFields(fields);
        tablePresenter.setDirty(true);
        tablePresenter.updateColumns();
    }

    private String makeUniqueFieldName(final String fieldName) {
        final Set<String> currentFields = getFields().stream().map(Field::getName).collect(
                Collectors.toSet());
        return UniqueUtil.makeUniqueName(fieldName, currentFields);
    }

    private String createRandomFieldId() {
        final Set<String> usedFieldIds = getFields().stream().map(Field::getId).collect(Collectors.toSet());
        return createRandomFieldId(usedFieldIds);
    }

    public String createRandomFieldId(final Set<String> usedFieldIds) {
        final String componentId = tablePresenter.getComponentConfig().getId();
        return UniqueUtil.createUniqueFieldId(componentId, usedFieldIds);
    }

    private void deleteField(final Field field) {
        if (getVisibleFieldCount() <= 1) {
            AlertEvent.fireError(tablePresenter, "You cannot remove or hide all fields", null);
        } else {
            replaceField(field, null);

            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
        }
    }

    private void replaceField(final Field oldField, final Field newField) {
        final List<Field> fields = new ArrayList<>();
        for (final Field field : getFields()) {
            if (field.getId().equals(oldField.getId())) {
                if (newField != null) {
                    fields.add(newField);
                }
            } else {
                fields.add(field);
            }
        }
        updateFields(fields);
    }

    private List<Field> getFields() {
        if (tablePresenter.getSettings() != null && tablePresenter.getTableSettings().getFields() != null) {
            return new ArrayList<>(tablePresenter.getTableSettings().getFields());
        }
        return new ArrayList<>();
    }

    private void updateFields(final List<Field> fields) {
        tablePresenter.setSettings(
                tablePresenter.getTableSettings()
                        .copy()
                        .fields(fields)
                        .build());
    }

    private void showField(final Field field) {
        replaceField(field, field.copy().visible(true).build());
        tablePresenter.setDirty(true);
        tablePresenter.updateColumns();
    }

    private void hideField(final Field field) {
        if (getVisibleFieldCount() <= 1) {
            AlertEvent.fireError(tablePresenter, "You cannot remove or hide all fields", null);
        } else {
            replaceField(field, field.copy().visible(false).build());
            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
        }
    }

    private long getVisibleFieldCount() {
        final List<Field> fields = getFields();
        return fields.stream()
                .filter(Field::isVisible)
                .count();
    }

    private Field getField(final int colIndex) {
        final List<Field> fields = getFields();
        int index = fieldsStartIndex;
        for (Field field : fields) {
            if (field.isVisible()) {
                if (index == colIndex) {
                    return field;
                }
                index++;
            }
        }
        return null;
    }

    public void setFieldsStartIndex(final int fieldsStartIndex) {
        this.fieldsStartIndex = fieldsStartIndex;
    }

    private List<Item> getMenuItems(final Field field) {
        final List<Item> menuItems = new ArrayList<>();

        // Create rename menu.
        menuItems.add(createRenameMenu(field));
        // Create expression menu.
        menuItems.add(createExpressionMenu(field));
        // Create sort menu.
        menuItems.add(createSortMenu(field));
        // Create group by menu.
        menuItems.add(createGroupByMenu(field));
        // Create format menu.
        menuItems.add(createFormatMenu(field));
        // Add filter menu item.
        menuItems.add(createFilterMenu(field));

        // Create move menu.
        menuItems.add(createMoveFirstMenu(field));
        menuItems.add(createMoveLastMenu(field));

        // Create duplicate menu.
        menuItems.add(createDuplicateMenu(field));

        // Create hide menu.
        menuItems.add(createHideMenu(field));

        // Create show menu.
        Item showMenu = createShowMenu();
        if (showMenu != null) {
            menuItems.add(showMenu);
        }

        // Create remove menu.
        menuItems.add(createRemoveMenu(field));

        return menuItems;
    }

    private Item createRenameMenu(final Field field) {
        return new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.EDIT)
                .text("Rename")
                .command(() -> showRename(field))
                .build();
    }

    private Item createExpressionMenu(final Field field) {
        boolean highlight = false;
        if (field.getExpression() != null) {
            String expression = field.getExpression();
            expression = expression.replaceAll("\\$\\{[^\\{\\}]*\\}", "");
            expression = expression.trim();
            if (expression.length() > 0) {
                highlight = true;
            }
        }

        return new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.FIELDS_EXPRESSION)
                .text("Expression")
                .command(() -> showExpression(field))
                .highlight(highlight)
                .build();
    }

    private Item createSortMenu(final Field field) {
        final List<Item> menuItems = new ArrayList<>();
        menuItems.add(
                createSortOption(field,
                        0,
                        SvgImage.FIELDS_SORTAZ,
                        "Sort A to Z",
                        SortDirection.ASCENDING));
        menuItems.add(
                createSortOption(field,
                        1,
                        SvgImage.FIELDS_SORTZA,
                        "Sort Z to A",
                        SortDirection.DESCENDING));
        menuItems.add(createSortOption(field, 2, null, "Unsorted", null));
        return new IconParentMenuItem.Builder()
                .priority(2)
                .icon(SvgImage.FIELDS_SORTAZ)
                .text("Sort")
                .children(menuItems)
                .highlight(field.getSort() != null)
                .build();
    }

    private Item createSortOption(final Field field,
                                  final int pos,
                                  final SvgImage icon,
                                  final String text,
                                  final SortDirection sortDirection) {
        return new IconMenuItem.Builder()
                .priority(pos)
                .icon(icon)
                .text(text)
                .command(() -> changeSort(field, sortDirection))
                .highlight(field.getSort() != null && field.getSort().getDirection() == sortDirection)
                .build();
    }

    private Item createGroupByMenu(final Field field) {
        final List<Item> menuItems = new ArrayList<>();
        final int maxGroup = fixGroups(getFields());
        for (int i = 0; i < maxGroup; i++) {
            final int group = i;
            final Item item = new IconMenuItem.Builder()
                    .priority(i)
                    .icon(SvgImage.FIELDS_GROUP)
                    .text("Level " + (i + 1))
                    .command(() -> setGroup(field, group))
                    .highlight(field.getGroup() != null && field.getGroup() == i)
                    .build();
            menuItems.add(item);
        }

        // Add the next possible group if the field isn't the only field in the
        // next group.
        if (addNextGroup(maxGroup, field)) {
            final Item item = new IconMenuItem.Builder()
                    .priority(maxGroup)
                    .icon(SvgImage.FIELDS_GROUP)
                    .text("Level " + (maxGroup + 1))
                    .command(() -> setGroup(field, maxGroup))
                    .build();
            menuItems.add(item);
        }

        final Item item = new IconMenuItem.Builder()
                .priority(maxGroup + 1)
                .text("Not grouped")
                .command(() -> setGroup(field, null))
                .build();
        menuItems.add(item);

        return new IconParentMenuItem.Builder()
                .priority(3)
                .icon(SvgImage.FIELDS_GROUP)
                .text("Group")
                .children(menuItems)
                .highlight(field.getGroup() != null)
                .build();
    }

    private void setGroup(final Field field, final Integer group) {
        if (!Objects.equals(field.getGroup(), group)) {
            replaceField(field, field.copy().group(group).build());
            fixGroups(getFields());
            tablePresenter.setDirty(true);
            tablePresenter.updateColumns();
            tablePresenter.clear();
        }
    }

    private boolean addNextGroup(final int maxGroup, final Field field) {
        // If the field we are dragging has no group then add the next possible
        // group.
        if (field.getGroup() == null) {
            return true;
        }

        // If the group the dragging field belongs to is not the current max
        // group then add the next possible group.
        if (field.getGroup() < maxGroup - 1) {
            return true;
        }

        // If there is another field with the same group level as the dragging
        // group then add the next possible group.
        for (final Field fld : getFields()) {
            if (fld != field && fld.getGroup() != null && fld.getGroup() >= field.getGroup()) {
                return true;
            }
        }

        return false;
    }

    private int fixGroups(final List<Field> fields) {
        // Make a map that groups fields by group depth.
        final Map<Integer, List<Field>> map = new HashMap<>();
        for (final Field field : fields) {
            final Integer group = field.getGroup();
            if (group != null) {
                map.computeIfAbsent(group, k -> new ArrayList<>()).add(field);
            }
        }

        // Fix group depths and add fields to grouped fields list.
        final List<Integer> depths = new ArrayList<>(map.keySet());
        Collections.sort(depths);

        for (int i = 0; i < depths.size(); i++) {
            final Integer depth = depths.get(i);
            final List<Field> groupedFields = map.get(depth);
            for (final Field field : groupedFields) {
                replaceField(field, field.copy().group(i).build());
            }
        }

        return depths.size();
    }

    private Item createFilterMenu(final Field field) {
        return new IconMenuItem.Builder()
                .priority(4)
                .icon(SvgImage.FILTER)
                .disabledIcon(SvgImage.FILTER)
                .text("Filter")
                .command(() -> filterField(field))
                .highlight(field.getFilter() != null
                        && ((field.getFilter().getIncludes() != null
                        && field.getFilter().getIncludes().trim().length() > 0)
                        || (field.getFilter().getExcludes() != null
                        && field.getFilter().getExcludes().trim().length() > 0)))
                .build();
    }

    private Item createFormatMenu(final Field field) {
        return new IconMenuItem.Builder()
                .priority(5)
                .icon(SvgImage.FIELDS_FORMAT)
                .text("Format")
                .command(() -> showFormat(field))
                .highlight(field.getFormat() != null && field.getFormat().getSettings() != null
                        && !field.getFormat().getSettings().isDefault())
                .build();
    }

    private Item createMoveFirstMenu(final Field field) {
        return new IconMenuItem.Builder()
                .priority(6)
                .icon(SvgImage.STEP_BACKWARD)
                .text("Move First")
                .command(() -> moveFirst(field))
                .build();
    }

    private Item createMoveLastMenu(final Field field) {
        return new IconMenuItem.Builder()
                .priority(7)
                .icon(SvgImage.STEP_FORWARD)
                .text("Move Last")
                .command(() -> moveLast(field))
                .build();
    }

    private Item createDuplicateMenu(final Field field) {
        return new IconMenuItem.Builder()
                .priority(8)
                .icon(SvgImage.COPY)
                .text("Duplicate")
                .command(() -> duplicateField(field))
                .build();
    }

    private Item createHideMenu(final Field field) {
        return new IconMenuItem.Builder()
                .priority(9)
                .icon(SvgImage.HIDE)
                .text("Hide")
                .command(() -> hideField(field))
                .build();
    }

    private Item createShowMenu() {
        final List<Item> menuItems = new ArrayList<>();

        int i = 0;
        for (final Field field : getFields()) {
            if (!field.isVisible() && !field.isSpecial()) {
                final Item item2 = new IconMenuItem.Builder()
                        .priority(i++)
                        .icon(SvgImage.SHOW)
                        .text(field.getName())
                        .command(() -> showField(field))
                        .build();
                menuItems.add(item2);
            }
        }

        if (menuItems.size() == 0) {
            return null;
        }

        return new IconParentMenuItem.Builder()
                .priority(10)
                .icon(SvgImage.SHOW)
                .text("Show")
                .children(menuItems)
                .build();
    }

    private Item createRemoveMenu(final Field field) {
        return new IconMenuItem.Builder()
                .priority(11)
                .icon(SvgImage.DELETE)
                .text("Remove")
                .command(() -> deleteField(field))
                .build();
    }
}
