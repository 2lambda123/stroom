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

import org.springframework.stereotype.Component;
import stroom.datafeed.server.DataFeedService;
import stroom.node.shared.ClientProperties;
import stroom.node.shared.ClientPropertiesService;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.NodeService;
import stroom.node.shared.Volume;
import stroom.node.shared.Volume.VolumeType;
import stroom.node.shared.Volume.VolumeUseStatus;
import stroom.node.shared.VolumeService;
import stroom.node.shared.VolumeState;
import stroom.pool.SecurityHelper;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.util.task.ServerTask;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * <p>
 * SERVLET that reports status of Stroom for scripting purposes.
 * </p>
 */
@Component(StatusServlet.BEAN_NAME)
public class StatusServlet extends HttpServlet implements DataFeedService {
    public static final String BEAN_NAME = "statusServlet";

    private static final long serialVersionUID = 1L;

    private transient NodeService nodeService;
    private transient ClientPropertiesService clientPropertiesService;
    private transient VolumeService volumeService;
    private transient SecurityContext securityContext;

    private static final String INFO = "INFO";
    private static final String WARN = "WARN";
    private static final String ERROR = "ERROR";
    private static final String DELIMITER = ",";

    private static final String AREA_HTTP = "HTTP";
    private static final String AREA_BUILD = "BUILD";
    private static final String AREA_DB = "DB";
    private static final String AREA_VOLUME = "VOLUME";

    private static final String MSG_OK = "OK";

    @Inject
    StatusServlet(final NodeService nodeService, final ClientPropertiesService clientPropertiesService, final VolumeService volumeService, final SecurityContext securityContext) {
        this.nodeService = nodeService;
        this.clientPropertiesService = clientPropertiesService;
        this.volumeService = volumeService;
        this.securityContext = securityContext;
    }

    /**
     * Method interceptor needs to go on public API By-pass authentication / authorisation checks.
     *
     * This servlet is NOT protected by default and should be filtered by Apache access controls, see documentation for
     * details.
     */
    @Override
    @Insecure
    public void service(final ServletRequest arg0, final ServletResponse arg1) throws ServletException, IOException {
        super.service(arg0, arg1);
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        try (SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            response.setContentType("text/plain");

            final PrintWriter pw = response.getWriter();

            reportHTTP(pw);
            reportNodeStatus(pw);
            reportVolumeStatus(pw);

            pw.close();
        }
    }

    /**
     * Sub reporting method.
     *
     * @param pw
     */
    public void reportHTTP(final PrintWriter pw) {
        writeInfoLine(pw, AREA_HTTP, MSG_OK);
    }

    /**
     * Sub reporting method.
     *
     * @param pw
     */
    public void reportNodeStatus(final PrintWriter pw) {
        try {
            final ClientProperties clientProperties = clientPropertiesService.getProperties();
            writeInfoLine(pw, AREA_BUILD, "Build version " + clientProperties.get(ClientProperties.BUILD_VERSION));
            writeInfoLine(pw, AREA_BUILD, "Build date " + clientProperties.get(ClientProperties.BUILD_DATE));
            writeInfoLine(pw, AREA_BUILD, "Up date " + clientProperties.get(ClientProperties.UP_DATE));
            writeInfoLine(pw, AREA_BUILD, "Node name " + clientProperties.get(ClientProperties.NODE_NAME));
            // This will query the database...
            nodeService.find(new FindNodeCriteria());
            writeInfoLine(pw, AREA_DB, MSG_OK);
        } catch (final Exception ex) {
            writeErrorLine(pw, AREA_DB, ex.getMessage());
        }
    }

    /**
     * Sub reporting method.
     *
     * @param pw
     */
    public void reportVolumeStatus(final PrintWriter pw) {
        try {
            final FindVolumeCriteria criteria = new FindVolumeCriteria();
            boolean oneOKVolume = false;
            final List<Volume> volumeList = volumeService.find(criteria);
            for (final Volume volume : volumeList) {
                final VolumeState state = volume.getVolumeState();
                if (state.getPercentUsed() == null) {
                    writeErrorLine(pw, AREA_VOLUME,
                            "Unknown Status for volume " + volume.getPath() + " on node " + volume.getNode().getName());
                } else {
                    if (volume.isFull()) {
                        writeWarnLine(pw, AREA_VOLUME,
                                "Volume " + volume.getPath() + " full on node " + volume.getNode().getName());
                    } else {
                        writeInfoLine(pw, AREA_VOLUME, "Volume " + volume.getPath() + " " + state.getPercentUsed()
                                + "% full on node " + volume.getNode().getName());
                        if (VolumeUseStatus.ACTIVE.equals(volume.getStreamStatus())
                                && VolumeType.PUBLIC.equals(volume.getVolumeType())) {
                            oneOKVolume = true;
                        }
                    }
                }
            }
            if (volumeList.size() == 0) {
                writeErrorLine(pw, AREA_VOLUME, "No volumes listed");
            }
            if (!oneOKVolume) {
                writeErrorLine(pw, AREA_VOLUME, "No OK public volumes listed");
            }

        } catch (final Exception ex) {
            writeErrorLine(pw, AREA_VOLUME, ex.getMessage());
        }
    }

    /**
     * Write a info line.
     */
    private void writeInfoLine(final PrintWriter pw, final String area, final String msg) {
        pw.print(INFO);
        pw.print(DELIMITER);
        pw.print(area);
        pw.print(DELIMITER);
        pw.print(msg);
        pw.println();
    }

    /**
     * Write a info line.
     */
    private void writeWarnLine(final PrintWriter pw, final String area, final String msg) {
        pw.print(WARN);
        pw.print(DELIMITER);
        pw.print(area);
        pw.print(DELIMITER);
        pw.print(msg);
        pw.println();
    }

    /**
     * Write a error line.
     */
    private void writeErrorLine(final PrintWriter pw, final String area, final String msg) {
        pw.print(ERROR);
        pw.print(DELIMITER);
        pw.print(area);
        pw.print(DELIMITER);
        pw.print(msg);
        pw.println();
    }
}
