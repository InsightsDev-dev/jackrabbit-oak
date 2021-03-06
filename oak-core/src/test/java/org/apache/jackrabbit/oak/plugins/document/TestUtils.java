/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.document;

import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.EmptyHook;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.state.NodeStore;

public class TestUtils {

    public static final Predicate<UpdateOp> IS_LAST_REV_UPDATE = new Predicate<UpdateOp>() {
        @Override
        public boolean apply(@Nullable UpdateOp input) {
            return input != null && isLastRevUpdate(input);
        }
    };

    /**
     * Returns {@code true} if the given {@code update} performs a
     * {@code _lastRev} update.
     *
     * @param update the update to check.
     * @return {@code true} if the operation performs an update on
     *          {@code _lastRev}, {@code false} otherwise.
     */
    public static boolean isLastRevUpdate(UpdateOp update) {
        for (Map.Entry<UpdateOp.Key, UpdateOp.Operation> change : update.getChanges().entrySet()) {
            if (!NodeDocument.isLastRevEntry(change.getKey().getName())
                    && !NodeDocument.MODIFIED_IN_SECS.equals(change.getKey().getName())) {
                return false;
            }
        }
        return true;
    }

    public static NodeState merge(NodeStore store, NodeBuilder builder)
            throws CommitFailedException {
        return store.merge(builder, EmptyHook.INSTANCE, CommitInfo.EMPTY);
    }
}
