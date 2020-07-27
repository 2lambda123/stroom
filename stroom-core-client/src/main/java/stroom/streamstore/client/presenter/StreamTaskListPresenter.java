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

package stroom.streamstore.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cell.info.client.InfoColumn;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.OrderByColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.FindActionDataProvider;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.ExpressionCriteria;
import stroom.entity.shared.NamedEntity;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.streamtask.shared.ExpressionUtil;
import stroom.streamtask.shared.FetchStreamTaskAction;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.StreamTask;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import java.util.ArrayList;

public class StreamTaskListPresenter extends MyPresenterWidget<DataGridView<StreamTask>>
        implements HasDocumentRead<BaseEntity> {
    private final FindActionDataProvider<ExpressionCriteria, StreamTask> dataProvider;
    private final ExpressionCriteria criteria;

    @Inject
    public StreamTaskListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                                   final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<>(false));

        // Info column.
        getView().addColumn(new InfoColumn<StreamTask>() {
            @Override
            protected void showInfo(final StreamTask row, final int x, final int y) {
                final StringBuilder html = new StringBuilder();
                TooltipUtil.addHeading(html, "Stream Task");
                TooltipUtil.addRowData(html, "Stream Task Id", row.getId());
                TooltipUtil.addRowData(html, "Status", row.getStatus().getDisplayValue());

                if (row.getStreamProcessorFilter() != null) {
                    TooltipUtil.addRowData(html, "Priority", row.getStreamProcessorFilter().getPriority());
                }

                TooltipUtil.addRowData(html, "Status Time", toDateString(row.getStatusMs()));
                TooltipUtil.addRowData(html, "Start Time", toDateString(row.getStartTimeMs()));
                TooltipUtil.addRowData(html, "End Time", toDateString(row.getEndTimeMs()));
                TooltipUtil.addRowData(html, "Node", toNameString(row.getNode()));

                TooltipUtil.addBreak(html);
                TooltipUtil.addHeading(html, "Stream");
                TooltipUtil.addRowData(html, "Stream Id", row.getStream().getId());
                TooltipUtil.addRowData(html, "Status", row.getStream().getStatus().getDisplayValue());
                TooltipUtil.addRowData(html, "Parent Stream Id", row.getStream().getParentStreamId());
                TooltipUtil.addRowData(html, "Created", toDateString(row.getStream().getCreateMs()));
                TooltipUtil.addRowData(html, "Effective", toDateString(row.getStream().getEffectiveMs()));
                TooltipUtil.addRowData(html, "Stream Type", toNameString(row.getStream().getStreamType()));
                TooltipUtil.addRowData(html, "Feed", toNameString(row.getStream().getFeed()));

                if (row.getStreamProcessorFilter() != null) {
                    if (row.getStreamProcessorFilter().getStreamProcessor() != null) {
                        if (row.getStreamProcessorFilter().getStreamProcessor().getPipeline() != null) {
                            TooltipUtil.addBreak(html);
                            TooltipUtil.addHeading(html, "Stream Processor");
                            TooltipUtil.addRowData(html, "Stream Processor Id",
                                    row.getStreamProcessorFilter().getStreamProcessor().getId());
                            TooltipUtil.addRowData(html, "Stream Processor Filter Id",
                                    row.getStreamProcessorFilter().getId());
                            if (row.getStreamProcessorFilter().getStreamProcessor().getPipeline() != null) {
                                TooltipUtil.addRowData(html, "Stream Processor Pipeline", toNameString(
                                        row.getStreamProcessorFilter().getStreamProcessor().getPipeline()));
                            }
                        }
                    }
                }

                tooltipPresenter.setHTML(html.toString());

                final PopupPosition popupPosition = new PopupPosition(x, y);
                ShowPopupEvent.fire(StreamTaskListPresenter.this, tooltipPresenter, PopupType.POPUP, popupPosition,
                        null);
            }
        }, "<br/>", ColumnSizeConstants.ICON_COL);

        getView().addResizableColumn(
                new OrderByColumn<StreamTask, String>(new TextCell(), FindStreamTaskCriteria.FIELD_CREATE_TIME, false) {
                    @Override
                    public String getValue(final StreamTask row) {
                        return ClientDateUtil.toISOString(row.getCreateMs());
                    }
                }, "Create", ColumnSizeConstants.DATE_COL);

        getView().addResizableColumn(
                new OrderByColumn<StreamTask, String>(new TextCell(), FindStreamTaskCriteria.FIELD_STATUS, false) {
                    @Override
                    public String getValue(final StreamTask row) {
                        return row.getStatus().getDisplayValue();
                    }
                }, "Status", 80);

        getView()
                .addColumn(new OrderByColumn<StreamTask, String>(new TextCell(), FindStreamTaskCriteria.FIELD_NODE, true) {
                    @Override
                    public String getValue(final StreamTask row) {
                        if (row.getNode() != null) {
                            return row.getNode().getName();
                        } else {
                            return "";
                        }
                    }
                }, "Node", 100);
        getView().addColumn(new OrderByColumn<StreamTask, String>(new TextCell(), FindStreamTaskCriteria.FIELD_PRIORITY, false) {
            @Override
            public String getValue(final StreamTask row) {
                if (row.getStreamProcessorFilter() != null) {
                    return String.valueOf(row.getStreamProcessorFilter().getPriority());
                }

                return "";
            }
        }, "Priority", 100);
        getView().addResizableColumn(
                new OrderByColumn<StreamTask, String>(new TextCell(), FindStreamTaskCriteria.FIELD_PIPELINE, true) {
                    @Override
                    public String getValue(final StreamTask row) {
                        if (row.getStreamProcessorFilter() != null) {
                            if (row.getStreamProcessorFilter().getStreamProcessor() != null) {
                                if (row.getStreamProcessorFilter().getStreamProcessor().getPipeline() != null) {
                                    return row.getStreamProcessorFilter().getStreamProcessor().getPipeline()
                                            .getDisplayValue();
                                }
                            }
                        }
                        return "";

                    }
                }, "Pipeline", 200);
        getView().addResizableColumn(
                new OrderByColumn<StreamTask, String>(new TextCell(), FindStreamTaskCriteria.FIELD_START_TIME, false) {
                    @Override
                    public String getValue(final StreamTask row) {
                        return ClientDateUtil.toISOString(row.getStartTimeMs());
                    }
                }, "Start Time", ColumnSizeConstants.DATE_COL);
        getView().addResizableColumn(
                new OrderByColumn<StreamTask, String>(new TextCell(), FindStreamTaskCriteria.FIELD_END_TIME_DATE, false) {
                    @Override
                    public String getValue(final StreamTask row) {
                        return ClientDateUtil.toISOString(row.getEndTimeMs());
                    }
                }, "End Time", ColumnSizeConstants.DATE_COL);

        getView().addEndColumn(new EndColumn<>());


        criteria = new ExpressionCriteria();
        dataProvider = new FindActionDataProvider<>(dispatcher, getView(), new FetchStreamTaskAction());
    }

    private String toDateString(final Long ms) {
        if (ms != null) {
            return ClientDateUtil.toISOString(ms) + " (" + ms + ")";
        } else {
            return "";
        }
    }

    private String toNameString(final NamedEntity namedEntity) {
        if (namedEntity != null) {
            return namedEntity.getName() + " (" + namedEntity.getId() + ")";
        } else {
            return "";
        }
    }

    @Override
    public void read(final DocRef docRef, final BaseEntity entity) {
        if (entity instanceof PipelineEntity) {
            setExpression(ExpressionUtil.createPipelineExpression((PipelineEntity) entity));
        } else if (entity instanceof Feed) {
            setExpression(ExpressionUtil.createFeedExpression((Feed) entity));
        } else if (docRef != null) {
            setExpression(ExpressionUtil.createFolderExpression(docRef));
        } else {
            setExpression(null);
        }
    }

    public void setExpression(final ExpressionOperator expression) {
        criteria.setExpression(expression);
        dataProvider.setCriteria(criteria);
    }

    public void clear() {
        getView().setRowData(0, new ArrayList<>(0));
        getView().setRowCount(0, true);
    }
}