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

package stroom.streamstore.server.udload;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.BaseResultList;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.fs.serializable.NestedInputStream;
import stroom.streamstore.server.fs.serializable.RANestedInputStream;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogItemProgress;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Monitor;
import stroom.util.spring.StroomScope;
import stroom.util.task.MonitorImpl;
import stroom.util.task.TaskMonitor;
import stroom.util.thread.ThreadLocalBuffer;
import stroom.util.zip.StroomFileNameUtil;
import stroom.util.zip.StroomZipEntry;
import stroom.util.zip.StroomZipFileType;
import stroom.util.zip.StroomZipOutputStream;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

@TaskHandlerBean(task = StreamDownloadTask.class)
@Scope(value = StroomScope.TASK)
public class StreamDownloadTaskHandler extends AbstractTaskHandler<StreamDownloadTask, StreamDownloadResult> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StreamDownloadTaskHandler.class);

    private static final String AGGREGATION_DELIMITER = "_";

    @Resource
    private TaskMonitor taskMonitor;
    @Resource
    private StreamStore streamStore;
    @Resource(name = "prototypeThreadLocalBuffer")
    private ThreadLocalBuffer writeThreadLocalBuffer;

    @Override
    public StreamDownloadResult exec(final StreamDownloadTask task) {
        StreamDownloadResult result = null;
        taskMonitor.info(task.getFile().toString());

        result = downloadData(task, task.getCriteria(), task.getFile(), task.getSettings());

        return result;
    }

    private StreamDownloadResult downloadData(final StreamDownloadTask task, final FindStreamCriteria criteria,
                                              Path data, final StreamDownloadSettings settings) throws RuntimeException {
        final BaseResultList<Stream> list = streamStore.find(criteria);

        final StreamDownloadResult result = new StreamDownloadResult();

        if (list.size() == 0) {
            return result;
        }

        final Monitor zipProgressMonitor = new MonitorImpl(taskMonitor);
        final Monitor streamProgressMonitor = new MonitorImpl(taskMonitor);

        StroomZipOutputStream stroomZipOutputStream = null;
        try {
            stroomZipOutputStream = new StroomZipOutputStream(data.toFile(), zipProgressMonitor, false);

            long id = 0;
            long fileCount = 0;

            final String fileBasePath = data.toAbsolutePath().toString().substring(0, data.toAbsolutePath().toString().lastIndexOf(".zip"));

            final LogItemProgress logItemProgress = new LogItemProgress(0, list.size());
            streamProgressMonitor.info("Stream %s", logItemProgress);

            for (final Stream stream : list) {
                result.incrementRecordsWritten();
                logItemProgress.incrementProgress();

                id = downloadStream(streamProgressMonitor, stream.getId(), stroomZipOutputStream, id,
                        settings.getMaxFileParts());

                boolean startNewFile = false;
                boolean hitMaxFileSize = false;
                boolean hitMaxFileParts = false;
                if (settings.getMaxFileSize() != null
                        && stroomZipOutputStream.getProgressSize() > settings.getMaxFileSize()) {
                    startNewFile = true;
                    hitMaxFileSize = true;
                }
                if (settings.getMaxFileParts() != null
                        && stroomZipOutputStream.getEntryCount() > settings.getMaxFileParts()) {
                    startNewFile = true;
                    hitMaxFileParts = true;
                }
                if (startNewFile) {
                    if (!settings.isMultipleFiles()) {
                        // Process no more !
                        result.setHitMaxFileParts(hitMaxFileParts);
                        result.setHitMaxFileSize(hitMaxFileSize);
                        break;
                    } else {
                        stroomZipOutputStream.close();
                        fileCount++;
                        data = Paths.get(fileBasePath + "_" + fileCount + ".zip");
                        stroomZipOutputStream = new StroomZipOutputStream(data.toFile(), zipProgressMonitor, false);
                    }
                }

            }

            if (id == 0) {
                stroomZipOutputStream.closeDelete();
            } else {
                stroomZipOutputStream.close();
            }
            stroomZipOutputStream = null;

            return result;
        } catch (final IOException ioEx) {
            throw new RuntimeException(ioEx);
        } finally {
            closeDelete(data, stroomZipOutputStream);
            taskMonitor.info("done");
        }
    }

    private long downloadStream(final Monitor parentMonitor, final long streamId,
                                final StroomZipOutputStream stroomZipOutputStream, final long startId, final Long maxParts) throws IOException {
        long id = startId;

        final Monitor streamProgressMonitor = new MonitorImpl(parentMonitor);

        NestedInputStream contextInputStream = null;
        NestedInputStream metaInputStream = null;
        NestedInputStream dataInputStream = null;
        StreamSource dataSource = null;

        try {
            id++;

            // Export Source
            dataSource = streamStore.openStreamSource(streamId);
            final StreamSource metaSource = dataSource.getChildStream(StreamType.META);
            final StreamSource contextSource = dataSource.getChildStream(StreamType.CONTEXT);

            dataInputStream = new RANestedInputStream(dataSource);
            metaInputStream = null;
            if (metaSource != null) {
                metaInputStream = new RANestedInputStream(metaSource);
            }
            contextInputStream = null;
            if (contextSource != null) {
                contextInputStream = new RANestedInputStream(contextSource);
            }

            long entryProgress = 0;
            final long entryTotal = dataInputStream.getEntryCount();

            if (maxParts == null || entryTotal < maxParts) {
                long part = -1;
                if (dataInputStream.getEntryCount() > 1) {
                    part = 0;
                }
                while (dataInputStream.getNextEntry()) {
                    entryProgress++;

                    streamProgressMonitor.info("Stream Input %s/%s", entryProgress, entryTotal);

                    String basePartName = StroomFileNameUtil.getFilePathForId(id);

                    if (part != -1) {
                        part++;
                        basePartName += AGGREGATION_DELIMITER + part;
                    }

                    // Write out the manifest
                    if (part == 1 || part == -1) {
                        dataSource.getAttributeMap().write(stroomZipOutputStream
                                .addEntry(new StroomZipEntry(null, basePartName, StroomZipFileType.Manifest)), true);
                    }

                    // False here as the loop does the next next next
                    streamToStream(dataInputStream, stroomZipOutputStream, basePartName, StroomZipFileType.Data, false);

                    streamToStream(metaInputStream, stroomZipOutputStream, basePartName, StroomZipFileType.Meta, true);

                    streamToStream(contextInputStream, stroomZipOutputStream, basePartName, StroomZipFileType.Context, true);

                }
            }
        } finally {
            CloseableUtil.close(contextInputStream);
            CloseableUtil.close(metaInputStream);
            CloseableUtil.close(dataInputStream);
            CloseableUtil.close(dataSource);
        }
        return id;
    }

    private boolean streamToStream(final NestedInputStream nestedInputStream, final StroomZipOutputStream zipOutputStream,
                                   final String basePartName, final StroomZipFileType fileType, final boolean getNext) throws IOException {
        if (nestedInputStream != null) {
            if (getNext) {
                if (!nestedInputStream.getNextEntry()) {
                    return false;
                }
            }

            final StroomZipEntry stroomZipEntry = new StroomZipEntry(null, basePartName, fileType);
            final OutputStream outputStream = zipOutputStream.addEntry(stroomZipEntry);
            final byte[] buffer = writeThreadLocalBuffer.getBuffer();

            int len;
            while ((len = StreamUtil.eagerRead(nestedInputStream, buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.close();

            nestedInputStream.closeEntry();
            return true;
        }
        return false;
    }

    private void closeDelete(final Path data, final StroomZipOutputStream stroomZipOutputStream) {
        if (stroomZipOutputStream != null) {
            try {
                stroomZipOutputStream.closeDelete();
            } catch (final IOException e) {
                LOGGER.error("closeDelete() - Failed to delete " + data);
            }
        }
    }
}
