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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.List;

@JsonInclude(Include.NON_NULL)
public class DocumentTypes {

    public static final String[] FOLDER_TYPES = new String[]{
            ExplorerConstants.SYSTEM,
            ExplorerConstants.FOLDER
    };

    @JsonProperty
    private final List<DocumentType> types;
    @JsonProperty
    private final List<DocumentType> visibleTypes;

    @JsonCreator
    public DocumentTypes(@JsonProperty("types") final List<DocumentType> types,
                         @JsonProperty("visibleTypes") final List<DocumentType> visibleTypes) {
        this.types = types;
        this.visibleTypes = visibleTypes;
    }

    public List<DocumentType> getTypes() {
        return types;
    }

    public List<DocumentType> getVisibleTypes() {
        return visibleTypes;
    }

    public static boolean isFolder(final String type) {
        return Arrays.asList(FOLDER_TYPES).contains(type);
    }

    public static boolean isSystem(final String type) {
        return ExplorerConstants.SYSTEM.equals(type);
    }
}
