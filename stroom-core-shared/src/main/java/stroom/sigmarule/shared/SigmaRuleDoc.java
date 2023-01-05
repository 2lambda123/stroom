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

package stroom.sigmarule.shared;

import stroom.docstore.shared.Doc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "sigmaRule"})
@JsonInclude(Include.NON_NULL)
public class SigmaRuleDoc extends Doc {

    public static final String DOCUMENT_TYPE = "SigmaRule";

    @JsonProperty
    private String description;
    @JsonProperty
    private String sigmaRule;

    public SigmaRuleDoc() {
    }

    @JsonCreator
    public SigmaRuleDoc(@JsonProperty("type") final String type,
                        @JsonProperty("uuid") final String uuid,
                        @JsonProperty("name") final String name,
                        @JsonProperty("version") final String version,
                        @JsonProperty("createTimeMs") final Long createTimeMs,
                        @JsonProperty("updateTimeMs") final Long updateTimeMs,
                        @JsonProperty("createUser") final String createUser,
                        @JsonProperty("updateUser") final String updateUser,
                        @JsonProperty("description") final String description,
                        @JsonProperty("sigmaRule") final String sigmaRule) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.sigmaRule = sigmaRule;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getSigmaRule() {
        return sigmaRule;
    }

    public void setSigmaRule(final String sigmaRule) {
        this.sigmaRule = sigmaRule;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final SigmaRuleDoc that = (SigmaRuleDoc) o;
        return Objects.equals(sigmaRule, that.sigmaRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sigmaRule);
    }
}