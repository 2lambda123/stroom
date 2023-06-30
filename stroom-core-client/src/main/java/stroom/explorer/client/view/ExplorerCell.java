package stroom.explorer.client.view;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.explorer.client.presenter.TickBoxSelectionModel;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.svg.shared.SvgImage;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.SelectionModel;

public class ExplorerCell extends AbstractCell<ExplorerNode> {

    private static Template template;
    private final SelectionModel<ExplorerNode> selectionModel;
    private TickBoxCell tickBoxCell;

    public ExplorerCell(final SelectionModel<ExplorerNode> selectionModel) {
        this.selectionModel = selectionModel;

        if (selectionModel != null && selectionModel instanceof TickBoxSelectionModel) {
            tickBoxCell = TickBoxCell.create(true, false);
        }

        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    private String getCellClassName() {
        return "explorerCell";
    }

    public String getExpanderClassName() {
        return getCellClassName() + "-expander";
    }

    public String getTickBoxClassName() {
        return getCellClassName() + "-tickBox";
    }

    @Override
    public void render(final Context context, final ExplorerNode item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final SafeHtmlBuilder content = new SafeHtmlBuilder();

            int expanderPadding = 4;

            SafeHtml expanderIcon = SafeHtmlUtils.EMPTY_SAFE_HTML;
            if (item.getNodeState() != null) {
                switch (item.getNodeState()) {
                    case LEAF:
                        expanderIcon = SafeHtmlUtils.fromTrustedString("<svg></svg>");
                        break;
                    case OPEN:
                        expanderIcon = SafeHtmlUtils.fromTrustedString(SvgImage.ARROW_DOWN.getSvg());
                        break;
                    case CLOSED:
                        expanderIcon = SafeHtmlUtils.fromTrustedString(SvgImage.ARROW_RIGHT.getSvg());
                        break;
                    default:
                        throw new RuntimeException("Unexpected state " + item.getNodeState());
                }
            }

            int indent = item.getDepth();
            indent = expanderPadding + (indent * 17);
            final SafeStyles paddingLeft = SafeStylesUtils.fromTrustedString("padding-left:" + indent + "px;");

            // Add expander.
            content.append(template.expander(getCellClassName() + "-expander", paddingLeft, expanderIcon));

            if (tickBoxCell != null) {
                final SafeHtmlBuilder tb = new SafeHtmlBuilder();
                tickBoxCell.render(context, getValue(item), tb);

                final SafeHtml tickBoxHtml = template.div(getCellClassName() + "-tickBox", tb.toSafeHtml());
                // Add tickbox
                content.append(tickBoxHtml);
            }

            if (item.getIcon() != null) {
                // Add icon
                content.append(template.icon(getCellClassName() + "-icon",
                        item.getType(),
                        SafeHtmlUtils.fromSafeConstant(item.getIcon().getSvg())));
            }

            if (item.getDisplayValue() != null) {
                // Add text
                content.append(template.div(getCellClassName() + "-text",
                        SafeHtmlUtils.fromString(item.getDisplayValue())));
            }

            // If the item is a favourite and not part of the Favourites node, display a star next to it
            if (item.getIsFavourite() && item.getRootNodeUuid() != null &&
                    !ExplorerConstants.FAVOURITES_DOC_REF.getUuid().equals(item.getRootNodeUuid())) {
                content.append(template.favIcon("svgIcon small",
                        "Item is a favourite",
                        SafeHtmlUtils.fromSafeConstant(SvgImage.FAVOURITES.getSvg())));
            }

            sb.append(template.outer(content.toSafeHtml()));
        }
    }

    private TickBoxState getValue(final ExplorerNode item) {
        if (selectionModel == null) {
            return TickBoxState.UNTICK;
        } else if (selectionModel instanceof TickBoxSelectionModel) {
            final TickBoxSelectionModel tickBoxSelectionModel = (TickBoxSelectionModel) selectionModel;
            return tickBoxSelectionModel.getState(item);
        } else {
            if (selectionModel.isSelected(item)) {
                return TickBoxState.TICK;
            } else {
                return TickBoxState.UNTICK;
            }
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml expander(String iconClass, SafeStyles styles, SafeHtml icon);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml icon(String iconClass, String typeName, SafeHtml icon);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml favIcon(String iconClass, String title, SafeHtml icon);

        @Template("<div class=\"explorerCell\">{0}</div>")
        SafeHtml outer(SafeHtml content);
    }
}
