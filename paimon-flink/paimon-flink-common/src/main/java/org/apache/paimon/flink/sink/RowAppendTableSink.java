/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.flink.sink;

import org.apache.paimon.data.InternalRow;
import org.apache.paimon.manifest.ManifestCommittable;
import org.apache.paimon.table.FileStoreTable;

import org.apache.flink.streaming.api.operators.OneInputStreamOperatorFactory;

import java.util.Map;

/** An {@link AppendTableSink} which handles {@link InternalRow}. */
public class RowAppendTableSink extends AppendTableSink<InternalRow> {

    private static final long serialVersionUID = 1L;

    public RowAppendTableSink(
            FileStoreTable table,
            Map<String, String> overwritePartitions,
            LogSinkFunction logSinkFunction,
            Integer parallelism) {
        super(table, overwritePartitions, logSinkFunction, parallelism);
    }

    @Override
    protected OneInputStreamOperatorFactory<InternalRow, Committable> createWriteOperatorFactory(
            StoreSinkWrite.Provider writeProvider, String commitUser) {
        return createNoStateRowWriteOperatorFactory(
                table, logSinkFunction, writeProvider, commitUser);
    }

    @Override
    protected CommittableStateManager<ManifestCommittable> createCommittableStateManager() {
        return createRestoreOnlyCommittableStateManager(table);
    }
}
