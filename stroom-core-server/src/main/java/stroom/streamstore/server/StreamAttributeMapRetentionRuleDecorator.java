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
 *
 */

package stroom.streamstore.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.ruleset.shared.DataRetentionRule;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamDataSource;
import stroom.util.date.DateUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public class StreamAttributeMapRetentionRuleDecorator {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamAttributeMapRetentionRuleDecorator.class);

    private final List<DataRetentionRule> rules;
    private final ExpressionMatcher expressionMatcher;

    public StreamAttributeMapRetentionRuleDecorator(final ExpressionMatcherFactory expressionMatcherFactory,
                                                    final List<DataRetentionRule> rules) {
        this.rules = rules;
        expressionMatcher = expressionMatcherFactory.create(StreamDataSource.getFieldMap());
    }

    public void addMatchingRetentionRuleInfo(final StreamAttributeMap streamAttributeMap) {
        try {
            int index = -1;

            // If there are no active rules then we aren't going to process anything.
            if (rules.size() > 0) {
                // Create an attribute map we can match on.
                final Map<String, Object> attributeMap = StreamAttributeMapUtil.createAttributeMap(streamAttributeMap);
                index = findMatchingRuleIndex(attributeMap);
            }

            if (index != -1) {
                final DataRetentionRule rule = rules.get(index);
                streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_AGE, rule.getAgeString());

                String keepUntil = DataRetentionRule.FOREVER;
                if (streamAttributeMap.getStream() != null) {
                    final long millis = streamAttributeMap.getStream().getCreateMs();
                    final LocalDateTime createTime = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDateTime();
                    final Long ms = DataRetentionAgeUtil.plus(createTime, rule);
                    if (ms != null) {
                        keepUntil = DateUtil.createNormalDateTimeString(ms);
                    }
                }

                streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_UNTIL, keepUntil);
                streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_RULE, rule.toString());
            } else {
                streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_AGE, DataRetentionRule.FOREVER);
                streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_UNTIL, DataRetentionRule.FOREVER);
                streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_RULE, "None");
            }
        } catch (final RuntimeException e) {
            streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_AGE, DataRetentionRule.FOREVER);
            streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_UNTIL, DataRetentionRule.FOREVER);
            streamAttributeMap.addAttribute(StreamAttributeConstants.RETENTION_RULE, "Error - " + e.getMessage());
        }
    }

    private int findMatchingRuleIndex(final Map<String, Object> attributeMap) {
        RuntimeException lastException = null;

        for (int i = 0; i < rules.size(); i++) {
            try {
                final DataRetentionRule rule = rules.get(i);
                // We will ignore rules that are not enabled or have no enabled expression.
                if (rule.isEnabled() && rule.getExpression() != null && rule.getExpression().enabled()) {
                    if (expressionMatcher.match(attributeMap, rule.getExpression())) {
                        return i;
                    }
                }
            } catch (final RuntimeException e) {
                lastException = e;
                LOGGER.debug(e.getMessage(), e);
            }
        }

        if (lastException != null) {
            throw lastException;
        }

        return -1;
    }
}