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

package stroom.statistics.server.common.rollup;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.security.Insecure;
import stroom.statistics.common.rollup.RollUpBitMask;
import stroom.statistics.shared.CustomRollUpMask;
import stroom.statistics.shared.RollUpBitMaskPermGenerationAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@TaskHandlerBean(task = RollUpBitMaskPermGenerationAction.class)
@Scope(value = StroomScope.TASK)
@Insecure
public class RollUpBitMaskPermGenerationHandler
        extends AbstractTaskHandler<RollUpBitMaskPermGenerationAction, ResultList<CustomRollUpMask>> {
    @Override
    public BaseResultList<CustomRollUpMask> exec(final RollUpBitMaskPermGenerationAction action) {
        final Set<List<Integer>> perms = RollUpBitMask.getRollUpPermutationsAsPositions(action.getFieldCount());

        final List<CustomRollUpMask> masks = new ArrayList<>();

        for (final List<Integer> perm : perms) {
            masks.add(new CustomRollUpMask(perm));
        }

        return BaseResultList.createUnboundedList(masks);
    }
}
