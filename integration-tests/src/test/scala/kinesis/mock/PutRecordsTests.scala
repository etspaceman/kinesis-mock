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

class PutRecordsTests extends AwsFunctionalTests {

  fixture.test("It should put records") { resources =>
    for {
      reqs <- IO(
        List.fill(10)(
          PutRecordsRequest
            .builder()
            .records(
              putRecordsRequestEntryArb.arbitrary
                .take(5)
                .toVector
                .map(x =>
                  PutRecordsRequestEntry
                    .builder()
                    .data(SdkBytes.fromByteArray(x.data))
                    .partitionKey(x.partitionKey)
                    .maybeTransform(x.explicitHashKey)(_.explicitHashKey(_))
                    .build()
                )
                .asJava
            )
            .streamName(resources.streamName.streamName)
            .build()
        )
      )
      _ <- reqs.parTraverse(req => resources.kinesisClient.putRecords(req).toIO)
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
      records = reqs.flatMap(_.records.asScala.toVector)
    } yield assert(
      res.length == 50 && res.forall(rec =>
        records.exists(req =>
          req.data.asByteArray.sameElements(rec.data.asByteArray)
            && req.partitionKey == rec.partitionKey
        )
      ),
      s"$res\n$records"
    )
  }
}
