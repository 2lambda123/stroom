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
import stroom.security.Insecure;
import stroom.statistics.common.rollup.RollUpBitMask;
import stroom.statistics.shared.CustomRollUpMask;
import stroom.statistics.shared.StatisticField;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.statistics.shared.StatisticsDataSourceFieldChangeAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import java.util.HashMap;
import java.util.Map;

@TaskHandlerBean(task = StatisticsDataSourceFieldChangeAction.class)
@Scope(value = StroomScope.TASK)
@Insecure
public class StatisticsDataSourceFieldChangeHandler
        extends AbstractTaskHandler<StatisticsDataSourceFieldChangeAction, StatisticsDataSourceData> {
    @Override
    public StatisticsDataSourceData exec(final StatisticsDataSourceFieldChangeAction action) {
        final StatisticsDataSourceData oldStatisticsDataSourceData = action.getOldStatisticsDataSourceData();
        final StatisticsDataSourceData newStatisticsDataSourceData = action.getNewStatisticsDataSourceData();
        final StatisticsDataSourceData copy = newStatisticsDataSourceData.deepCopy();

        if (!oldStatisticsDataSourceData.getStatisticFields()
                .equals(newStatisticsDataSourceData.getStatisticFields())) {
            final Map<Integer, Integer> newToOldFieldPositionMap = new HashMap<>();

            int pos = 0;
            for (final StatisticField newField : newStatisticsDataSourceData.getStatisticFields()) {
                // old position may be null if this is a new field
                newToOldFieldPositionMap.put(pos++,
                        oldStatisticsDataSourceData.getFieldPositionInList(newField.getFieldName()));
            }

            copy.clearCustomRollUpMask();

            for (final CustomRollUpMask oldCustomRollUpMask : oldStatisticsDataSourceData.getCustomRollUpMasks()) {
                final RollUpBitMask oldRollUpBitMask = RollUpBitMask
                        .fromTagPositions(oldCustomRollUpMask.getRolledUpTagPositions());

                final RollUpBitMask newRollUpBitMask = oldRollUpBitMask.convert(newToOldFieldPositionMap);

                copy.addCustomRollUpMask(new CustomRollUpMask(newRollUpBitMask.getTagPositionsAsList()));
            }
        }

        return copy;
    }
}
