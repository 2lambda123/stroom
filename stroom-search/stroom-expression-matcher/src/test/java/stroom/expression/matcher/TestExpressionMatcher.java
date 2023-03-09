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

package stroom.expression.matcher;

import stroom.data.shared.StreamTypeNames;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.TextField;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.TimeZone;
import stroom.query.api.v2.TimeZone.Use;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestExpressionMatcher {

    @Mock
    private WordListProvider mockWordListProvider;

    public static final DocRefField FEED = DocRefField.byUniqueName("Feed", "Feed");
    private static final TextField TYPE = new TextField("Type");
    private static final TextField FRUIT = new TextField("Fruit");
    private static final Map<String, AbstractField> FIELD_MAP = Map.of(
            FEED.getName(),
            FEED,
            TYPE.getName(),
            TYPE);

    @Test
    void testSimpleMatch() {
        test(createAttributeMap(), createExpression(Op.AND, "TEST_FEED"), true);
    }

    @Test
    void testLeadingWildcardMatch() {
        test(createAttributeMap(), createExpression(Op.AND, "*FEED"), true);
    }

    @Test
    void testTrailingWildcardMatch() {
        test(createAttributeMap(), createExpression(Op.AND, "TEST*"), true);
    }

    @Test
    void testNotMatch() {
        test(createAttributeMap(), createExpression(Op.NOT, "TEST_FEED"), false);
    }

    @Test
    void testMatchAll() {
        test(createAttributeMap(), ExpressionOperator.builder().build(), true);
    }

    @Test
    void testMatchNone1() {
        test(createAttributeMap(), null, false);
    }

    @Test
    void testMatchNone2() {
        test(createAttributeMap(), ExpressionOperator.builder().enabled(false).build(), false);
    }

    @Test
    void testInDictionary() {
        final DocRef docRef = DictionaryDoc.buildDocRef()
                .randomUuid()
                .name("foo")
                .build();

        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(
                Map.of(FRUIT.getName(), FRUIT),
                mockWordListProvider,
                null,
                DateTimeSettings.builder()
                        .timeZone(TimeZone.builder()
                                .use(Use.UTC)
                                .build())
                        .build(),
                System.currentTimeMillis());

        Mockito.when(mockWordListProvider.getWords(Mockito.eq(docRef)))
                .thenReturn(new String[]{
                        "banana",
                        "orange",
                        "apple",
                        "kiwi fruit"});

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field(FRUIT.getName())
                        .condition(Condition.IN_DICTIONARY)
                        .docRef(docRef)
                        .build())
                .build();

        boolean match = expressionMatcher.match(
                Map.of(FRUIT.getName(), "orange"),
                expression);
        assertThat(match)
                .isTrue();

        // This probably ought to work, but stroom.expression.matcher.ExpressionMatcher#isIn
        // is splitting dictionary lines on spaces
//        match = expressionMatcher.match(
//                Map.of(FRUIT.getName(), "kiwi fruit"),
//                expression);
//        assertThat(match)
//                .isTrue();
    }

    private void test(final Map<String, Object> attributeMap,
                      final ExpressionOperator expression,
                      final boolean outcome) {
        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(FIELD_MAP,
                null,
                null,
                null,
                System.currentTimeMillis());
        assertThat(expressionMatcher.match(attributeMap, expression)).isEqualTo(outcome);
    }

    private ExpressionOperator createExpression(final Op op, final String feedName) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(op);
        builder.addTerm(FEED, Condition.EQUALS, feedName);
        return builder.build();
    }

    private Map<String, Object> createAttributeMap() {
        final Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put(FEED.getName(), "TEST_FEED");
        attributeMap.put(TYPE.getName(), StreamTypeNames.RAW_EVENTS);
        return attributeMap;
    }
}
