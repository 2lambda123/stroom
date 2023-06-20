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

package stroom.explorer.shared;

import stroom.docref.DocContentMatch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ExplorerDocContentMatch {

    @JsonProperty
    private final DocContentMatch docContentMatch;
    @JsonProperty
    private final String path;
    @JsonProperty
    private final DocumentIcon icon;
    @JsonProperty
    private final boolean isFavourite;

    @JsonCreator
    public ExplorerDocContentMatch(@JsonProperty("docContentMatch") final DocContentMatch docContentMatch,
                                   @JsonProperty("path") final String path,
                                   @JsonProperty("icon") final DocumentIcon icon,
                                   @JsonProperty("isFavourite") final boolean isFavourite) {
        this.docContentMatch = docContentMatch;
        this.path = path;
        this.icon = icon;
        this.isFavourite = isFavourite;
    }

    public DocContentMatch getDocContentMatch() {
        return docContentMatch;
    }

    public String getPath() {
        return path;
    }

    public DocumentIcon getIcon() {
        return icon;
    }

    public boolean getIsFavourite() {
        return isFavourite;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExplorerDocContentMatch that = (ExplorerDocContentMatch) o;
        return isFavourite == that.isFavourite && Objects.equals(docContentMatch,
                that.docContentMatch) && Objects.equals(path,
                that.path) && Objects.equals(icon, that.icon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docContentMatch, path, icon, isFavourite);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private DocContentMatch docContentMatch;
        private String path;
        private DocumentIcon icon;
        private boolean isFavourite;

        private Builder() {
        }

        private Builder(final ExplorerDocContentMatch explorerDocContentMatch) {
            this.docContentMatch = explorerDocContentMatch.docContentMatch;
            this.path = explorerDocContentMatch.path;
            this.icon = explorerDocContentMatch.icon;
            this.isFavourite = explorerDocContentMatch.isFavourite;
        }

        public Builder docContentMatch(final DocContentMatch docContentMatch) {
            this.docContentMatch = docContentMatch;
            return this;
        }

        public Builder path(final String path) {
            this.path = path;
            return this;
        }

        public Builder icon(final DocumentIcon icon) {
            this.icon = icon;
            return this;
        }

        public Builder isFavourite(final boolean isFavourite) {
            this.isFavourite = isFavourite;
            return this;
        }

        public ExplorerDocContentMatch build() {
            return new ExplorerDocContentMatch(
                    docContentMatch,
                    path,
                    icon,
                    isFavourite);
        }
    }
}