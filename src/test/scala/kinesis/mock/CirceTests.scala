package kinesis.mock

import scala.reflect.ClassTag

import cats.kernel.Eq
import cats.syntax.all._
import enumeratum.scalacheck._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class CirceTests extends munit.ScalaCheckSuite {
  def identityLawTest[A: Encoder: Decoder: Arbitrary: Eq](implicit
      loc: munit.Location,
      CT: ClassTag[A]
  ) =
    property(s"Circe Identity Laws Test for ${CT.runtimeClass.getName()}") {
      forAll { a: A =>
        val encoded = a.asJson.noSpaces
        val decoded = parse(encoded).flatMap(_.as[A])

        decoded.exists(_ === a) :| s"\n\tInput:\n\t${a}\n\tDecoded:\n\t${decoded
          .fold(_.toString, _.toString)}"
      }

    }

  identityLawTest[AwsRegion]
  identityLawTest[Consumer]
  identityLawTest[ConsumerStatus]
  identityLawTest[EncryptionType]
  identityLawTest[HashKeyRange]
  identityLawTest[KinesisRecord]
  identityLawTest[ShardLevelMetric]
  identityLawTest[ShardLevelMetrics]
  identityLawTest[SequenceNumber]
  identityLawTest[SequenceNumberConstant]
  identityLawTest[SequenceNumberRange]
  identityLawTest[Shard]
  identityLawTest[StreamStatus]

  identityLawTest[AddTagsToStreamRequest]
  identityLawTest[CreateStreamRequest]
  identityLawTest[DecreaseStreamRetentionRequest]
  identityLawTest[DeleteStreamRequest]
  identityLawTest[DeregisterStreamConsumerRequest]
  identityLawTest[DescribeLimitsResponse]
  identityLawTest[DescribeStreamConsumerRequest]
  identityLawTest[DescribeStreamRequest]
  identityLawTest[DescribeStreamResponse]
  identityLawTest[DescribeStreamSummaryRequest]
  identityLawTest[DescribeStreamSummaryResponse]
  identityLawTest[DisableEnhancedMonitoringRequest]
  identityLawTest[DisableEnhancedMonitoringResponse]
  identityLawTest[EnableEnhancedMonitoringRequest]
  identityLawTest[EnableEnhancedMonitoringResponse]
  identityLawTest[GetRecordsRequest]
  identityLawTest[GetRecordsResponse]
  identityLawTest[GetShardIteratorRequest]
  identityLawTest[GetShardIteratorResponse]
  identityLawTest[ListShardsRequest]
  identityLawTest[ListShardsResponse]
  identityLawTest[ListStreamConsumersRequest]
  identityLawTest[ListStreamConsumersResponse]
  identityLawTest[ListStreamsRequest]
  identityLawTest[ListStreamsResponse]
  identityLawTest[ListTagsForStreamRequest]
  identityLawTest[ListTagsForStreamResponse]
  identityLawTest[MergeShardsRequest]
  identityLawTest[PutRecordRequest]
  identityLawTest[PutRecordResponse]
  identityLawTest[PutRecordsRequest]
  identityLawTest[PutRecordsRequestEntry]
  identityLawTest[PutRecordsResponse]
  identityLawTest[PutRecordsResultEntry]
  identityLawTest[RegisterStreamConsumerRequest]
  identityLawTest[RegisterStreamConsumerResponse]
  identityLawTest[ScalingType]
  identityLawTest[ShardFilter]
  identityLawTest[ShardFilterType]
  identityLawTest[ShardIterator]
  identityLawTest[ShardIteratorType]
  identityLawTest[ShardSummary]
  identityLawTest[SplitShardRequest]
  identityLawTest[StartStreamEncryptionRequest]
  identityLawTest[StopStreamEncryptionRequest]
  identityLawTest[StreamDescription]
  identityLawTest[StreamDescriptionSummary]
  identityLawTest[UpdateShardCountRequest]

}
