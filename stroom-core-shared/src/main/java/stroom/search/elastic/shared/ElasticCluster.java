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

package stroom.search.elastic.shared;

import stroom.docstore.shared.Doc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "connection"})
public class ElasticCluster extends Doc {
    public static final String ENTITY_TYPE = "ElasticCluster";

    private static final long serialVersionUID = 1L;

    private String description;
    private ElasticConnectionConfig connectionConfig = new ElasticConnectionConfig();

    public ElasticCluster() { }

    public String getDescription() { return description; }

    public void setDescription(final String description) { this.description = description; }

    @JsonProperty("connection")
    public ElasticConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    @JsonProperty("connection")
    public void setConnectionConfig(final ElasticConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ElasticCluster)) return false;
        if (!super.equals(o)) return false;
        final ElasticCluster elasticCluster = (ElasticCluster) o;
        return Objects.equals(description, elasticCluster.description) &&
                Objects.equals(connectionConfig, elasticCluster.connectionConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, connectionConfig);
    }

    @Override
    public String toString() {
        return "ElasticCluster{" +
                "description='" + description + '\'' +
                ", connectionConfig=" + connectionConfig +
                '}';
    }
}