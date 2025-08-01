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

package org.apache.paimon.flink.utils;

import org.apache.flink.api.dag.Transformation;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;

/** Utility methods about Flink operator parallelism to resolve compatibility issues. */
public class ParallelismUtils {
    /**
     * Configures the parallelism of the target stream to be the same as the source stream. In Flink
     * 1.17+, this method will also configure {@link Transformation#isParallelismConfigured()}.
     */
    public static void forwardParallelism(
            SingleOutputStreamOperator<?> targetStream, DataStream<?> sourceStream) {
        setParallelism(
                targetStream,
                sourceStream.getParallelism(),
                sourceStream.getTransformation().isParallelismConfigured());
    }

    public static void forwardParallelism(
            Transformation<?> targetTransformation, DataStream<?> sourceStream) {
        targetTransformation.setParallelism(
                sourceStream.getParallelism(),
                sourceStream.getTransformation().isParallelismConfigured());
    }

    public static void setParallelism(
            SingleOutputStreamOperator<?> targetStream,
            int parallelism,
            boolean parallelismConfigured) {
        // In Flink, SingleOutputStreamOperator#setParallelism wraps Transformation#setParallelism
        // and provide additional checks and validations. In order to enable the checks in
        // SingleOutputStreamOperator as well as the parallelismConfigured ability in
        // Transformation, this method would invoke both setParallelism methods.

        targetStream.setParallelism(parallelism);

        targetStream.getTransformation().setParallelism(parallelism, parallelismConfigured);
    }
}
