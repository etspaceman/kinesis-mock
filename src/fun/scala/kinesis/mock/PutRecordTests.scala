package kinesis.mock

import scala.jdk.CollectionConverters._

import cats.effect.IO
import cats.syntax.all._
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.id._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class PutRecordTests extends munit.CatsEffectSuite with AwsFunctionalTests {

  fixture.test("It should put a record") { resources =>
    for {
      recordRequests <- IO(
        putRecordRequestArb.arbitrary
          .take(5)
          .toList
          .map(_.copy(streamName = resources.streamName))
          .map(x =>
            PutRecordRequest
              .builder()
              .partitionKey(x.partitionKey)
              .streamName(x.streamName.streamName)
              .data(SdkBytes.fromByteArray(x.data))
              .maybeTransform(x.explicitHashKey)(_.explicitHashKey(_))
              .maybeTransform(x.sequenceNumberForOrdering)((req, sequenceNum) =>
                req.sequenceNumberForOrdering(sequenceNum.value)
              )
              .build()
          )
      )
      _ <- recordRequests.traverse(x =>
        resources.kinesisClient.putRecord(x).toIO
      )
      shards <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
        .map(_.shards().asScala.toList)
      shardIterators <- shards.traverse(shard =>
        resources.kinesisClient
          .getShardIterator(
            GetShardIteratorRequest
              .builder()
              .shardId(shard.shardId())
              .streamName(resources.streamName.streamName)
              .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
              .build()
          )
          .toIO
          .map(_.shardIterator())
      )
      gets <- shardIterators.traverse(shardIterator =>
        resources.kinesisClient
          .getRecords(
            GetRecordsRequest.builder().shardIterator(shardIterator).build()
          )
          .toIO
      )
      res = gets.flatMap(_.records().asScala.toList)
    } yield assert(
      res.length == 5 && res.forall(rec =>
        recordRequests.exists(req =>
          req.data.asByteArray.sameElements(rec.data.asByteArray)
            && req.partitionKey == rec.partitionKey
        )
      ),
      s"$res\n$recordRequests"
    )
  }
}
