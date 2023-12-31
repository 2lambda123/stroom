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

package stroom.pipeline.server.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.StringValue;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.pipeline.state.FeedHolder;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@Component
@Scope(StroomScope.PROTOTYPE)
class Classification extends StroomExtensionFunctionCall {
    private final FeedHolder feedHolder;
    private final FeedService feedService;

    private Feed feed;
    private String classification;

    @Inject
    Classification(final FeedHolder feedHolder, final FeedService feedService) {
        this.feedHolder = feedHolder;
        this.feedService = feedService;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            if (feed == null || feed != feedHolder.getFeed()) {
                feed = feedHolder.getFeed();
                classification = feedService.getDisplayClassification(feed);
            }

            result = classification;
        } catch (final Exception e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }
}
