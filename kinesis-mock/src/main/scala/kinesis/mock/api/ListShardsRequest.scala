/*
 * Copyright 2021-2023 Typelevel
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
package api

import java.time.Instant

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.instances.circe._
import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_ListShards.html
final case class ListShardsRequest(
    exclusiveStartShardId: Option[String],
    maxResults: Option[Int],
    nextToken: Option[String],
    shardFilter: Option[ShardFilter],
    streamCreationTimestamp: Option[Instant],
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def listShards(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[ListShardsResponse]] = Utils.now.flatMap { now =>
    streamsRef.get.map { streams =>
      (
        exclusiveStartShardId,
        nextToken,
        shardFilter,
        streamCreationTimestamp,
        streamName,
        streamArn
      ) match {
        case (None, Some(nt), None, None, None, None) =>
          CommonValidations
            .validateNextToken(nt)
            .flatMap(ListShardsRequest.parseNextToken)
            .flatMap { case (streamName, shardId) =>
              val streamArn = StreamArn(awsRegion, streamName, awsAccountId)
              (
                CommonValidations.validateStreamName(streamName),
                CommonValidations.validateShardId(shardId),
                CommonValidations.findStream(streamArn, streams),
                maxResults match {
                  case Some(l) => CommonValidations.validateMaxResults(l)
                  case _       => Right(())
                },
                shardFilter match {
                  case Some(sf) => ListShardsRequest.validateShardFilter(sf)
                  case None     => Right(())
                }
              ).mapN { (_, _, stream, _, _) =>
                val allShards = stream.shards.keys.toVector
                val lastShardIndex = allShards.length - 1
                val limit = maxResults.map(l => Math.min(l, 100)).getOrElse(100)
                val firstIndex =
                  allShards.indexWhere(_.shardId.shardId == shardId) + 1
                val lastIndex = Math.min(firstIndex + limit, lastShardIndex + 1)
                val shards = allShards.slice(firstIndex, lastIndex)
                val nextToken =
                  if (lastShardIndex + 1 == lastIndex) None
                  else
                    Some(
                      ListShardsRequest
                        .createNextToken(
                          streamName,
                          shards.last.shardId.shardId
                        )
                    )
                ListShardsResponse(
                  nextToken,
                  shards.map(ShardSummary.fromShard)
                )
              }
            }
        case (_, None, _, _, _, Some(sArn)) =>
          getList(sArn, sArn.streamName, streams, now)
        case (_, None, _, _, Some(sName), _) =>
          val streamArn = StreamArn(awsRegion, sName, awsAccountId)
          getList(streamArn, sName, streams, now)
        case (_, None, _, _, None, None) =>
          InvalidArgumentException(
            "StreamName or StreamARN is required if NextToken is not provided"
          ).asLeft
        case _ =>
          InvalidArgumentException(
            "Cannot define ExclusiveStartShardId, StreamCreationTimestamp or StreamName if NextToken is defined"
          ).asLeft
      }
    }
  }

  def getList(
      streamArn: StreamArn,
      sName: StreamName,
      streams: Streams,
      now: Instant
  ) = CommonValidations
    .findStream(streamArn, streams)
    .flatMap(stream =>
      (
        CommonValidations.validateStreamName(sName),
        exclusiveStartShardId match {
          case Some(eShardId) =>
            CommonValidations.validateShardId(eShardId)
          case None => Right(())
        },
        shardFilter match {
          case Some(sf) => ListShardsRequest.validateShardFilter(sf)
          case None     => Right(())
        }
      ).mapN { (_, _, _) =>
        val allShards: Vector[Shard] = stream.shards.keys.toVector
        val filteredShards = shardFilter match {
          case Some(sf)
              if sf.`type` == ShardFilterType.AT_TRIM_HORIZON ||
                (sf.`type` == ShardFilterType.AT_TIMESTAMP && sf.timestamp
                  .exists(x =>
                    x.getEpochSecond < stream.streamCreationTimestamp.getEpochSecond
                  )) ||
                (sf.`type` == ShardFilterType.FROM_TIMESTAMP && sf.timestamp
                  .exists(x =>
                    x.getEpochSecond < stream.streamCreationTimestamp.getEpochSecond
                  )) =>
            allShards
          case Some(sf) if sf.`type` == ShardFilterType.FROM_TRIM_HORIZON =>
            allShards.filter(x =>
              x.closedTimestamp.isEmpty || x.closedTimestamp.exists(x =>
                x.plusSeconds(stream.retentionPeriod.toSeconds)
                  .getEpochSecond >= now.getEpochSecond
              )
            )

          case Some(sf) if sf.`type` == ShardFilterType.AT_LATEST =>
            allShards.filter(_.isOpen)

          case Some(sf) if sf.`type` == ShardFilterType.AT_TIMESTAMP =>
            allShards.filter(x =>
              sf.timestamp.exists(ts =>
                x.createdAtTimestamp.getEpochSecond <= ts.getEpochSecond && (x.isOpen || x.closedTimestamp
                  .exists(cTs => cTs.getEpochSecond >= ts.getEpochSecond))
              )
            )

          case Some(sf) if sf.`type` == ShardFilterType.FROM_TIMESTAMP =>
            allShards.filter(x =>
              x.isOpen || sf.timestamp.exists(ts =>
                x.closedTimestamp
                  .exists(cTs => cTs.getEpochSecond >= ts.getEpochSecond)
              )
            )
          case Some(sf) if sf.`type` == ShardFilterType.AFTER_SHARD_ID =>
            val index = sf.shardId
              .map(eShardId =>
                allShards.indexWhere(_.shardId.shardId == eShardId) + 1
              )
              .getOrElse(0)
            allShards.slice(index, allShards.length)

          case _ => allShards
        }
        val lastShardIndex = filteredShards.length - 1
        val limit = maxResults.map(l => Math.min(l, 100)).getOrElse(100)
        val firstIndex = exclusiveStartShardId
          .map(eShardId =>
            filteredShards.indexWhere(_.shardId.shardId == eShardId) + 1
          )
          .getOrElse(0)
        val lastIndex = Math.min(firstIndex + limit, lastShardIndex + 1)
        val shards = filteredShards.slice(firstIndex, lastIndex)
        val nextToken =
          if (lastShardIndex + 1 == lastIndex) None
          else
            Some(
              ListShardsRequest
                .createNextToken(sName, shards.last.shardId.shardId)
            )
        ListShardsResponse(nextToken, shards.map(ShardSummary.fromShard))
      }
    )
}

object ListShardsRequest {
  def listShardsRequestCirceEncoder(implicit
      ESF: circe.Encoder[ShardFilter],
      EI: circe.Encoder[Instant]
  ): circe.Encoder[ListShardsRequest] =
    circe.Encoder.forProduct7(
      "ExclusiveStartShardId",
      "MaxResults",
      "NextToken",
      "ShardFilter",
      "StreamCreationTimestamp",
      "StreamName",
      "StreamARN"
    )(x =>
      (
        x.exclusiveStartShardId,
        x.maxResults,
        x.nextToken,
        x.shardFilter,
        x.streamCreationTimestamp,
        x.streamName,
        x.streamArn
      )
    )
  def listShardsRequestCirceDecoder(implicit
      DSF: circe.Decoder[ShardFilter],
      DI: circe.Decoder[Instant]
  ): circe.Decoder[ListShardsRequest] = { x =>
    for {
      exclusiveStartShardId <- x
        .downField("ExclusiveStartShardId")
        .as[Option[String]]
      maxResults <- x.downField("MaxResults").as[Option[Int]]
      nextToken <- x.downField("NextToken").as[Option[String]]
      shardFilter <- x.downField("ShardFilter").as[Option[ShardFilter]]
      streamCreationTimestamp <- x
        .downField("StreamCreationTimestamp")
        .as[Option[Instant]]
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    } yield ListShardsRequest(
      exclusiveStartShardId,
      maxResults,
      nextToken,
      shardFilter,
      streamCreationTimestamp,
      streamName,
      streamArn
    )
  }
  implicit val listShardsRequestEncoder: Encoder[ListShardsRequest] =
    Encoder.instance(
      listShardsRequestCirceEncoder(
        Encoder[ShardFilter].circeEncoder,
        instantBigDecimalCirceEncoder
      ),
      listShardsRequestCirceEncoder(
        Encoder[ShardFilter].circeCborEncoder,
        instantLongCirceEncoder
      )
    )
  implicit val listShardsRequestDecoder: Decoder[ListShardsRequest] =
    Decoder.instance(
      listShardsRequestCirceDecoder(
        Decoder[ShardFilter].circeDecoder,
        instantBigDecimalCirceDecoder
      ),
      listShardsRequestCirceDecoder(
        Decoder[ShardFilter].circeCborDecoder,
        instantLongCirceDecoder
      )
    )

  implicit val listShardsRequestEq: Eq[ListShardsRequest] =
    (x, y) =>
      x.exclusiveStartShardId == y.exclusiveStartShardId &&
        x.maxResults == y.maxResults &&
        x.nextToken == y.nextToken &&
        x.shardFilter === y.shardFilter &&
        x.streamCreationTimestamp.map(
          _.getEpochSecond()
        ) == y.streamCreationTimestamp.map(_.getEpochSecond()) &&
        x.streamName == y.streamName &&
        x.streamArn == y.streamArn

  def createNextToken(streamName: StreamName, shardId: String): String =
    s"$streamName::$shardId"
  def parseNextToken(
      nextToken: String
  ): Response[(StreamName, String)] = {
    val split = nextToken.split("::")
    if (split.length != 2)
      InvalidArgumentException(s"NextToken is improperly formatted").asLeft
    else Right((StreamName(split.head), split(1)))
  }
  def validateShardFilter(
      shardFilter: ShardFilter
  ): Response[ShardFilter] =
    shardFilter.`type` match {
      case ShardFilterType.AFTER_SHARD_ID if shardFilter.shardId.isEmpty =>
        InvalidArgumentException(
          "ShardId must be supplied in a ShardFilter with a Type of AFTER_SHARD_ID"
        ).asLeft
      case ShardFilterType.AT_TIMESTAMP | ShardFilterType.FROM_TIMESTAMP
          if shardFilter.timestamp.isEmpty =>
        InvalidArgumentException(
          "Timestamp must be supplied in a ShardFilter with a Type of FROM_TIMESTAMP or AT_TIMESTAMP"
        ).asLeft
      case _ => Right(shardFilter)
    }
}
