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
package stroom.util;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestByteSizeUnit {

    private static final double ASSERT_DELTA = 0.0005;

    @Test
    public void testFromShortName() {
        assertEquals(ByteSizeUnit.MEBIBYTE, ByteSizeUnit.fromShortName("MiB"));
        assertEquals(ByteSizeUnit.MEBIBYTE, ByteSizeUnit.fromShortName("mib"));
    }

    @Test
    public void convertMiBToB() {
        double bytes = ByteSizeUnit.fromShortName("MiB").convert(5, ByteSizeUnit.BYTE);

        Assert.assertEquals(5* ByteSizeUnit.MEBIBYTE.intBytes(), bytes, ASSERT_DELTA);
    }

    @Test
    public void convertBToKiB() {
        double bytes = ByteSizeUnit.fromShortName("B").convert(100, ByteSizeUnit.KIBIBYTE);

        Assert.assertEquals((double)100 / ByteSizeUnit.KIBIBYTE.intBytes(), bytes, ASSERT_DELTA);
    }

    @Test
    public void fromBytes(){
        Assert.assertEquals(ByteSizeUnit.KIBIBYTE, ByteSizeUnit.fromBytes(1024));
    }

    @Test
    public void unitValue(){
        Assert.assertEquals(1, ByteSizeUnit.KIBIBYTE.unitValue(1024), ASSERT_DELTA);
    }

}