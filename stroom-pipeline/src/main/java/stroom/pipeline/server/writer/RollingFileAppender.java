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

package stroom.pipeline.server.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingFileDestination;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.io.FileUtil;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Joins text instances into a single text instance.
 */
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(
        type = "RollingFileAppender",
        category = Category.DESTINATION,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = ElementIcons.FILES)
class RollingFileAppender extends AbstractRollingAppender {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollingFileAppender.class);

    private final PathCreator pathCreator;

    private String[] outputPaths;
    private String fileNamePattern;
    private String rolledFileNamePattern;
    private boolean useCompression;
    private String filePermissions;

    private String dir;
    private String fileName;
    private String rolledFileName;
    private String key;

    @Inject
    RollingFileAppender(final PathCreator pathCreator) {
        this.pathCreator = pathCreator;
    }

    @Override
    public RollingDestination createDestination() throws IOException {
        String dir = this.dir;
        String fileName = this.fileName;

        dir = PathCreator.replaceTimeVars(dir);
        dir = PathCreator.replaceUUIDVars(dir);

        fileName = PathCreator.replaceTimeVars(fileName);
        fileName = PathCreator.replaceUUIDVars(fileName);

        // Create a new destination.
        final Path file = Paths.get(dir).resolve(fileName);

        // Try and create the path.
        final Path parentDir = file.getParent();
        final Set<PosixFilePermission> permissions = parsePosixFilePermissions(filePermissions);
        if (!Files.isDirectory(parentDir)) {
            try {
                Files.createDirectories(parentDir);

                // Set permissions on the created directory
                if (permissions != null) {
                    Files.setPosixFilePermissions(parentDir, permissions);
                }
            } catch (final IOException e) {
                throw new ProcessException("Unable to create output dirs: " + FileUtil.getCanonicalPath(parentDir));
            }
        }

        return new RollingFileDestination(
                key,
                getFrequency(),
                getSchedule(),
                getRollSize(),
                System.currentTimeMillis(),
                fileName,
                rolledFileName,
                parentDir,
                file,
                useCompression,
                permissions
        );
    }

    @Override
    Object getKey() throws IOException {
        try {
            // Create the current file name if one isn't set.
            if (key == null) {
                dir = getRandomOutputPath();
                dir = pathCreator.replaceContextVars(dir);
                dir = PathCreator.replaceSystemProperties(dir);

                fileName = fileNamePattern;
                fileName = pathCreator.replaceContextVars(fileName);
                fileName = PathCreator.replaceSystemProperties(fileName);

                rolledFileName = rolledFileNamePattern;
                rolledFileName = pathCreator.replaceContextVars(rolledFileName);
                rolledFileName = PathCreator.replaceSystemProperties(rolledFileName);

                key = dir + '/' + fileName;
            }

            return key;
        } catch (final IOException e) {
            throw e;
        } catch (final Throwable t) {
            throw new IOException(t.getMessage(), t);
        }
    }

    private String getRandomOutputPath() throws IOException {
        if (outputPaths == null || outputPaths.length == 0) {
            throw new IOException("No output paths have been set");
        }

        // Get a path to use.
        String path;
        if (outputPaths.length == 1) {
            path = outputPaths[0];
        } else {
            // Choose one of the output paths at random.
            path = outputPaths[(int) Math.round(Math.random() * (outputPaths.length - 1))];
        }

        return path;
    }

    /**
     * Parses a POSIX-style file permission string like "rwxr--r--"
     */
    private static Set<PosixFilePermission> parsePosixFilePermissions(final String filePermissions) {
        if (filePermissions == null || filePermissions.isEmpty()) {
            return null;
        }

        try {
            return PosixFilePermissions.fromString(filePermissions);
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Invalid file permissions format: '" + filePermissions + "'");
            return null;
        }
    }

    @Override
    void validateSpecificSettings() {
        if (outputPaths == null || outputPaths.length == 0) {
            throw new ProcessException("No output paths have been specified");
        }

        if (fileNamePattern == null || fileNamePattern.length() == 0) {
            throw new ProcessException("No file name has been specified");
        }

        if (rolledFileNamePattern == null || rolledFileNamePattern.length() == 0) {
            throw new ProcessException("No rolled file name has been specified");
        }

        if (fileNamePattern.equals(rolledFileNamePattern)) {
            throw new ProcessException("File name and rolled file name cannot be the same");
        }
    }

    @PipelineProperty(description = "One or more destination paths for output files separated with commas. Replacement variables can be used in path strings such as ${feed}.")
    public void setOutputPaths(final String outputPaths) {
        this.outputPaths = outputPaths.split(",");
    }

    @PipelineProperty(description = "Choose the name of the file to write.")
    public void setFileName(final String fileNamePattern) {
        this.fileNamePattern = fileNamePattern;
    }

    @PipelineProperty(description = "Choose the name that files will be renamed to when they are rolled.")
    public void setRolledFileName(final String rolledFileNamePattern) {
        this.rolledFileNamePattern = rolledFileNamePattern;
    }

    @PipelineProperty(description = "Apply GZIP compression to output files", defaultValue = "false")
    public void setUseCompression(final boolean useCompression) { this.useCompression = useCompression; }

    @PipelineProperty(description = "Set file system permissions of finished files (example: 'rwxr--r--')")
    public void setFilePermissions(final String filePermissions) { this.filePermissions = filePermissions; }

    @PipelineProperty(description = "Choose how frequently files are rolled.", defaultValue = "1h")
    public void setFrequency(final String frequency) {
        super.setFrequency(frequency);
    }

    @PipelineProperty(description = "Provide a cron expression to determine when files are rolled.")
    public void setSchedule(final String expression) {
        super.setSchedule(expression);
    }

    @PipelineProperty(description = "When the current output file exceeds this size it will be closed and a new one created, e.g. 10M, 1G.", defaultValue = "100M")
    public void setRollSize(final String rollSize) {
        super.setRollSize(rollSize);
    }
}