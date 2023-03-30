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

package stroom.annotation.impl;

import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.EventLink;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.annotation.shared.SetStatusRequest;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.datasource.api.v2.AbstractField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.UserName;

import java.util.List;

public interface AnnotationDao {

    Annotation get(long annotationId);

    AnnotationDetail getDetail(long annotationId);

    List<Annotation> getAnnotationsForEvents(long streamId, long eventId);

    List<AnnotationDetail> getAnnotationDetailsForEvents(long streamId, long eventId);

    AnnotationDetail createEntry(CreateEntryRequest request, UserName user);

    List<EventId> getLinkedEvents(Long annotationId);

    List<EventId> link(EventLink eventLink, UserName user);

    List<EventId> unlink(EventLink eventLink, UserName user);

    Integer setStatus(SetStatusRequest request, UserName user);

    Integer setAssignedTo(SetAssignedToRequest request, UserName user);

    void search(ExpressionCriteria criteria, AbstractField[] fields, ValuesConsumer consumer);
}
