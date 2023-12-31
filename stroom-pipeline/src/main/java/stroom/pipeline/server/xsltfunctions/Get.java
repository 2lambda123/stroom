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
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@Component
@Scope(StroomScope.PROTOTYPE)
class Get extends StroomExtensionFunctionCall {
    private final TaskScopeMap map;

    @Inject
    Get(final TaskScopeMap map) {
        this.map = map;
    }

    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) {
        String result = null;

        try {
            final String key = getSafeString(functionName, context, arguments, 0);
            result = map.get(key);
        } catch (final Exception e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }
}
