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

package stroom.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class ItemsArrayList<E> implements Items<E> {
    private final List<E> list = new ArrayList<>();

    @Override
    public boolean add(final E item) {
        return list.add(item);
    }

    @Override
    public boolean remove(final E item) {
        return list.remove(item);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public void trim(final int size, final Comparator<E> comparator, final RemoveHandler<E> removeHandler) {
        // Sort the list before trimming if we have a comparator.
        if (comparator != null) {
            list.sort(comparator);
        }

        while (list.size() > size) {
            final E lastItem = list.remove(list.size() - 1);

            // Tell the remove handler that we have removed an item.
            if (removeHandler != null) {
                removeHandler.onRemove(lastItem);
            }
        }
    }

    @Override
    public Iterator<E> iterator() {
        return list.iterator();
    }

    @Override
    public String toString() {
        return list.toString();
    }
}
