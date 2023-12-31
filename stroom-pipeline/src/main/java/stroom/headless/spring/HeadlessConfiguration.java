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

package stroom.headless.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Defines the component scanning required for the server module.
 * <p>
 * Defined separately from the main configuration so it can be easily
 * overridden.
 */
@Configuration
@ComponentScan(basePackages = {
        "stroom.cache",
        "stroom.datafeed",
        "stroom.dictionary",
        "stroom.entity",
        "stroom.feed",
        "stroom.folder",
        "stroom.importexport",
        "stroom.io",
        "stroom.jobsystem",
        "stroom.lifecycle",
        "stroom.node",
        "stroom.pipeline",
        "stroom.pool",
        "stroom.process",
        "stroom.resource",
        "stroom.spring",
        "stroom.streamstore",
        "stroom.streamtask",
        "stroom.task",
        "stroom.util",
        "stroom.volume",
        "stroom.xmlschema",
        "stroom.headless"
}, excludeFilters = {
        // Exclude other configurations that might be found accidentally during
        // a component scan as configurations should be specified explicitly.
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),})
public class HeadlessConfiguration {
}
