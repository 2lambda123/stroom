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

package stroom.servlet;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.springframework.stereotype.Component;
import stroom.feed.client.DataUploadService;
import stroom.logging.StreamEventLog;
import stroom.util.io.StreamUtil;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.PropertyMap;
import stroom.util.shared.ResourceKey;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic Import Service
 */
@Component(ImportFileServlet.BEAN_NAME)
public final class ImportFileServlet extends HttpServlet implements DataUploadService {
    public static final String BEAN_NAME = "importFileServlet";

    private static final StroomLogger LOGGER = StroomLogger.getLogger(ImportFileServlet.class);

    private static final long serialVersionUID = 487567988479000995L;

    private final SessionResourceStore sessionResourceStore;
    private final StreamEventLog streamEventLog;

    @Inject
    ImportFileServlet(final SessionResourceStore sessionResourceStore, final StreamEventLog streamEventLog) {
        this.sessionResourceStore = sessionResourceStore;
        this.streamEventLog = streamEventLog;
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain;charset=UTF-8");

        final PropertyMap propertyMap = new PropertyMap();
        propertyMap.setSuccess(false);

        try {
            // Parse the request and populate a map of file items.
            final Map<String, FileItem> items = getFileItems(request);
            if (items.size() == 0) {
                response.getWriter().write(propertyMap.toArgLine());
                return;
            }

            final FileItem fileItem = items.get("fileUpload");
            final InputStream inputStream = fileItem.getInputStream();

            final ResourceKey uuid = sessionResourceStore.createTempFile(fileItem.getName());
            final Path file = sessionResourceStore.getTempFile(uuid);
            streamEventLog.importStream(new Date(), "Import", file.toAbsolutePath().toString(), null);

            StreamUtil.streamToStream(inputStream, Files.newOutputStream(file));

            propertyMap.setSuccess(true);
            uuid.write(propertyMap);
            fileItem.delete();

        } catch (final Throwable t) {
            streamEventLog.importStream(new Date(), "Import", null, t);
            LOGGER.error(t.getMessage(), t);
            propertyMap.put("exception", t.getMessage());
        }

        response.getWriter().write(propertyMap.toArgLine());
    }

    private Map<String, FileItem> getFileItems(final HttpServletRequest request) {
        final Map<String, FileItem> fields = new HashMap<>();
        final FileItemFactory factory = new DiskFileItemFactory();
        final ServletFileUpload upload = new ServletFileUpload(factory);

        try {
            final List<?> items = upload.parseRequest(request);
            for (final Object o : items) {
                final FileItem item = (FileItem) o;
                fields.put(item.getFieldName(), item);
            }
        } catch (final FileUploadException e) {
            LOGGER.error(e, e);
        }

        return fields;
    }
}
