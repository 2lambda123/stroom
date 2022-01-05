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

package stroom.importexport.impl;

import stroom.security.api.SecurityContext;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestContentPackImport {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestContentPackImport.class);

    private static final Path CONTENT_PACK_IMPORT_DIR = Paths.get("contentPackImport");

    @Mock
    private ImportExportService importExportService;
    @Mock
    private ContentPackImportConfig contentPackImportConfig;
    @Mock
    private SecurityContext securityContext;

    private Path tempDir;
    private Path contentPackDir;

    private Path testPack1;
    private Path testPack2;
    private Path testPack3;

    @BeforeEach
    void setup(@TempDir final Path tempDir) throws IOException {
        this.tempDir = tempDir;

        contentPackDir = tempDir.resolve(CONTENT_PACK_IMPORT_DIR);

        Files.createDirectories(contentPackDir);

        testPack1 = contentPackDir.resolve("testPack1.zip");
        testPack2 = contentPackDir.resolve("testPack2.zip");
        testPack3 = contentPackDir.resolve("testPack3.badExtension");

        LOGGER.info("Using {} for content pack import", FileUtil.getCanonicalPath(contentPackDir));

        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(contentPackDir)) {
            stream.forEach(file -> {
                try {
                    if (Files.isRegularFile(file)) {
                        Files.deleteIfExists(file);
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(String.format("Error deleting files from %s",
                            contentPackDir.toAbsolutePath()), e);
                }
            });
        }
    }

    @AfterEach
    void teardown() throws IOException {
        deleteTestFiles();
    }

    private void setStandardMockAnswers() {
        Mockito.when(contentPackImportConfig.getImportDirectory())
                .thenReturn(contentPackDir.toAbsolutePath().toString());
        Mockito.when(contentPackImportConfig.isEnabled())
                .thenReturn(true);
        Mockito
                .doAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(0);
                    runnable.run();
                    return null;
                })
                .when(securityContext)
                .asAdminUser(Mockito.any(Runnable.class));
    }

    private void deleteTestFiles() throws IOException {
        Files.deleteIfExists(testPack1);
        Files.deleteIfExists(testPack2);
        Files.deleteIfExists(testPack3);
    }


    @Test
    void testStartup_disabled() throws IOException {
        Mockito.reset(contentPackImportConfig);

        Mockito.when(contentPackImportConfig.isEnabled()).thenReturn(false);
        ContentPackImport contentPackImport = getContentPackImport();

        FileUtil.touch(testPack1);

        contentPackImport.startup();

        Mockito.verifyNoInteractions(importExportService);
        assertThat(Files.exists(testPack1)).isTrue();
    }

    @Test
    void testStartup_enabledNoFiles() {
        setStandardMockAnswers();
        Mockito.when(contentPackImportConfig.isEnabled()).thenReturn(true);
        ContentPackImport contentPackImport = getContentPackImport();
        contentPackImport.startup();
        Mockito.verifyNoInteractions(importExportService);
    }

    @Test
    void testStartup_enabledNullDir() {
        Mockito.when(contentPackImportConfig.isEnabled())
                .thenReturn(true);
        Mockito.when(contentPackImportConfig.getImportDirectory())
                .thenReturn(null);

        ContentPackImport contentPackImport = getContentPackImport();
        contentPackImport.startup();
        Mockito.verifyNoInteractions(importExportService);
    }

    @Test
    void testStartup_invalidDir() {
        setStandardMockAnswers();
        Mockito.when(contentPackImportConfig.isEnabled())
                .thenReturn(true);
        Mockito.when(contentPackImportConfig.getImportDirectory())
                .thenReturn("/xxxxxxxxxxxxxxxx");

        ContentPackImport contentPackImport = getContentPackImport();
        contentPackImport.startup();
        Mockito.verifyNoInteractions(importExportService);
    }

    @Test
    void testStartup_enabledThreeFiles() throws IOException {
        setStandardMockAnswers();
        Mockito.when(contentPackImportConfig.isEnabled()).thenReturn(true);
        final ContentPackImport contentPackImport = getContentPackImport();

        FileUtil.touch(testPack1);
        FileUtil.touch(testPack2);
        FileUtil.touch(testPack3);

        contentPackImport.startup();
        Mockito.verify(importExportService, Mockito.times(1))
                .performImportWithoutConfirmation(testPack1);
        Mockito.verify(importExportService, Mockito.times(1))
                .performImportWithoutConfirmation(testPack2);
        //not a zip extension so should not be called
        Mockito.verify(importExportService, Mockito.times(0))
                .performImportWithoutConfirmation(testPack3);

        assertThat(Files.exists(testPack1))
                .isFalse();

        //File should have moved into the imported dir
        assertThat(Files.exists(testPack1))
                .isFalse();
        assertThat(Files.exists(
                contentPackDir
                        .resolve(ContentPackImport.IMPORTED_DIR)
                        .resolve(testPack1.getFileName())))
                .isTrue();
    }

    @Disabled // until we can change ContentPackImport to only import from the first
    // dir it finds or a specified list.
    @Test
    void testStartup_customLocation(@TempDir final Path tempDir) throws IOException {
        setStandardMockAnswers();
        Mockito.when(contentPackImportConfig.isEnabled()).thenReturn(true);
        Mockito.when(contentPackImportConfig.getImportDirectory())
                .thenReturn(tempDir.toAbsolutePath().toString());

        ContentPackImport contentPackImport = getContentPackImport();

        Path packFile = tempDir.resolve("testFile1.zip");
        FileUtil.touch(packFile);

        contentPackImport.startup();
        Mockito.verify(importExportService, Mockito.times(1))
                .performImportWithoutConfirmation(packFile);

        assertThat(Files.exists(packFile)).isFalse();

        //File should have moved into the imported dir
        assertThat(Files.exists(packFile)).isFalse();
        assertThat(Files.exists(tempDir.resolve(ContentPackImport.IMPORTED_DIR).resolve(packFile.getFileName())))
                .isTrue();
    }

    @Test
    void testStartup_failedImport() throws IOException {
        setStandardMockAnswers();
        Mockito.when(contentPackImportConfig.isEnabled()).thenReturn(true);
        ContentPackImport contentPackImport = getContentPackImport();

        Mockito.doThrow(new RuntimeException("Error thrown by mock import service for test"))
                .when(importExportService)
                .performImportWithoutConfirmation(ArgumentMatchers.any());

        FileUtil.touch(testPack1);

        contentPackImport.startup();

        //File should have moved into the failed dir
        assertThat(Files.exists(testPack1)).isFalse();
        assertThat(Files.exists(contentPackDir.resolve(ContentPackImport.FAILED_DIR).resolve(testPack1.getFileName())))
                .isTrue();
    }

    private ContentPackImport getContentPackImport() {
        final PathCreator pathCreator = new PathCreator(() -> tempDir, () -> tempDir);
        return new ContentPackImport(
                importExportService, contentPackImportConfig, securityContext, pathCreator);
    }
}
