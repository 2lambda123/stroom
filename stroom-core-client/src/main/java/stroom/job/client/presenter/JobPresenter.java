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

package stroom.job.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.job.shared.Job;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class JobPresenter extends ContentTabPresenter<JobPresenter.JobView> {

    public static final String JOB_LIST = "JOB_LIST";
    public static final String JOB_NODE_LIST = "JOB_NODE_LIST";
    private final JobListPresenter jobListPresenter;
    private final JobNodeListPresenter jobNodeListPresenter;

    @Inject
    public JobPresenter(final EventBus eventBus,
                        final JobView view,
                        final JobListPresenter jobListPresenter,
                        final JobNodeListPresenter jobNodeListPresenter) {
        super(eventBus, view);
        this.jobListPresenter = jobListPresenter;
        this.jobNodeListPresenter = jobNodeListPresenter;

        setInSlot(JOB_LIST, jobListPresenter);
        setInSlot(JOB_NODE_LIST, jobNodeListPresenter);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(jobListPresenter.getSelectionModel().addSelectionHandler(event -> {
            final Job row = jobListPresenter.getSelectionModel().getSelected();
            jobNodeListPresenter.read(row);
        }));
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.JOBS;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Jobs";
    }

    public interface JobView extends View {

    }
}
