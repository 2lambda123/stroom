/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.expression.v1;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionParserAggregates extends AbstractExpressionParserTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestExpressionParserAggregates.class);

    @Test
    void testMin1() {
        createGenerator("min(${val1})", gen -> {
            gen.set(getVals(300D));
            gen.set(getVals(180D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(180D, Offset.offset(0D));

            gen.set(getVals(500D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(180D, Offset.offset(0D));

            gen.set(getVals(600D));
            gen.set(getVals(13D));
            gen.set(getVals(99.3D));
            gen.set(getVals(87D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(13D, Offset.offset(0D));
        });
    }

    @Test
    void testMinUngrouped2() {
        createGenerator("min(${val1}, 100, 30, 8)", gen -> {
            gen.set(getVals(300D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testMinGrouped2() {
        createGenerator("min(min(${val1}), 100, 30, 8)", gen -> {
            gen.set(getVals(300D));
            gen.set(getVals(180D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testMin3() {
        createGenerator("min(min(${val1}), 100, 30, 8, count(), 55)", gen -> {
            gen.set(getVals(300D));
            gen.set(getVals(180D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));

            gen.set(getVals(300D));
            gen.set(getVals(180D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testMax1() {
        createGenerator("max(${val1})", gen -> {
            gen.set(getVals(300D));
            gen.set(getVals(180D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300D, Offset.offset(0D));

            gen.set(getVals(500D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(500D, Offset.offset(0D));

            gen.set(getVals(600D));
            gen.set(getVals(13D));
            gen.set(getVals(99.3D));
            gen.set(getVals(87D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(600D, Offset.offset(0D));
        });
    }

    @Test
    void testMaxUngrouped2() {
        createGenerator("max(${val1}, 100, 30, 8)", gen -> {
            gen.set(getVals(10D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(100D, Offset.offset(0D));
        });
    }

    @Test
    void testMaxGrouped2() {
        createGenerator("max(max(${val1}), 100, 30, 8)", gen -> {
            gen.set(getVals(10D));
            gen.set(getVals(40D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(100D, Offset.offset(0D));
        });
    }

    @Test
    void testMax3() {
        createGenerator("max(max(${val1}), count())", gen -> {
            gen.set(getVals(3D));
            gen.set(getVals(2D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));

            gen.set(getVals(1D));
            gen.set(getVals(1D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testSum() {
        // This is a bad usage of functions as ${val1} will produce the last set
        // value when we evaluate the sum. As we are effectively grouping and we
        // don't have any control over the order that cell values are inserted
        // we will end up with indeterminate behaviour.
        createGenerator("sum(${val1}, count())", gen -> {
            gen.set(getVals(3D));
            gen.set(getVals(2D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

            gen.set(getVals(1D));
            gen.set(getVals(1D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(5D, Offset.offset(0D));
        });
    }

    @Test
    void testSumOfSum() {
        createGenerator("sum(sum(${val1}), count())", gen -> {
            gen.set(getVals(3D));
            gen.set(getVals(2D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(7D, Offset.offset(0D));

            gen.set(getVals(1D));
            gen.set(getVals(1D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(11D, Offset.offset(0D));
        });
    }

    @Test
    void testAverageUngrouped() {
        // This is a bad usage of functions as ${val1} will produce the last set
        // value when we evaluate the sum. As we are effectively grouping and we
        // don't have any control over the order that cell values are inserted
        // we will end up with indeterminate behaviour.
        createGenerator("average(${val1}, count())", gen -> {
            gen.set(getVals(3D));
            gen.set(getVals(4D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));

            gen.set(getVals(1D));
            gen.set(getVals(8D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(6D, Offset.offset(0D));
        });
    }

    @Test
    void testAverageGrouped() {
        createGenerator("average(${val1})", gen -> {
            gen.set(getVals(3D));
            gen.set(getVals(4D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3.5D, Offset.offset(0D));

            gen.set(getVals(1D));
            gen.set(getVals(8D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testVariance1() {
        createGenerator("variance(600, 470, 170, 430, 300)", gen -> {
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(21704D, Offset.offset(0D));
        });
    }

    @Test
    void testVariance2() {
        createGenerator("variance(${val1})", gen -> {
            gen.set(getVals(600));
            gen.set(getVals(470));
            gen.set(getVals(170));
            gen.set(getVals(430));
            gen.set(getVals(300));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(21704D, Offset.offset(0D));
        });
    }

    @Test
    void testStDev1() {
        createGenerator("round(stDev(600, 470, 170, 430, 300))", gen -> {
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(147, Offset.offset(0D));
        });
    }

    @Test
    void testStDev2() {
        createGenerator("round(stDev(${val1}))", gen -> {
            gen.set(getVals(600));
            gen.set(getVals(470));
            gen.set(getVals(170));
            gen.set(getVals(430));
            gen.set(getVals(300));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(147, Offset.offset(0D));
        });
    }
}
