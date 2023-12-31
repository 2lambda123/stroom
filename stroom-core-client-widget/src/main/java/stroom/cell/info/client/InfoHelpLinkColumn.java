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

package stroom.cell.info.client;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import stroom.widget.button.client.GlyphIcon;
import stroom.widget.button.client.GlyphIcons;

public abstract class InfoHelpLinkColumn<T> extends Column<T, GlyphIcon> {
    public InfoHelpLinkColumn() {
        super(new FACell());
    }

    @Override
    public GlyphIcon getValue(T object) {
        return GlyphIcons.HELP;
    }

    protected String formatAnchor(String name) {
        return "#" + name.replace(" ", "_");
    }

    @Override
    public void onBrowserEvent(final Context context, final Element elem, final T row, final NativeEvent event) {
        super.onBrowserEvent(context, elem, row, event);
        showHelp(row);
    }

    protected abstract void showHelp(final T row);
}
