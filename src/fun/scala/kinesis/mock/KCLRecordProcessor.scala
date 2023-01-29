package kinesis.mock

import scala.jdk.CollectionConverters._

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import software.amazon.kinesis.common.StreamIdentifier
import software.amazon.kinesis.lifecycle.events._
import software.amazon.kinesis.processor._
import software.amazon.kinesis.retrieval.KinesisClientRecord

case class KCLRecordProcessor(
    resultsQueue: Queue[IO, KinesisClientRecord]
)(implicit R: IORuntime)
    extends ShardRecordProcessor {
  override def initialize(x: InitializationInput): Unit = ()
  override def processRecords(x: ProcessRecordsInput): Unit = x
    .records()
    .asScala
    .toVector
    .traverse_(record =>
      resultsQueue.offer(record) *> IO(
        x.checkpointer()
          .checkpoint(record.sequenceNumber(), record.subSequenceNumber())
      )
    )
    .unsafeRunSync()
  override def leaseLost(x: LeaseLostInput): Unit = ()
  override def shardEnded(x: ShardEndedInput): Unit =
    x.checkpointer().checkpoint()
  override def shutdownRequested(x: ShutdownRequestedInput): Unit = ()
}

case class KCLRecordProcessorFactory(
    resultsQueue: Queue[IO, KinesisClientRecord]
)(implicit R: IORuntime)
    extends ShardRecordProcessorFactory {
  override def shardRecordProcessor(): ShardRecordProcessor =
    KCLRecordProcessor(resultsQueue)
  override def shardRecordProcessor(
      streamIdentifier: StreamIdentifier
  ): ShardRecordProcessor = KCLRecordProcessor(resultsQueue)
}
