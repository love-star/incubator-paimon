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

package org.apache.paimon.flink.action;

import org.apache.paimon.CoreOptions;
import org.apache.paimon.FileStore;
import org.apache.paimon.Snapshot;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.catalog.CatalogFactory;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.flink.copy.CopyFilesUtil;
import org.apache.paimon.fs.FileIO;
import org.apache.paimon.fs.Path;
import org.apache.paimon.table.FileStoreTable;
import org.apache.paimon.utils.Pair;
import org.apache.paimon.utils.SnapshotManager;
import org.apache.paimon.utils.TraceableFileIO;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.config.TableConfigOptions;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.apache.paimon.testutils.assertj.PaimonAssertions.anyCauseMatches;
import static org.apache.paimon.utils.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IT cases for {@link CopyFilesAction}. */
public class CopyFilesActionITCase extends ActionITCaseBase {

    // ------------------------------------------------------------------------
    //  Constructed Tests
    // ------------------------------------------------------------------------

    @ParameterizedTest(name = "invoker = {0}")
    @ValueSource(strings = {"action", "procedure_indexed", "procedure_named"})
    public void testCopyTable(String invoker) throws Exception {
        String sourceWarehouse = getTempDirPath("source-ware");
        prepareData(sourceWarehouse);

        String targetWarehouse = getTempDirPath("target-ware");
        switch (invoker) {
            case "action":
                String[] args =
                        new String[] {
                            "copy_files",
                            "--warehouse",
                            sourceWarehouse,
                            "--database",
                            "db1",
                            "--table",
                            "t1",
                            "--target_warehouse",
                            targetWarehouse,
                            "--target_database",
                            "mydb",
                            "--target_table",
                            "myt"
                        };
                ActionFactory.createAction(args).get().run();
                break;
            case "procedure_indexed":
                executeSQL(
                        String.format(
                                "CALL sys.copy_files('%s', 'db1', 't1', '', '%s', 'mydb', 'myt')",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            case "procedure_named":
                executeSQL(
                        String.format(
                                "CALL sys.copy_files(warehouse => '%s', database => 'db1', `table` => 't1', target_warehouse => '%s', target_database => 'mydb', target_table => 'myt')",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            default:
                throw new UnsupportedOperationException(invoker);
        }

        // check result
        TableEnvironment tEnv = tableEnvironmentBuilder().batchMode().build();
        tEnv.executeSql(
                "CREATE CATALOG targetcat WITH (\n"
                        + "  'type' = 'paimon',\n"
                        + String.format("  'warehouse' = '%s'\n", targetWarehouse)
                        + ")");
        tEnv.executeSql("USE CATALOG targetcat");

        List<String> actual = collect(tEnv, "SELECT pt, k, v FROM mydb.myt ORDER BY pt, k");
        assertThat(actual)
                .containsExactly(
                        "+I[one, 1, 10]", "+I[one, 2, 21]", "+I[two, 1, 101]", "+I[two, 2, 200]");
        compareCopyFiles(sourceWarehouse, "db1", "t1", targetWarehouse, "mydb", "myt");
    }

    @ParameterizedTest(name = "invoker = {0}")
    @ValueSource(strings = {"action", "procedure_indexed", "procedure_named"})
    public void testCopyTableWithSourceTableExternalPath(String invoker) throws Exception {
        String sourceWarehouse = getTempDirPath("source-ware");
        prepareDataWithExternalPath(sourceWarehouse);

        String targetWarehouse = getTempDirPath("target-ware");
        switch (invoker) {
            case "action":
                String[] args =
                        new String[] {
                            "copy_files",
                            "--warehouse",
                            sourceWarehouse,
                            "--database",
                            "db1",
                            "--table",
                            "t1",
                            "--target_warehouse",
                            targetWarehouse,
                            "--target_database",
                            "mydb",
                            "--target_table",
                            "myt"
                        };
                ActionFactory.createAction(args).get().run();
                break;
            case "procedure_indexed":
                executeSQL(
                        String.format(
                                "CALL sys.copy_files('%s', 'db1', 't1', '', '%s', 'mydb', 'myt')",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            case "procedure_named":
                executeSQL(
                        String.format(
                                "CALL sys.copy_files(warehouse => '%s', database => 'db1', `table` => 't1', target_warehouse => '%s', target_database => 'mydb', target_table => 'myt')",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            default:
                throw new UnsupportedOperationException(invoker);
        }

        // check result
        TableEnvironment tEnv = tableEnvironmentBuilder().batchMode().build();
        tEnv.executeSql(
                "CREATE CATALOG targetcat WITH (\n"
                        + "  'type' = 'paimon',\n"
                        + String.format("  'warehouse' = '%s'\n", targetWarehouse)
                        + ")");
        tEnv.executeSql("USE CATALOG targetcat");

        List<String> actual = collect(tEnv, "SELECT pt, k, v FROM mydb.myt ORDER BY pt, k");
        assertThat(actual)
                .containsExactly(
                        "+I[one, 1, 10]", "+I[one, 2, 21]", "+I[two, 1, 101]", "+I[two, 2, 200]");
        compareCopyFiles(sourceWarehouse, "db1", "t1", targetWarehouse, "mydb", "myt");
    }

    @ParameterizedTest(name = "invoker = {0}")
    @ValueSource(strings = {"action", "procedure_indexed", "procedure_named"})
    public void testCopyDatabase(String invoker) throws Exception {
        String sourceWarehouse = getTempDirPath("source-ware");
        prepareData(sourceWarehouse);

        String targetWarehouse = getTempDirPath("target-ware");
        switch (invoker) {
            case "action":
                String[] args =
                        new String[] {
                            "copy_files",
                            "--warehouse",
                            sourceWarehouse,
                            "--database",
                            "db1",
                            "--target_warehouse",
                            targetWarehouse,
                            "--target_database",
                            "mydb"
                        };
                ActionFactory.createAction(args).get().run();
                break;
            case "procedure_indexed":
                executeSQL(
                        String.format(
                                "CALL sys.copy_files('%s', 'db1', '', '', '%s', 'mydb')",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            case "procedure_named":
                executeSQL(
                        String.format(
                                "CALL sys.copy_files(warehouse => '%s', database => 'db1', target_warehouse => '%s', target_database => 'mydb')",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            default:
                throw new UnsupportedOperationException(invoker);
        }

        // check result
        TableEnvironment tEnv = tableEnvironmentBuilder().batchMode().build();
        tEnv.executeSql(
                "CREATE CATALOG targetcat WITH (\n"
                        + "  'type' = 'paimon',\n"
                        + String.format("  'warehouse' = '%s'\n", targetWarehouse)
                        + ")");
        tEnv.executeSql("USE CATALOG targetcat");

        List<String> actual = collect(tEnv, "SELECT pt, k, v FROM mydb.t1 ORDER BY pt, k");
        assertThat(actual)
                .containsExactly(
                        "+I[one, 1, 10]", "+I[one, 2, 21]", "+I[two, 1, 101]", "+I[two, 2, 200]");
        compareCopyFiles(sourceWarehouse, "db1", "t1", targetWarehouse, "mydb", "t1");

        actual = collect(tEnv, "SELECT k, v FROM mydb.t2 ORDER BY k");
        assertThat(actual)
                .containsExactly("+I[10, 100]", "+I[20, 201]", "+I[100, 1001]", "+I[200, 2000]");
        compareCopyFiles(sourceWarehouse, "db1", "t2", targetWarehouse, "mydb", "t2");
    }

    @ParameterizedTest(name = "invoker = {0}")
    @ValueSource(strings = {"action", "procedure_indexed", "procedure_named"})
    public void testCopyWarehouse(String invoker) throws Exception {
        String sourceWarehouse = getTempDirPath("source-ware");
        prepareData(sourceWarehouse);

        String targetWarehouse = getTempDirPath("target-ware");
        switch (invoker) {
            case "action":
                String[] args =
                        new String[] {
                            "copy_files",
                            "--warehouse",
                            sourceWarehouse,
                            "--target_warehouse",
                            targetWarehouse
                        };
                ActionFactory.createAction(args).get().run();
                break;
            case "procedure_indexed":
                executeSQL(
                        String.format(
                                "CALL sys.copy_files('%s', '', '', '', '%s')",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            case "procedure_named":
                executeSQL(
                        String.format(
                                "CALL sys.copy_files(warehouse => '%s', target_warehouse => '%s')",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            default:
                throw new UnsupportedOperationException(invoker);
        }

        // check result
        TableEnvironment tEnv = tableEnvironmentBuilder().batchMode().build();
        tEnv.executeSql(
                "CREATE CATALOG targetcat WITH (\n"
                        + "  'type' = 'paimon',\n"
                        + String.format("  'warehouse' = '%s'\n", targetWarehouse)
                        + ")");
        tEnv.executeSql("USE CATALOG targetcat");

        List<String> actual = collect(tEnv, "SELECT pt, k, v FROM db1.t1 ORDER BY pt, k");
        assertThat(actual)
                .containsExactly(
                        "+I[one, 1, 10]", "+I[one, 2, 21]", "+I[two, 1, 101]", "+I[two, 2, 200]");
        compareCopyFiles(sourceWarehouse, "db1", "t1", targetWarehouse, "db1", "t1");

        actual = collect(tEnv, "SELECT k, v FROM db1.t2 ORDER BY k");
        assertThat(actual)
                .containsExactly("+I[10, 100]", "+I[20, 201]", "+I[100, 1001]", "+I[200, 2000]");
        compareCopyFiles(sourceWarehouse, "db1", "t2", targetWarehouse, "db1", "t2");

        actual = collect(tEnv, "SELECT pt, k, v FROM db2.t3 ORDER BY pt, k");
        assertThat(actual)
                .containsExactly(
                        "+I[1, 1, one]",
                        "+I[1, 2, twenty]",
                        "+I[2, 1, banana]",
                        "+I[2, 2, orange]");
        compareCopyFiles(sourceWarehouse, "db2", "t3", targetWarehouse, "db2", "t3");

        actual = collect(tEnv, "SELECT k, v FROM db2.t4 ORDER BY k");
        assertThat(actual)
                .containsExactly(
                        "+I[10, one]", "+I[20, twenty]", "+I[100, banana]", "+I[200, orange]");
        compareCopyFiles(sourceWarehouse, "db2", "t4", targetWarehouse, "db2", "t4");
    }

    private void prepareData(String sourceWarehouse) throws Exception {
        TableEnvironment tEnv = tableEnvironmentBuilder().batchMode().build();
        tEnv.executeSql(
                "CREATE CATALOG sourcecat WITH (\n"
                        + "  'type' = 'paimon',\n"
                        + String.format("  'warehouse' = '%s'\n", sourceWarehouse)
                        + ")");
        tEnv.executeSql("USE CATALOG sourcecat");

        tEnv.executeSql("CREATE DATABASE db1");
        tEnv.executeSql("CREATE DATABASE db2");

        // prepare data: db1.t1
        tEnv.executeSql(
                "CREATE TABLE db1.t1 (\n"
                        + "  pt STRING,\n"
                        + "  k INT,\n"
                        + "  v INT,\n"
                        + "  PRIMARY KEY (pt, k) NOT ENFORCED\n"
                        + ") PARTITIONED BY (pt) WITH (\n"
                        + "  'changelog-producer' = 'lookup'\n"
                        + ")");
        tEnv.executeSql(
                        "INSERT INTO db1.t1 VALUES "
                                + "('one', 1, 10), "
                                + "('one', 2, 20), "
                                + "('two', 1, 100)")
                .await();
        tEnv.executeSql(
                        "INSERT INTO db1.t1 VALUES "
                                + "('one', 2, 21), "
                                + "('two', 1, 101), "
                                + "('two', 2, 200)")
                .await();

        // prepare data: db1.t2
        tEnv.executeSql(
                "CREATE TABLE db1.t2 (\n"
                        + "  k INT,\n"
                        + "  v INT,\n"
                        + "  PRIMARY KEY (k) NOT ENFORCED\n"
                        + ") WITH (\n"
                        + "  'changelog-producer' = 'lookup'\n"
                        + ")");
        tEnv.executeSql(
                        "INSERT INTO db1.t2 VALUES "
                                + "(10, 100), "
                                + "(20, 200), "
                                + "(100, 1000)")
                .await();
        tEnv.executeSql(
                        "INSERT INTO db1.t2 VALUES "
                                + "(20, 201), "
                                + "(100, 1001), "
                                + "(200, 2000)")
                .await();

        // prepare data: db2.t3
        tEnv.executeSql(
                "CREATE TABLE db2.t3 (\n"
                        + "  pt INT,\n"
                        + "  k INT,\n"
                        + "  v STRING,\n"
                        + "  PRIMARY KEY (pt, k) NOT ENFORCED\n"
                        + ") PARTITIONED BY (pt) WITH (\n"
                        + "  'changelog-producer' = 'lookup'\n"
                        + ")");
        tEnv.executeSql(
                        "INSERT INTO db2.t3 VALUES "
                                + "(1, 1, 'one'), "
                                + "(1, 2, 'two'), "
                                + "(2, 1, 'apple')")
                .await();
        tEnv.executeSql(
                        "INSERT INTO db2.t3 VALUES "
                                + "(1, 2, 'twenty'), "
                                + "(2, 1, 'banana'), "
                                + "(2, 2, 'orange')")
                .await();

        // prepare data: db2.t4
        tEnv.executeSql(
                "CREATE TABLE db2.t4 (\n"
                        + "  k INT,\n"
                        + "  v STRING,\n"
                        + "  PRIMARY KEY (k) NOT ENFORCED\n"
                        + ") WITH (\n"
                        + "  'changelog-producer' = 'lookup'\n"
                        + ")");
        tEnv.executeSql(
                        "INSERT INTO db2.t4 VALUES "
                                + "(10, 'one'), "
                                + "(20, 'two'), "
                                + "(100, 'apple')")
                .await();
        tEnv.executeSql(
                        "INSERT INTO db2.t4 VALUES "
                                + "(20, 'twenty'), "
                                + "(100, 'banana'), "
                                + "(200, 'orange')")
                .await();
    }

    private void prepareDataWithExternalPath(String sourceWarehouse) throws Exception {
        TableEnvironment tEnv = tableEnvironmentBuilder().batchMode().build();
        tEnv.executeSql(
                "CREATE CATALOG sourcecat WITH (\n"
                        + "  'type' = 'paimon',\n"
                        + String.format("  'warehouse' = '%s'\n", sourceWarehouse)
                        + ")");
        tEnv.executeSql("USE CATALOG sourcecat");

        tEnv.executeSql("CREATE DATABASE db1");
        tEnv.executeSql("CREATE DATABASE db2");

        String db1T1ExternalPath = TraceableFileIO.SCHEME + "://" + getTempDirPath();
        String db1T2ExternalPath = TraceableFileIO.SCHEME + "://" + getTempDirPath();
        String db2T3ExternalPath = TraceableFileIO.SCHEME + "://" + getTempDirPath();
        String db2T4ExternalPath = TraceableFileIO.SCHEME + "://" + getTempDirPath();

        // prepare data: db1.t1
        tEnv.executeSql(
                "CREATE TABLE db1.t1 (\n"
                        + "  pt STRING,\n"
                        + "  k INT,\n"
                        + "  v INT,\n"
                        + "  PRIMARY KEY (pt, k) NOT ENFORCED\n"
                        + ") PARTITIONED BY (pt) WITH (\n"
                        + "  'changelog-producer' = 'lookup',\n"
                        + "  'data-file.external-paths' = '"
                        + db1T1ExternalPath
                        + "',\n"
                        + "  'data-file.external-paths.strategy' = 'round-robin'\n"
                        + ")");
        tEnv.executeSql(
                        "INSERT INTO db1.t1 VALUES "
                                + "('one', 1, 10), "
                                + "('one', 2, 20), "
                                + "('two', 1, 100)")
                .await();
        tEnv.executeSql(
                        "INSERT INTO db1.t1 VALUES "
                                + "('one', 2, 21), "
                                + "('two', 1, 101), "
                                + "('two', 2, 200)")
                .await();

        // prepare data: db1.t2
        tEnv.executeSql(
                "CREATE TABLE db1.t2 (\n"
                        + "  k INT,\n"
                        + "  v INT,\n"
                        + "  PRIMARY KEY (k) NOT ENFORCED\n"
                        + ") WITH (\n"
                        + "  'changelog-producer' = 'lookup',\n"
                        + "  'data-file.external-paths' = '"
                        + db1T2ExternalPath
                        + "',\n"
                        + "  'data-file.external-paths.strategy' = 'round-robin'\n"
                        + ")");
        tEnv.executeSql(
                        "INSERT INTO db1.t2 VALUES "
                                + "(10, 100), "
                                + "(20, 200), "
                                + "(100, 1000)")
                .await();
        tEnv.executeSql(
                        "INSERT INTO db1.t2 VALUES "
                                + "(20, 201), "
                                + "(100, 1001), "
                                + "(200, 2000)")
                .await();

        // prepare data: db2.t3
        tEnv.executeSql(
                "CREATE TABLE db2.t3 (\n"
                        + "  pt INT,\n"
                        + "  k INT,\n"
                        + "  v STRING,\n"
                        + "  PRIMARY KEY (pt, k) NOT ENFORCED\n"
                        + ") PARTITIONED BY (pt) WITH (\n"
                        + "  'changelog-producer' = 'lookup',\n"
                        + "  'data-file.external-paths' = '"
                        + db2T3ExternalPath
                        + "',\n"
                        + "  'data-file.external-paths.strategy' = 'round-robin'\n"
                        + ")");
        tEnv.executeSql(
                        "INSERT INTO db2.t3 VALUES "
                                + "(1, 1, 'one'), "
                                + "(1, 2, 'two'), "
                                + "(2, 1, 'apple')")
                .await();
        tEnv.executeSql(
                        "INSERT INTO db2.t3 VALUES "
                                + "(1, 2, 'twenty'), "
                                + "(2, 1, 'banana'), "
                                + "(2, 2, 'orange')")
                .await();

        // prepare data: db2.t4
        tEnv.executeSql(
                "CREATE TABLE db2.t4 (\n"
                        + "  k INT,\n"
                        + "  v STRING,\n"
                        + "  PRIMARY KEY (k) NOT ENFORCED\n"
                        + ") WITH (\n"
                        + "  'changelog-producer' = 'lookup',\n"
                        + "  'data-file.external-paths' = '"
                        + db2T4ExternalPath
                        + "',\n"
                        + "  'data-file.external-paths.strategy' = 'round-robin'\n"
                        + ")");
        tEnv.executeSql(
                        "INSERT INTO db2.t4 VALUES "
                                + "(10, 'one'), "
                                + "(20, 'two'), "
                                + "(100, 'apple')")
                .await();
        tEnv.executeSql(
                        "INSERT INTO db2.t4 VALUES "
                                + "(20, 'twenty'), "
                                + "(100, 'banana'), "
                                + "(200, 'orange')")
                .await();
    }

    @ParameterizedTest(name = "invoker = {0}")
    @ValueSource(strings = {"action", "procedure_indexed", "procedure_named"})
    public void testCopyWithSchemaEvolution(String invoker) throws Exception {
        String sourceWarehouse = getTempDirPath("source-ware");
        TableEnvironment tEnv = tableEnvironmentBuilder().batchMode().build();
        tEnv.executeSql(
                "CREATE CATALOG sourcecat WITH (\n"
                        + "  'type' = 'paimon',\n"
                        + String.format("  'warehouse' = '%s'\n", sourceWarehouse)
                        + ")");
        tEnv.executeSql("USE CATALOG sourcecat");

        tEnv.executeSql(
                "CREATE TABLE t (\n"
                        + "  pt STRING,\n"
                        + "  k INT,\n"
                        + "  v INT,\n"
                        + "  PRIMARY KEY (pt, k) NOT ENFORCED\n"
                        + ") PARTITIONED BY (pt) WITH (\n"
                        + "  'changelog-producer' = 'lookup'\n"
                        + ")");
        tEnv.executeSql(
                        "INSERT INTO t VALUES "
                                + "('one', 1, 10), "
                                + "('one', 2, 20), "
                                + "('two', 1, 100)")
                .await();
        tEnv.executeSql("ALTER TABLE t ADD v2 STRING AFTER v");
        tEnv.executeSql(
                        "INSERT INTO t VALUES "
                                + "('one', 2, 21, 'apple'), "
                                + "('two', 1, 101, 'banana'), "
                                + "('two', 2, 200, 'orange')")
                .await();

        String targetWarehouse = getTempDirPath("target-ware");
        switch (invoker) {
            case "action":
                String[] args =
                        new String[] {
                            "copy_files",
                            "--warehouse",
                            sourceWarehouse,
                            "--target_warehouse",
                            targetWarehouse
                        };
                ActionFactory.createAction(args).get().run();
                break;
            case "procedure_indexed":
                executeSQL(
                        String.format(
                                "CALL sys.copy_files('%s', '', '', '', '%s')",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            case "procedure_named":
                executeSQL(
                        String.format(
                                "CALL sys.copy_files(warehouse => '%s', target_warehouse => '%s')",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            default:
                throw new UnsupportedOperationException(invoker);
        }

        // check result
        tEnv.executeSql(
                "CREATE CATALOG targetcat WITH (\n"
                        + "  'type' = 'paimon',\n"
                        + String.format("  'warehouse' = '%s'\n", targetWarehouse)
                        + ")");
        tEnv.executeSql("USE CATALOG targetcat");

        List<String> actual = collect(tEnv, "SELECT pt, k, v, v2 FROM t ORDER BY pt, k");
        assertThat(actual)
                .containsExactly(
                        "+I[one, 1, 10, null]",
                        "+I[one, 2, 21, apple]",
                        "+I[two, 1, 101, banana]",
                        "+I[two, 2, 200, orange]");
        compareCopyFiles(sourceWarehouse, "default", "t", targetWarehouse, "default", "t");
    }

    protected void compareCopyFiles(
            String sourceWarehouse,
            String sourceDb,
            String sourceTableName,
            String targetWarehouse,
            String targetDb,
            String targetTableName)
            throws Exception {
        FileStoreTable targetTable = getFileStoreTable(targetWarehouse, targetDb, targetTableName);

        FileStore<?> store = targetTable.store();
        SnapshotManager snapshotManager = store.snapshotManager();
        Snapshot latestSnapshot = snapshotManager.latestSnapshot();
        assertThat(latestSnapshot).isNotNull();
        long snapshotId = latestSnapshot.id();
        FileStoreTable sourceTable = getFileStoreTable(sourceWarehouse, sourceDb, sourceTableName);
        Path tableLocation = sourceTable.location();

        // 1. check the schema files
        List<Path> targetTableSchemaFiles =
                CopyFilesUtil.getSchemaUsedFilesForSnapshot(targetTable, snapshotId);
        List<Pair<Path, Path>> filesPathInfoList =
                targetTableSchemaFiles.stream()
                        .map(
                                absolutePath ->
                                        Pair.of(
                                                absolutePath,
                                                getPathExcludeTableRoot(
                                                        absolutePath, targetTable.location())))
                        .collect(Collectors.toList());
        for (Pair<Path, Path> filesPathInfo : filesPathInfoList) {
            Path sourceTableFile = new Path(tableLocation.toString() + filesPathInfo.getRight());
            assertThat(sourceTable.fileIO().exists(sourceTableFile)).isTrue();
            assertThat(targetTable.fileIO().getFileSize(filesPathInfo.getLeft()))
                    .isEqualTo(sourceTable.fileIO().getFileSize(sourceTableFile));
        }

        // 2. check the manifest files
        List<Path> targetTableManifestFiles =
                CopyFilesUtil.getManifestUsedFilesForSnapshot(targetTable, snapshotId);
        filesPathInfoList =
                targetTableManifestFiles.stream()
                        .map(
                                absolutePath ->
                                        Pair.of(
                                                absolutePath,
                                                getPathExcludeTableRoot(
                                                        absolutePath, targetTable.location())))
                        .collect(Collectors.toList());
        boolean isExternalPath =
                sourceTable.options().containsKey(CoreOptions.DATA_FILE_EXTERNAL_PATHS.key());
        for (Pair<Path, Path> filesPathInfo : filesPathInfoList) {
            Path sourceTableFile = new Path(tableLocation.toString() + filesPathInfo.getRight());
            assertThat(sourceTable.fileIO().exists(sourceTableFile)).isTrue();
            if (!isExternalPath) {
                assertThat(targetTable.fileIO().getFileSize(filesPathInfo.getLeft()))
                        .isEqualTo(sourceTable.fileIO().getFileSize(sourceTableFile));
            } else {
                // todo need to check the content of manifest files
            }
        }

        // 3. check the data files
        filesPathInfoList = CopyFilesUtil.getDataUsedFilesForSnapshot(targetTable, snapshotId);
        isExternalPath =
                sourceTable.options().containsKey(CoreOptions.DATA_FILE_EXTERNAL_PATHS.key());
        String externalPaths = null;
        if (isExternalPath) {
            externalPaths = sourceTable.options().get(CoreOptions.DATA_FILE_EXTERNAL_PATHS.key());
        }

        for (Pair<Path, Path> filesPathInfo : filesPathInfoList) {
            List<Path> paths = new ArrayList<>();
            if (externalPaths == null) {
                paths.add(new Path(tableLocation.toString() + filesPathInfo.getRight()));
            } else {
                for (String externalPath : externalPaths.split(",")) {
                    paths.add(new Path(externalPath + filesPathInfo.getRight()));
                }
            }

            Pair<Path, Boolean> result = pathExist(sourceTable.fileIO(), paths);
            assertThat(result.getRight()).isTrue();
            assertThat(targetTable.fileIO().getFileSize(filesPathInfo.getLeft()))
                    .isEqualTo(sourceTable.fileIO().getFileSize(result.getLeft()));
        }
    }

    protected Pair<Path, Boolean> pathExist(FileIO fileIO, List<Path> paths) throws IOException {
        for (Path path : paths) {
            if (fileIO.exists(path)) {
                return Pair.of(path, true);
            }
        }
        return Pair.of(null, false);
    }

    protected Path getPathExcludeTableRoot(Path absolutePath, Path sourceTableRoot) {
        String fileAbsolutePath = absolutePath.toUri().toString();
        String sourceTableRootPath = sourceTableRoot.toString();

        checkState(
                fileAbsolutePath.startsWith(sourceTableRootPath),
                "This is a bug, please report. fileAbsolutePath is : "
                        + fileAbsolutePath
                        + ", sourceTableRootPath is : "
                        + sourceTableRootPath);

        return new Path(fileAbsolutePath.substring(sourceTableRootPath.length()));
    }

    private FileStoreTable getFileStoreTable(String warehouse, String db, String tableName)
            throws Exception {
        try (Catalog catalog =
                CatalogFactory.createCatalog(CatalogContext.create(new Path(warehouse)))) {
            return (FileStoreTable) catalog.getTable(Identifier.create(db, tableName));
        }
    }

    // ------------------------------------------------------------------------
    //  Random Tests
    // ------------------------------------------------------------------------

    @ParameterizedTest(name = "invoker = {0}")
    @ValueSource(strings = {"action", "procedure_indexed", "procedure_named"})
    @Timeout(180)
    public void testCopyTableWithExpiration(String invoker) throws Exception {
        String sourceWarehouse = getTempDirPath("source-ware");

        TableEnvironment tEnv = tableEnvironmentBuilder().batchMode().parallelism(1).build();
        tEnv.executeSql(
                "CREATE CATALOG sourcecat WITH (\n"
                        + "  'type' = 'paimon',\n"
                        + String.format("  'warehouse' = '%s'\n", sourceWarehouse)
                        + ")");
        tEnv.executeSql("USE CATALOG sourcecat");
        tEnv.executeSql(
                "CREATE TABLE t (\n"
                        + "  pt INT,\n"
                        + "  k INT,\n"
                        + "  v INT,\n"
                        + "  PRIMARY KEY (pt, k) NOT ENFORCED\n"
                        + ") PARTITIONED BY (pt) WITH (\n"
                        + "  'changelog-producer' = 'lookup',\n"
                        // very fast expiration
                        + "  'snapshot.num-retained.min' = '1',\n"
                        + "  'snapshot.num-retained.max' = '1',\n"
                        + "  'write-buffer-size' = '256 kb'\n"
                        + ")");

        int numPartitions = 3;
        int numKeysPerPartition = 10000;
        tEnv.executeSql(
                "CREATE TEMPORARY TABLE s (\n"
                        + "  a INT\n"
                        + ") WITH (\n"
                        + "  'connector' = 'datagen',\n"
                        + "  'fields.a.kind' = 'sequence',\n"
                        + "  'fields.a.start' = '0',\n"
                        + String.format(
                                "  'fields.a.end' = '%d',\n",
                                numPartitions * numKeysPerPartition - 1)
                        + String.format(
                                "  'number-of-rows' = '%d'\n", numPartitions * numKeysPerPartition)
                        + ")");

        // write initial data
        tEnv.executeSql(
                        String.format(
                                "INSERT INTO t SELECT (a / %d) AS pt, (a %% %d) AS k, 0 AS v FROM s",
                                numKeysPerPartition, numKeysPerPartition))
                .await();

        AtomicBoolean running = new AtomicBoolean(true);
        Runnable runnable =
                () -> {
                    int rounds = 1;
                    while (running.get()) {
                        String sql =
                                String.format(
                                        "INSERT INTO t SELECT (a / %d) AS pt, (a %% %d) AS k, %d AS v FROM s",
                                        numKeysPerPartition, numKeysPerPartition, rounds);
                        try {
                            tEnv.executeSql(sql).await();
                            // Sleeping time will become longer and longer, so expiration time will
                            // also become longer.
                            // Thus, at the beginning of the test, copy job is very likely to fail
                            // due to FileNotFoundException.
                            // However, as the test progresses further, copy job should be able to
                            // complete due to longer expiration time.
                            Thread.sleep(100L << Math.min(rounds, 9));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        rounds++;
                    }
                };
        Thread thread = new Thread(runnable);
        thread.start();

        Thread.sleep(ThreadLocalRandom.current().nextInt(2000));
        String targetWarehouse = getTempDirPath("target-ware");

        doCopyJob(invoker, sourceWarehouse, targetWarehouse);

        running.set(false);
        thread.join();

        // check result
        tEnv.executeSql(
                "CREATE CATALOG targetcat WITH (\n"
                        + "  'type' = 'paimon',\n"
                        + String.format("  'warehouse' = '%s'\n", targetWarehouse)
                        + ")");
        tEnv.executeSql("USE CATALOG targetcat");

        List<String> result;
        while (true) {
            try {
                result = collect(tEnv, "SELECT pt, COUNT(*) FROM t GROUP BY pt ORDER BY pt");
            } catch (Exception e) {
                // ignore the exception, as it is expected to fail due to FileNotFoundException
                // we will retry the copy job, and check the result again until success.
                doCopyJob(invoker, sourceWarehouse, targetWarehouse);
                continue;
            }
            break;
        }

        assertThat(result)
                .isEqualTo(
                        IntStream.range(0, numPartitions)
                                .mapToObj(i -> String.format("+I[%d, %d]", i, numKeysPerPartition))
                                .collect(Collectors.toList()));
        assertThat(collect(tEnv, "SELECT COUNT(DISTINCT v) FROM t"))
                .isEqualTo(Collections.singletonList("+I[1]"));
    }

    private void doCopyJob(String invoker, String sourceWarehouse, String targetWarehouse)
            throws Exception {
        switch (invoker) {
            case "action":
                String[] args =
                        new String[] {
                            "copy_files",
                            "--warehouse",
                            // special file io to make cloning slower, thus more likely to face
                            // FileNotFoundException, see CopyFilesActionSlowFileIO
                            "copy-slow://" + sourceWarehouse,
                            "--target_warehouse",
                            "copy-slow://" + targetWarehouse,
                            "--parallelism",
                            "1"
                        };
                CopyFilesAction action = (CopyFilesAction) ActionFactory.createAction(args).get();

                StreamExecutionEnvironment env =
                        streamExecutionEnvironmentBuilder().streamingMode().allowRestart().build();
                action.withStreamExecutionEnvironment(env).build();
                env.execute();
                break;
            case "procedure_indexed":
                callProcedureWithRestartAllowed(
                        String.format(
                                "CALL sys.copy_files('copy-slow://%s', '', '', '', 'copy-slow://%s', '', '', '', 1)",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            case "procedure_named":
                callProcedureWithRestartAllowed(
                        String.format(
                                "CALL sys.copy_files(warehouse => 'copy-slow://%s', target_warehouse => 'copy-slow://%s', parallelism => 1)",
                                sourceWarehouse, targetWarehouse),
                        true,
                        true);
                break;
            default:
                throw new UnsupportedOperationException(invoker);
        }
    }

    // ------------------------------------------------------------------------
    //  Negative Tests
    // ------------------------------------------------------------------------

    @Test
    public void testEmptySourceCatalog() {
        String sourceWarehouse = getTempDirPath("source-ware");

        TableEnvironment tEnv = tableEnvironmentBuilder().batchMode().parallelism(1).build();
        tEnv.executeSql(
                "CREATE CATALOG sourcecat WITH (\n"
                        + "  'type' = 'paimon',\n"
                        + String.format("  'warehouse' = '%s'\n", sourceWarehouse)
                        + ")");

        String targetWarehouse = getTempDirPath("target-ware");

        String[] args =
                new String[] {
                    "copy_files",
                    "--warehouse",
                    sourceWarehouse,
                    "--target_warehouse",
                    targetWarehouse,
                    "--parallelism",
                    "1"
                };
        CopyFilesAction action = (CopyFilesAction) ActionFactory.createAction(args).get();

        StreamExecutionEnvironment env =
                streamExecutionEnvironmentBuilder().streamingMode().allowRestart().build();
        action.withStreamExecutionEnvironment(env);

        assertThatThrownBy(action::run)
                .satisfies(
                        anyCauseMatches(
                                IllegalStateException.class,
                                "Didn't find any table in source catalog."));
    }

    // ------------------------------------------------------------------------
    //  Utils
    // ------------------------------------------------------------------------

    private CloseableIterator<Row> callProcedureWithRestartAllowed(
            String procedureStatement, boolean isStreaming, boolean dmlSync) {
        TableEnvironment tEnv;
        if (isStreaming) {
            tEnv =
                    tableEnvironmentBuilder()
                            .streamingMode()
                            .allowRestart()
                            .checkpointIntervalMs(500)
                            .build();
        } else {
            tEnv = tableEnvironmentBuilder().batchMode().allowRestart().build();
        }

        tEnv.getConfig().set(TableConfigOptions.TABLE_DML_SYNC, dmlSync);

        tEnv.executeSql(
                String.format(
                        "CREATE CATALOG PAIMON WITH ('type'='paimon', 'warehouse'='%s');",
                        warehouse));
        tEnv.useCatalog("PAIMON");

        return tEnv.executeSql(procedureStatement).collect();
    }

    protected List<String> collect(TableEnvironment tEnv, String sql) throws Exception {
        List<String> actual = new ArrayList<>();
        try (CloseableIterator<Row> it = tEnv.executeSql(sql).collect()) {
            while (it.hasNext()) {
                Row row = it.next();
                actual.add(row.toString());
            }
        }
        return actual;
    }
}
