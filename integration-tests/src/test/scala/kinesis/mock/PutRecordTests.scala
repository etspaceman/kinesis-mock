package kinesis.mock

import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.syntax.all.*
import org.scalacheck.Arbitrary
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.syntax.id.*
import kinesis.mock.syntax.javaFuture.*
import kinesis.mock.syntax.scalacheck.*

class PutRecordTests extends AwsFunctionalTests:

  fixture.test("It should put a record") { resources =>
    for
      recordRequests <- IO(
        Arbitrary
          .arbitrary[kinesis.mock.api.PutRecordRequest]
          .take(20)
          .toVector
          .map(
            _.copy(streamName = Some(resources.streamName), streamArn = None)
          )
          .map(x =>
            PutRecordRequest
              .builder()
              .partitionKey(x.partitionKey)
              .streamName(resources.streamName.streamName)
              .data(SdkBytes.fromByteArray(x.data))
              .maybeTransform(x.explicitHashKey)(_.explicitHashKey(_))
              .maybeTransform(x.sequenceNumberForOrdering)((req, sequenceNum) =>
                req.sequenceNumberForOrdering(sequenceNum.value)
              )
              .build()
          )
      )
      _ <- recordRequests.parTraverse(x =>
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
        .map(_.shards().asScala.toVector)
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
      res = gets.flatMap(_.records().asScala.toVector)
    yield assert(
      res.length == 20 && res.forall(rec =>
        recordRequests.exists(req =>
          req.data.asByteArray.sameElements(rec.data.asByteArray)
            && req.partitionKey == rec.partitionKey
        )
      ),
      s"$res\n$recordRequests"
    )
  }
