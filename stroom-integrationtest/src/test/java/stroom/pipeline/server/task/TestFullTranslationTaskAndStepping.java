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

package stroom.pipeline.server.task;

import org.joda.time.Period;
import org.junit.Test;
import stroom.test.StroomProcessTestFileUtil;
import stroom.util.logging.StroomLogger;

import java.io.File;

public class TestFullTranslationTaskAndStepping extends TranslationTest {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestFullTranslationTaskAndStepping.class);

    @Override
    protected boolean doSingleSetup() {
        testTranslationTask(false);
        return true;
    }

    @Test
    public void testDataSplitterEvents() throws Exception {
        testStepping("DATA_SPLITTER-EVENTS");
    }

    @Test
    public void testFileToLocationReference() throws Exception {
        testStepping("FILENO_TO_LOCATION-REFERENCE");
    }

    @Test
    public void testJSONEvents() throws Exception {
        testStepping("JSON-EVENTS");
    }

    @Test
    public void testRawStreamingEvents() throws Exception {
        testStepping("RAW_STREAMING-EVENTS");
    }

    @Test
    public void testRawStreamingForkEvents() throws Exception {
        testStepping("RAW_STREAMING_FORK-EVENTS");
    }

    @Test
    public void testXMLFragmentEvents() throws Exception {
        testStepping("XML_FRAGMENT-EVENTS");
    }

    @Test
    public void testXMLEvents() throws Exception {
        testStepping("XML-EVENTS");
    }

    @Test
    public void testZipTestDataSplitterEvents() throws Exception {
        testStepping("ZIP_TEST-DATA_SPLITTER-EVENTS");
    }

    private void testStepping(final String feedName) throws Exception {
        final File outDir = new File(StroomProcessTestFileUtil.getTestResourcesDir(),
                "TestFullTranslationTaskAndStepping");

        final long time = System.currentTimeMillis();
        testSteppingTask(feedName, outDir);
        final long steppingTime = System.currentTimeMillis() - time;

        LOGGER.info(feedName + " TRANSLATION STEPPING TOOK: " + new Period(steppingTime).toString());
    }
}
