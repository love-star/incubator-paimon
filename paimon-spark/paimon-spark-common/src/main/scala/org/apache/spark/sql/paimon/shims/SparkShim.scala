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

package org.apache.spark.sql.paimon.shims

import org.apache.paimon.data.variant.Variant
import org.apache.paimon.spark.data.{SparkArrayData, SparkInternalRow}
import org.apache.paimon.types.{DataType, RowType}

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.{Attribute, Expression}
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression
import org.apache.spark.sql.catalyst.parser.ParserInterface
import org.apache.spark.sql.catalyst.plans.logical.{CTERelationRef, LogicalPlan, MergeAction, MergeIntoTable}
import org.apache.spark.sql.catalyst.rules.Rule
import org.apache.spark.sql.catalyst.util.ArrayData
import org.apache.spark.sql.connector.catalog.{Identifier, Table, TableCatalog}
import org.apache.spark.sql.connector.expressions.Transform
import org.apache.spark.sql.types.StructType

import java.util.{Map => JMap}

/**
 * A spark shim trait. It declares methods which have incompatible implementations between Spark 3
 * and Spark 4. The specific SparkShim implementation will be loaded through Service Provider
 * Interface.
 */
trait SparkShim {

  def classicApi: ClassicApi

  def createSparkParser(delegate: ParserInterface): ParserInterface

  def createCustomResolution(spark: SparkSession): Rule[LogicalPlan]

  def createSparkInternalRow(rowType: RowType): SparkInternalRow

  def createSparkArrayData(elementType: DataType): SparkArrayData

  def createTable(
      tableCatalog: TableCatalog,
      ident: Identifier,
      schema: StructType,
      partitions: Array[Transform],
      properties: JMap[String, String]): Table

  def createCTERelationRef(
      cteId: Long,
      resolved: Boolean,
      output: Seq[Attribute],
      isStreaming: Boolean): CTERelationRef

  def supportsHashAggregate(
      aggregateBufferAttributes: Seq[Attribute],
      groupingExpression: Seq[Expression]): Boolean

  def supportsObjectHashAggregate(
      aggregateExpressions: Seq[AggregateExpression],
      groupByExpressions: Seq[Expression]): Boolean

  def createMergeIntoTable(
      targetTable: LogicalPlan,
      sourceTable: LogicalPlan,
      mergeCondition: Expression,
      matchedActions: Seq[MergeAction],
      notMatchedActions: Seq[MergeAction],
      notMatchedBySourceActions: Seq[MergeAction],
      withSchemaEvolution: Boolean): MergeIntoTable

  // for variant
  def toPaimonVariant(o: Object): Variant

  def toPaimonVariant(row: InternalRow, pos: Int): Variant

  def toPaimonVariant(array: ArrayData, pos: Int): Variant

  def isSparkVariantType(dataType: org.apache.spark.sql.types.DataType): Boolean

  def SparkVariantType(): org.apache.spark.sql.types.DataType
}
