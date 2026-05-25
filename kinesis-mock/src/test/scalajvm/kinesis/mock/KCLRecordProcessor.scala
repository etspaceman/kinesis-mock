/*
 * Copyright 2021-2026 io.github.etspaceman
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

package kinesis.mock

import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.IORuntime
import cats.syntax.all.*
import software.amazon.kinesis.common.StreamIdentifier
import software.amazon.kinesis.lifecycle.events.*
import software.amazon.kinesis.processor.*
import software.amazon.kinesis.retrieval.KinesisClientRecord

case class KCLRecordProcessor(
    resultsQueue: Queue[IO, KinesisClientRecord]
)(using R: IORuntime)
    extends ShardRecordProcessor:
  override def initialize(x: InitializationInput): Unit = ()
  override def processRecords(x: ProcessRecordsInput): Unit = x
    .records()
    .asScala
    .toVector
    .traverse_(record =>
      resultsQueue.offer(record) >> IO.blocking(
        x.checkpointer()
          .checkpoint(record.sequenceNumber(), record.subSequenceNumber())
      )
    )
    .unsafeRunSync()
  override def leaseLost(x: LeaseLostInput): Unit = ()
  override def shardEnded(x: ShardEndedInput): Unit =
    x.checkpointer().checkpoint()

  override def shutdownRequested(x: ShutdownRequestedInput): Unit =
    x.checkpointer().checkpoint()

case class KCLRecordProcessorFactory(
    resultsQueue: Queue[IO, KinesisClientRecord]
)(using R: IORuntime)
    extends ShardRecordProcessorFactory:
  override def shardRecordProcessor(): ShardRecordProcessor =
    KCLRecordProcessor(resultsQueue)
  override def shardRecordProcessor(
      streamIdentifier: StreamIdentifier
  ): ShardRecordProcessor = KCLRecordProcessor(resultsQueue)
