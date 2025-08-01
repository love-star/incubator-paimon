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

package org.apache.paimon;

import org.apache.paimon.annotation.Public;
import org.apache.paimon.utils.JsonSerdeUtil;

import org.apache.paimon.shade.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.paimon.shade.jackson2.com.fasterxml.jackson.annotation.JsonGetter;
import org.apache.paimon.shade.jackson2.com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.paimon.shade.jackson2.com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.paimon.shade.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * This file is the entrance to all data committed at some specific time point.
 *
 * <p>Versioned change list:
 *
 * <ul>
 *   <li>Version 1: Initial version for paimon <= 0.2. There is no "version" field in json file.
 *   <li>Version 2: Introduced in paimon 0.3. Add "version" field and "changelogManifestList" field.
 *   <li>Version 3: Introduced in paimon 0.4. Add "baseRecordCount" field, "deltaRecordCount" field
 *       and "changelogRecordCount" field.
 * </ul>
 *
 * <p>Unversioned change list:
 *
 * <ul>
 *   <li>Since paimon 0.2 and paimon 0.3, commitIdentifier is changed from a String to a long value.
 *       For paimon < 0.2, only Flink connectors have paimon sink and they use checkpointId as
 *       commitIdentifier (which is a long value). Json can automatically perform type conversion so
 *       there is no compatibility issue.
 * </ul>
 *
 * @since 0.9.0
 */
@Public
@JsonIgnoreProperties(ignoreUnknown = true)
public class Snapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final long FIRST_SNAPSHOT_ID = 1;

    public static final int TABLE_STORE_02_VERSION = 1;
    protected static final int CURRENT_VERSION = 3;

    protected static final String FIELD_VERSION = "version";
    protected static final String FIELD_ID = "id";
    protected static final String FIELD_SCHEMA_ID = "schemaId";
    protected static final String FIELD_BASE_MANIFEST_LIST = "baseManifestList";
    protected static final String FIELD_BASE_MANIFEST_LIST_SIZE = "baseManifestListSize";
    protected static final String FIELD_DELTA_MANIFEST_LIST = "deltaManifestList";
    protected static final String FIELD_DELTA_MANIFEST_LIST_SIZE = "deltaManifestListSize";
    protected static final String FIELD_CHANGELOG_MANIFEST_LIST = "changelogManifestList";
    protected static final String FIELD_CHANGELOG_MANIFEST_LIST_SIZE = "changelogManifestListSize";
    protected static final String FIELD_INDEX_MANIFEST = "indexManifest";
    protected static final String FIELD_COMMIT_USER = "commitUser";
    protected static final String FIELD_COMMIT_IDENTIFIER = "commitIdentifier";
    protected static final String FIELD_COMMIT_KIND = "commitKind";
    protected static final String FIELD_TIME_MILLIS = "timeMillis";
    protected static final String FIELD_LOG_OFFSETS = "logOffsets";
    protected static final String FIELD_TOTAL_RECORD_COUNT = "totalRecordCount";
    protected static final String FIELD_DELTA_RECORD_COUNT = "deltaRecordCount";
    protected static final String FIELD_CHANGELOG_RECORD_COUNT = "changelogRecordCount";
    protected static final String FIELD_WATERMARK = "watermark";
    protected static final String FIELD_STATISTICS = "statistics";
    protected static final String FIELD_PROPERTIES = "properties";
    protected static final String FIELD_NEXT_ROW_ID = "nextRowId";

    // version of snapshot
    // null for paimon <= 0.2
    @JsonProperty(FIELD_VERSION)
    @Nullable
    protected final Integer version;

    @JsonProperty(FIELD_ID)
    protected final long id;

    @JsonProperty(FIELD_SCHEMA_ID)
    protected final long schemaId;

    // a manifest list recording all changes from the previous snapshots
    @JsonProperty(FIELD_BASE_MANIFEST_LIST)
    protected final String baseManifestList;

    @JsonProperty(FIELD_BASE_MANIFEST_LIST_SIZE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    protected final Long baseManifestListSize;

    // a manifest list recording all new changes occurred in this snapshot
    // for faster expire and streaming reads
    @JsonProperty(FIELD_DELTA_MANIFEST_LIST)
    protected final String deltaManifestList;

    @JsonProperty(FIELD_DELTA_MANIFEST_LIST_SIZE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    protected final Long deltaManifestListSize;

    // a manifest list recording all changelog produced in this snapshot
    // null if no changelog is produced, or for paimon <= 0.2
    @JsonProperty(FIELD_CHANGELOG_MANIFEST_LIST)
    @Nullable
    protected final String changelogManifestList;

    @JsonProperty(FIELD_CHANGELOG_MANIFEST_LIST_SIZE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    protected final Long changelogManifestListSize;

    // a manifest recording all index files of this table
    // null if no index file
    @JsonProperty(FIELD_INDEX_MANIFEST)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    protected final String indexManifest;

    @JsonProperty(FIELD_COMMIT_USER)
    protected final String commitUser;

    // Mainly for snapshot deduplication.
    //
    // If multiple snapshots have the same commitIdentifier, reading from any of these snapshots
    // must produce the same table.
    //
    // If snapshot A has a smaller commitIdentifier than snapshot B, then snapshot A must be
    // committed before snapshot B, and thus snapshot A must contain older records than snapshot B.
    @JsonProperty(FIELD_COMMIT_IDENTIFIER)
    protected final long commitIdentifier;

    @JsonProperty(FIELD_COMMIT_KIND)
    protected final CommitKind commitKind;

    @JsonProperty(FIELD_TIME_MILLIS)
    protected final long timeMillis;

    @JsonProperty(FIELD_LOG_OFFSETS)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    protected final Map<Integer, Long> logOffsets;

    // record count of all changes occurred in this snapshot
    // null for paimon <= 0.3
    @JsonProperty(FIELD_TOTAL_RECORD_COUNT)
    @Nullable
    protected final Long totalRecordCount;

    // record count of all new changes occurred in this snapshot
    // null for paimon <= 0.3
    @JsonProperty(FIELD_DELTA_RECORD_COUNT)
    @Nullable
    protected final Long deltaRecordCount;

    // record count of all changelog produced in this snapshot
    // null for paimon <= 0.3
    @JsonProperty(FIELD_CHANGELOG_RECORD_COUNT)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    protected final Long changelogRecordCount;

    // watermark for input records
    // null for paimon <= 0.3
    // null if there is no watermark in new committing, and the previous snapshot does not have a
    // watermark
    @JsonProperty(FIELD_WATERMARK)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Nullable
    protected final Long watermark;

    // stats file name for statistics of this table
    // null if no stats file
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(FIELD_STATISTICS)
    @Nullable
    protected final String statistics;

    // properties
    // null for paimon <= 1.1 or empty properties
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(FIELD_PROPERTIES)
    @Nullable
    protected final Map<String, String> properties;

    @Nullable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(FIELD_NEXT_ROW_ID)
    protected final Long nextRowId;

    public Snapshot(
            long id,
            long schemaId,
            String baseManifestList,
            @Nullable Long baseManifestListSize,
            String deltaManifestList,
            @Nullable Long deltaManifestListSize,
            @Nullable String changelogManifestList,
            @Nullable Long changelogManifestListSize,
            @Nullable String indexManifest,
            String commitUser,
            long commitIdentifier,
            CommitKind commitKind,
            long timeMillis,
            Map<Integer, Long> logOffsets,
            @Nullable Long totalRecordCount,
            @Nullable Long deltaRecordCount,
            @Nullable Long changelogRecordCount,
            @Nullable Long watermark,
            @Nullable String statistics,
            @Nullable Map<String, String> properties,
            @Nullable Long nextRowId) {
        this(
                CURRENT_VERSION,
                id,
                schemaId,
                baseManifestList,
                baseManifestListSize,
                deltaManifestList,
                deltaManifestListSize,
                changelogManifestList,
                changelogManifestListSize,
                indexManifest,
                commitUser,
                commitIdentifier,
                commitKind,
                timeMillis,
                logOffsets,
                totalRecordCount,
                deltaRecordCount,
                changelogRecordCount,
                watermark,
                statistics,
                properties,
                nextRowId);
    }

    @JsonCreator
    public Snapshot(
            @JsonProperty(FIELD_VERSION) @Nullable Integer version,
            @JsonProperty(FIELD_ID) long id,
            @JsonProperty(FIELD_SCHEMA_ID) long schemaId,
            @JsonProperty(FIELD_BASE_MANIFEST_LIST) String baseManifestList,
            @JsonProperty(FIELD_BASE_MANIFEST_LIST_SIZE) @Nullable Long baseManifestListSize,
            @JsonProperty(FIELD_DELTA_MANIFEST_LIST) String deltaManifestList,
            @JsonProperty(FIELD_DELTA_MANIFEST_LIST_SIZE) @Nullable Long deltaManifestListSize,
            @JsonProperty(FIELD_CHANGELOG_MANIFEST_LIST) @Nullable String changelogManifestList,
            @JsonProperty(FIELD_CHANGELOG_MANIFEST_LIST_SIZE) @Nullable
                    Long changelogManifestListSize,
            @JsonProperty(FIELD_INDEX_MANIFEST) @Nullable String indexManifest,
            @JsonProperty(FIELD_COMMIT_USER) String commitUser,
            @JsonProperty(FIELD_COMMIT_IDENTIFIER) long commitIdentifier,
            @JsonProperty(FIELD_COMMIT_KIND) CommitKind commitKind,
            @JsonProperty(FIELD_TIME_MILLIS) long timeMillis,
            @JsonProperty(FIELD_LOG_OFFSETS) @Nullable Map<Integer, Long> logOffsets,
            @JsonProperty(FIELD_TOTAL_RECORD_COUNT) @Nullable Long totalRecordCount,
            @JsonProperty(FIELD_DELTA_RECORD_COUNT) @Nullable Long deltaRecordCount,
            @JsonProperty(FIELD_CHANGELOG_RECORD_COUNT) @Nullable Long changelogRecordCount,
            @JsonProperty(FIELD_WATERMARK) @Nullable Long watermark,
            @JsonProperty(FIELD_STATISTICS) @Nullable String statistics,
            @JsonProperty(FIELD_PROPERTIES) @Nullable Map<String, String> properties,
            @JsonProperty(FIELD_NEXT_ROW_ID) @Nullable Long nextRowId) {
        this.version = version;
        this.id = id;
        this.schemaId = schemaId;
        this.baseManifestList = baseManifestList;
        this.baseManifestListSize = baseManifestListSize;
        this.deltaManifestList = deltaManifestList;
        this.deltaManifestListSize = deltaManifestListSize;
        this.changelogManifestList = changelogManifestList;
        this.changelogManifestListSize = changelogManifestListSize;
        this.indexManifest = indexManifest;
        this.commitUser = commitUser;
        this.commitIdentifier = commitIdentifier;
        this.commitKind = commitKind;
        this.timeMillis = timeMillis;
        this.logOffsets = logOffsets;
        this.totalRecordCount = totalRecordCount;
        this.deltaRecordCount = deltaRecordCount;
        this.changelogRecordCount = changelogRecordCount;
        this.watermark = watermark;
        this.statistics = statistics;
        this.properties = properties;
        this.nextRowId = nextRowId;
    }

    @JsonGetter(FIELD_VERSION)
    public int version() {
        // there is no version field for paimon <= 0.2
        return version == null ? TABLE_STORE_02_VERSION : version;
    }

    @JsonGetter(FIELD_ID)
    public long id() {
        return id;
    }

    @JsonGetter(FIELD_SCHEMA_ID)
    public long schemaId() {
        return schemaId;
    }

    @JsonGetter(FIELD_BASE_MANIFEST_LIST)
    public String baseManifestList() {
        return baseManifestList;
    }

    @JsonGetter(FIELD_BASE_MANIFEST_LIST_SIZE)
    @Nullable
    public Long baseManifestListSize() {
        return baseManifestListSize;
    }

    @JsonGetter(FIELD_DELTA_MANIFEST_LIST)
    public String deltaManifestList() {
        return deltaManifestList;
    }

    @JsonGetter(FIELD_DELTA_MANIFEST_LIST_SIZE)
    @Nullable
    public Long deltaManifestListSize() {
        return deltaManifestListSize;
    }

    @JsonGetter(FIELD_CHANGELOG_MANIFEST_LIST)
    @Nullable
    public String changelogManifestList() {
        return changelogManifestList;
    }

    @JsonGetter(FIELD_CHANGELOG_MANIFEST_LIST_SIZE)
    @Nullable
    public Long changelogManifestListSize() {
        return changelogManifestListSize;
    }

    @JsonGetter(FIELD_INDEX_MANIFEST)
    @Nullable
    public String indexManifest() {
        return indexManifest;
    }

    @JsonGetter(FIELD_COMMIT_USER)
    public String commitUser() {
        return commitUser;
    }

    @JsonGetter(FIELD_COMMIT_IDENTIFIER)
    public long commitIdentifier() {
        return commitIdentifier;
    }

    @JsonGetter(FIELD_COMMIT_KIND)
    public CommitKind commitKind() {
        return commitKind;
    }

    @JsonGetter(FIELD_TIME_MILLIS)
    public long timeMillis() {
        return timeMillis;
    }

    @JsonGetter(FIELD_LOG_OFFSETS)
    @Nullable
    public Map<Integer, Long> logOffsets() {
        return logOffsets;
    }

    @JsonGetter(FIELD_TOTAL_RECORD_COUNT)
    @Nullable
    public Long totalRecordCount() {
        return totalRecordCount;
    }

    @JsonGetter(FIELD_DELTA_RECORD_COUNT)
    @Nullable
    public Long deltaRecordCount() {
        return deltaRecordCount;
    }

    @JsonGetter(FIELD_CHANGELOG_RECORD_COUNT)
    @Nullable
    public Long changelogRecordCount() {
        return changelogRecordCount;
    }

    @JsonGetter(FIELD_WATERMARK)
    @Nullable
    public Long watermark() {
        return watermark;
    }

    @JsonGetter(FIELD_STATISTICS)
    @Nullable
    public String statistics() {
        return statistics;
    }

    @JsonGetter(FIELD_PROPERTIES)
    @Nullable
    public Map<String, String> properties() {
        return properties;
    }

    @JsonGetter(FIELD_NEXT_ROW_ID)
    @Nullable
    public Long nextRowId() {
        return nextRowId;
    }

    public String toJson() {
        return JsonSerdeUtil.toJson(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                version,
                id,
                schemaId,
                baseManifestList,
                baseManifestListSize,
                deltaManifestList,
                deltaManifestListSize,
                changelogManifestList,
                changelogManifestListSize,
                indexManifest,
                commitUser,
                commitIdentifier,
                commitKind,
                timeMillis,
                logOffsets,
                totalRecordCount,
                deltaRecordCount,
                changelogRecordCount,
                watermark,
                statistics,
                properties,
                nextRowId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Snapshot that = (Snapshot) o;
        return Objects.equals(version, that.version)
                && id == that.id
                && schemaId == that.schemaId
                && Objects.equals(baseManifestList, that.baseManifestList)
                && Objects.equals(baseManifestListSize, that.baseManifestListSize)
                && Objects.equals(deltaManifestList, that.deltaManifestList)
                && Objects.equals(deltaManifestListSize, that.deltaManifestListSize)
                && Objects.equals(changelogManifestList, that.changelogManifestList)
                && Objects.equals(changelogManifestListSize, that.changelogManifestListSize)
                && Objects.equals(indexManifest, that.indexManifest)
                && Objects.equals(commitUser, that.commitUser)
                && commitIdentifier == that.commitIdentifier
                && commitKind == that.commitKind
                && timeMillis == that.timeMillis
                && Objects.equals(logOffsets, that.logOffsets)
                && Objects.equals(totalRecordCount, that.totalRecordCount)
                && Objects.equals(deltaRecordCount, that.deltaRecordCount)
                && Objects.equals(changelogRecordCount, that.changelogRecordCount)
                && Objects.equals(watermark, that.watermark)
                && Objects.equals(statistics, that.statistics)
                && Objects.equals(properties, that.properties)
                && Objects.equals(nextRowId, that.nextRowId);
    }

    /** Type of changes in this snapshot. */
    public enum CommitKind {

        /** Changes flushed from the mem table. */
        APPEND,

        /** Changes by compacting existing data files. */
        COMPACT,

        /** Changes that clear up the whole partition and then add new records. */
        OVERWRITE,

        /** Collect statistics. */
        ANALYZE
    }

    // =================== Utils for reading =========================

    public static Snapshot fromJson(String json) {
        return JsonSerdeUtil.fromJson(json, Snapshot.class);
    }
}
