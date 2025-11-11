package kinesis.mock

import scala.jdk.CollectionConverters._

import java.nio.charset.Charset

import cats.effect.IO
import cats.syntax.all._
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.id._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class GetRecordsTests extends AwsFunctionalTests {

  fixture().test("It should get records") { resources =>
    for {
      recordRequests <- IO(
        putRecordRequestArb.arbitrary
          .take(5)
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

  fixture().test("It should get records with quotes around shard iterator") {
    resources =>
      for {
        recordRequests <- IO(
          putRecordRequestArb.arbitrary
            .take(1)
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
                .maybeTransform(x.sequenceNumberForOrdering)(
                  (req, sequenceNum) =>
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
              GetRecordsRequest
                .builder()
                .shardIterator(s"\"$shardIterator\"")
                .build()
            )
            .toIO
        )
        res = gets.flatMap(_.records().asScala.toVector)
      } yield assert(
        res.length == 1 && res.forall(rec =>
          recordRequests.exists(req =>
            req.data.asByteArray.sameElements(rec.data.asByteArray)
              && req.partitionKey == rec.partitionKey
          )
        ),
        s"$res\n$recordRequests"
      )
  }

  fixture(1).test("It should get records after a sequence number") { resources =>
    for {
      putResp1 <- resources.kinesisClient
        .putRecord(
          PutRecordRequest
            .builder()
            .partitionKey("test-key-0")
            .streamName(resources.streamName.streamName)
            .data(SdkBytes.fromString("AA==", Charset.defaultCharset()))
            .build()
        )
        .toIO
      resp2PartitionKey = "test-key-1"
      resp2Data = SdkBytes.fromString("AQ==", Charset.defaultCharset())
      _ <- resources.kinesisClient
        .putRecord(
          PutRecordRequest
            .builder()
            .partitionKey(resp2PartitionKey)
            .streamName(resources.streamName.streamName)
            .data(resp2Data)
            .build()
        )
        .toIO
      shardId = putResp1.shardId()
      shardIterator <- resources.kinesisClient
        .getShardIterator(
          GetShardIteratorRequest
            .builder()
            .shardId(shardId)
            .streamName(resources.streamName.streamName)
            .shardIteratorType(ShardIteratorType.AFTER_SEQUENCE_NUMBER)
            .startingSequenceNumber(putResp1.sequenceNumber())
            .build()
        )
        .toIO
        .map(_.shardIterator())
      res <- resources.kinesisClient
        .getRecords(
          GetRecordsRequest.builder().shardIterator(shardIterator).build()
        )
        .toIO
        .map(_.records().asScala.toVector)
    } yield assert(
      res.length == 1 && res.headOption.exists(rec =>
        resp2Data.asByteArray.sameElements(rec.data.asByteArray)
          && resp2PartitionKey == rec.partitionKey
      ),
      s"$res"
    )
  }

  fixture(1).test("It should get records using subsequent shard iterators") {
    resources =>
      for {
        shard <- resources.kinesisClient
          .listShards(
            ListShardsRequest
              .builder()
              .streamName(resources.streamName.streamName)
              .build()
          )
          .toIO
          .map(_.shards().getFirst())
        shardIterator <- resources.kinesisClient
          .getShardIterator(
            GetShardIteratorRequest
              .builder()
              .shardId(shard.shardId())
              .streamName(resources.streamName.streamName)
              .shardIteratorType(ShardIteratorType.LATEST)
              .build()
          )
          .toIO
          .map(_.shardIterator())
        partitionKey = "test-key"
        data = SdkBytes.fromString("AA==", Charset.defaultCharset())
        _ <- resources.kinesisClient
          .putRecord(
            PutRecordRequest
              .builder()
              .partitionKey(partitionKey)
              .streamName(resources.streamName.streamName)
              .data(data)
              .build()
          )
          .toIO
        res1 <- resources.kinesisClient
          .getRecords(
            GetRecordsRequest.builder().shardIterator(shardIterator).build()
          )
          .toIO
        nextShardIterator = res1.nextShardIterator()
        res1Records = res1.records().asScala.toVector
        res2 <- resources.kinesisClient
          .getRecords(
            GetRecordsRequest.builder().shardIterator(nextShardIterator).build()
          )
          .toIO
        res2Records = res2.records().asScala.toVector
      } yield assert(
        res1Records.length == 1 && res1Records.headOption.exists(rec =>
          data.asByteArray.sameElements(rec.data.asByteArray)
            && partitionKey == rec.partitionKey
        ) && res2Records.isEmpty,
        s"$res1Records\n\n$res2Records"
      )
  }
}
