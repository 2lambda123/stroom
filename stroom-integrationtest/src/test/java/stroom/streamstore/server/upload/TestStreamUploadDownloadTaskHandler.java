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

package stroom.streamstore.server.upload;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestScenarioCreator;
import stroom.entity.shared.DocRef;
import stroom.feed.shared.Feed;
import stroom.streamstore.server.StreamAttributeValueFlush;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.server.fs.serializable.NestedStreamTarget;
import stroom.streamstore.server.udload.StreamDownloadSettings;
import stroom.streamstore.server.udload.StreamDownloadTask;
import stroom.streamstore.server.udload.StreamUploadTask;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.task.server.TaskManager;
import stroom.util.io.StreamUtil;
import stroom.util.task.ServerTask;
import stroom.util.zip.StroomZipFile;
import stroom.util.zip.StroomZipFileType;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TestStreamUploadDownloadTaskHandler extends AbstractCoreIntegrationTest {
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private StreamStore streamStore;
    @Resource
    private StreamAttributeValueFlush streamAttributeValueFlush;
    @Resource
    private TaskManager taskManager;

    @Test
    public void testDownload() throws IOException {
        final Feed feed = commonTestScenarioCreator.createSimpleFeed();
        commonTestScenarioCreator.createSample2LineRawFile(feed, StreamType.RAW_EVENTS);
        commonTestScenarioCreator.createSample2LineRawFile(feed, StreamType.RAW_EVENTS);

        final Path file = Files.createTempFile(getCurrentTestPath(), "TestStreamDownloadTaskHandler", ".zip");
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed.getId());
        final StreamDownloadSettings streamDownloadSettings = new StreamDownloadSettings();

        Assert.assertEquals(2, streamStore.find(findStreamCriteria).size());

        taskManager.exec(new StreamDownloadTask(ServerTask.INTERNAL_PROCESSING_USER_TOKEN, findStreamCriteria, file, streamDownloadSettings));

        Assert.assertEquals(2, streamStore.find(findStreamCriteria).size());

        final StroomZipFile stroomZipFile = new StroomZipFile(file.toFile());
        Assert.assertTrue(stroomZipFile.containsEntry("001", StroomZipFileType.Manifest));
        Assert.assertTrue(stroomZipFile.containsEntry("001", StroomZipFileType.Data));
        Assert.assertFalse(stroomZipFile.containsEntry("001", StroomZipFileType.Context));
        Assert.assertFalse(stroomZipFile.containsEntry("001", StroomZipFileType.Meta));
        stroomZipFile.close();

        taskManager.exec(new StreamUploadTask(ServerTask.INTERNAL_PROCESSING_USER_TOKEN, "test.zip", file, DocRef.create(feed),
                DocRef.create(StreamType.RAW_EVENTS), null, null));

        Assert.assertEquals(4, streamStore.find(findStreamCriteria).size());
    }

    @Test
    public void testUploadFlatFile() throws IOException {
        final Feed feed = commonTestScenarioCreator.createSimpleFeed();
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed.getId());

        final Path file = Files.createTempFile(getCurrentTestPath(), "TestStreamDownloadTaskHandler", ".dat");
        Files.write(file, "TEST".getBytes());

        taskManager.exec(new StreamUploadTask(ServerTask.INTERNAL_PROCESSING_USER_TOKEN, "test.dat", file, DocRef.create(feed),
                DocRef.create(StreamType.RAW_EVENTS), null, "Tom:One\nJames:Two\n"));

        Assert.assertEquals(1, streamStore.find(findStreamCriteria).size());
    }

    @Test
    public void testDownloadNestedComplex() throws IOException {
        final Path file = Files.createTempFile(getCurrentTestPath(), "TestStreamDownloadTaskHandler", ".zip");
        final Feed feed = commonTestScenarioCreator.createSimpleFeed();

        final StreamTarget streamTarget = streamStore
                .openStreamTarget(Stream.createStream(StreamType.RAW_EVENTS, feed, null));

        final NestedStreamTarget nestedStreamTarget = new NestedStreamTarget(streamTarget, true);

        nestedStreamTarget.putNextEntry();
        nestedStreamTarget.getOutputStream().write("DATA1".getBytes(StreamUtil.DEFAULT_CHARSET));
        nestedStreamTarget.closeEntry();

        nestedStreamTarget.putNextEntry(StreamType.META);
        nestedStreamTarget.getOutputStream(StreamType.META).write("META:1\nX:1\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        nestedStreamTarget.closeEntry(StreamType.META);

        nestedStreamTarget.putNextEntry(StreamType.CONTEXT);
        nestedStreamTarget.getOutputStream(StreamType.CONTEXT).write("CONTEXT1".getBytes(StreamUtil.DEFAULT_CHARSET));
        nestedStreamTarget.closeEntry(StreamType.CONTEXT);

        nestedStreamTarget.putNextEntry();
        nestedStreamTarget.getOutputStream().write("DATA2".getBytes(StreamUtil.DEFAULT_CHARSET));
        nestedStreamTarget.closeEntry();

        nestedStreamTarget.putNextEntry(StreamType.META);
        nestedStreamTarget.getOutputStream(StreamType.META).write("META:2\nY:2\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        nestedStreamTarget.closeEntry(StreamType.META);

        nestedStreamTarget.putNextEntry(StreamType.CONTEXT);
        nestedStreamTarget.getOutputStream(StreamType.CONTEXT).write("CONTEXT2".getBytes(StreamUtil.DEFAULT_CHARSET));
        nestedStreamTarget.closeEntry(StreamType.CONTEXT);

        nestedStreamTarget.close();

        streamStore.closeStreamTarget(streamTarget);

        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed.getId());
        final StreamDownloadSettings streamDownloadSettings = new StreamDownloadSettings();

        Assert.assertEquals(1, streamStore.find(findStreamCriteria).size());

        taskManager.exec(new StreamDownloadTask(ServerTask.INTERNAL_PROCESSING_USER_TOKEN, findStreamCriteria, file, streamDownloadSettings));

        final StroomZipFile stroomZipFile = new StroomZipFile(file.toFile());
        Assert.assertTrue(stroomZipFile.containsEntry("001_1", StroomZipFileType.Manifest));
        Assert.assertTrue(stroomZipFile.containsEntry("001_1", StroomZipFileType.Meta));
        Assert.assertTrue(stroomZipFile.containsEntry("001_1", StroomZipFileType.Context));
        Assert.assertTrue(stroomZipFile.containsEntry("001_1", StroomZipFileType.Data));
        Assert.assertTrue(stroomZipFile.containsEntry("001_2", StroomZipFileType.Meta));
        Assert.assertTrue(stroomZipFile.containsEntry("001_2", StroomZipFileType.Context));
        Assert.assertTrue(stroomZipFile.containsEntry("001_2", StroomZipFileType.Data));
        stroomZipFile.close();

        final String extraMeta = "Z:ALL\n";

        taskManager.exec(new StreamUploadTask(ServerTask.INTERNAL_PROCESSING_USER_TOKEN, "test.zip", file, DocRef.create(feed),
                DocRef.create(StreamType.RAW_EVENTS), null, extraMeta));

        final List<Stream> streamList = streamStore.find(findStreamCriteria);

        Assert.assertEquals(2, streamList.size());

        final Stream originalStream = streamTarget.getStream();

        streamAttributeValueFlush.flush();

        for (final Stream stream : streamList) {
            Assert.assertEquals(StreamStatus.UNLOCKED, stream.getStatus());
            final StreamSource streamSource = streamStore.openStreamSource(stream.getId());

            Assert.assertEquals("DATA1DATA2", StreamUtil.streamToString(streamSource.getInputStream(), false));
            Assert.assertEquals("CONTEXT1CONTEXT2",
                    StreamUtil.streamToString(streamSource.getChildStream(StreamType.CONTEXT).getInputStream(), false));

            if (originalStream.equals(stream)) {
                assertContains(
                        StreamUtil.streamToString(streamSource.getChildStream(StreamType.META).getInputStream(), false),
                        "META:1", "X:1", "META:2", "Y:2");
            } else {
                assertContains(
                        StreamUtil.streamToString(streamSource.getChildStream(StreamType.META).getInputStream(), false),
                        "Compression:ZIP\n", "META:1\n", "X:1\n", "Z:ALL\n", "Compression:ZIP\n", "META:2\n", "Y:2\n",
                        "Z:ALL\n");
            }

            streamStore.closeStreamSource(streamSource);
        }
    }

    private void assertContains(final String str, final String... testList) {
        for (final String test : testList) {
            Assert.assertTrue("Expecting " + str + " to contain " + test, str.contains(test));
        }
    }
}
