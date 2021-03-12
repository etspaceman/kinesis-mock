package kinesis.mock
package api

import java.time.Instant

import cats.data.Validated._
import cats.data._
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_ListShards.html
final case class ListShardsRequest(
    exclusiveStartShardId: Option[String],
    maxResults: Option[Int],
    nextToken: Option[String],
    shardFilter: Option[ShardFilter],
    streamCreationTimestamp: Option[Instant],
    streamName: Option[String]
) {
  def listShards(
      streams: Streams
  ): ValidatedNel[KinesisMockException, ListShardsResponse] =
    (
      exclusiveStartShardId,
      nextToken,
      shardFilter,
      streamCreationTimestamp,
      streamName
    ) match {
      case (None, Some(nt), None, None, None) =>
        CommonValidations
          .validateNextToken(nt)
          .andThen(ListShardsRequest.parseNextToken)
          .andThen { case (streamName, shardId) =>
            (
              CommonValidations.validateStreamName(streamName),
              CommonValidations.validateShardId(shardId),
              CommonValidations.findStream(streamName, streams),
              maxResults match {
                case Some(l) if (l < 1 || l > 10000) =>
                  InvalidArgumentException(
                    s"MaxResults must be between 1 and 10000"
                  ).invalidNel
                case _ => Valid(())
              },
              shardFilter match {
                case Some(sf) => ListShardsRequest.validateShardFilter(sf)
                case None     => Valid(())
              }
            ).mapN((_, _, stream, _, _) => {
              val allShards = stream.shards.keys.toList
              val lastShardIndex = allShards.length - 1
              val limit = maxResults.map(l => Math.min(l, 100)).getOrElse(100)
              val firstIndex = allShards.indexWhere(_.shardId == shardId) + 1
              val lastIndex = Math.min(firstIndex + limit, lastShardIndex) + 1
              val shards = allShards.slice(firstIndex, lastIndex)
              val nextToken =
                if (lastShardIndex + 1 == lastIndex) None
                else
                  Some(
                    ListShardsRequest
                      .createNextToken(streamName, shards.last.shardId)
                  )
              ListShardsResponse(nextToken, shards)
            })
          }
      case (_, None, _, _, Some(sName)) =>
        CommonValidations
          .findStream(sName, streams)
          .andThen(stream =>
            (
              CommonValidations.validateStreamName(sName),
              exclusiveStartShardId match {
                case Some(eShardId) =>
                  CommonValidations.validateShardId(eShardId)
                case None => Valid(())
              },
              shardFilter match {
                case Some(sf) => ListShardsRequest.validateShardFilter(sf)
                case None     => Valid(())
              }
            ).mapN((_, _, _) => {
              val allShards: List[Shard] = stream.shards.keys.toList
              val filteredShards = shardFilter match {
                case Some(sf)
                    if sf.`type` == ShardFilterType.AT_TRIM_HORIZON ||
                      (sf.`type` == ShardFilterType.AT_TIMESTAMP && sf.timestamp
                        .exists(x =>
                          x.getEpochSecond() < stream.streamCreationTimestamp
                            .getEpochSecond()
                        )) ||
                      (sf.`type` == ShardFilterType.FROM_TIMESTAMP && sf.timestamp
                        .exists(x =>
                          x.getEpochSecond() < stream.streamCreationTimestamp
                            .getEpochSecond()
                        )) =>
                  allShards
                case Some(sf)
                    if sf.`type` == ShardFilterType.FROM_TRIM_HORIZON => {
                  val now = Instant.now()
                  allShards.filter(x =>
                    x.closedTimestamp.isEmpty || x.closedTimestamp.exists(x =>
                      x.plusSeconds(stream.retentionPeriod.toSeconds)
                        .getEpochSecond() <= now.getEpochSecond()
                    )
                  )
                }
                case Some(sf) if sf.`type` == ShardFilterType.AT_LATEST =>
                  allShards.filter(_.isOpen)
                case Some(sf)
                    if sf.`type` == ShardFilterType.AT_TIMESTAMP || sf.`type` == ShardFilterType.FROM_TIMESTAMP =>
                  allShards.filter(x =>
                    x.isOpen || sf.timestamp.exists(ts =>
                      x.createdAtTimestamp.getEpochSecond() <= ts
                        .getEpochSecond() && x.closedTimestamp.exists(cTs =>
                        cTs.getEpochSecond() >= ts.getEpochSecond()
                      )
                    )
                  )
                case Some(sf)
                    if sf.`type` == ShardFilterType.AFTER_SHARD_ID => {
                  val index = sf.shardId
                    .map(eShardId =>
                      allShards.indexWhere(_.shardId == eShardId) + 1
                    )
                    .getOrElse(0)
                  allShards.slice(index, allShards.length - 1)
                }
                case _ => allShards
              }
              val lastShardIndex = filteredShards.length - 1
              val limit = maxResults.map(l => Math.min(l, 100)).getOrElse(100)
              val firstIndex = exclusiveStartShardId
                .map(eShardId =>
                  filteredShards.indexWhere(_.shardId == eShardId) + 1
                )
                .getOrElse(0)
              val lastIndex = Math.min(firstIndex + limit, lastShardIndex) + 1
              val shards = filteredShards.slice(firstIndex, lastIndex)
              val nextToken =
                if (lastShardIndex + 1 == lastIndex) None
                else
                  Some(
                    ListShardsRequest
                      .createNextToken(sName, shards.last.shardId)
                  )
              ListShardsResponse(nextToken, shards)
            })
          )
      case (_, None, _, _, None) =>
        InvalidArgumentException(
          "StreamName is required if NextToken is not provided"
        ).invalidNel
      case _ =>
        InvalidArgumentException(
          "Cannot define ExclusiveStartShardId, StreamCreationTimestamp or StreamName if NextToken is defined"
        ).invalidNel
    }
}

object ListShardsRequest {
  implicit val listShardsRequestEncoder: Encoder[ListShardsRequest] =
    Encoder.forProduct6(
      "ExclusiveStartShardId",
      "MaxResults",
      "NextToken",
      "ShardFilter",
      "StreamCreationTimestamp",
      "StreamName"
    )(x =>
      (
        x.exclusiveStartShardId,
        x.maxResults,
        x.nextToken,
        x.shardFilter,
        x.streamCreationTimestamp,
        x.streamName
      )
    )
  implicit val listShardsRequestDecoder: Decoder[ListShardsRequest] = { x =>
    for {
      exclusiveStartShardId <- x
        .downField("ExclusiveStartShardId")
        .as[Option[String]]
      maxResults <- x.downField("MaxResults").as[Option[Int]]
      nextToken <- x.downField("NextToken").as[Option[String]]
      shardFilter <- x.downField("ShardFilter").as[Option[ShardFilter]]
      shardCreationTimestamp <- x
        .downField("ShardCreationTimestamp")
        .as[Option[Instant]]
      streamName <- x.downField("StreamName").as[Option[String]]
    } yield ListShardsRequest(
      exclusiveStartShardId,
      maxResults,
      nextToken,
      shardFilter,
      shardCreationTimestamp,
      streamName
    )
  }

  def createNextToken(streamName: String, shardId: String): String =
    s"$streamName::$shardId"
  def parseNextToken(
      nextToken: String
  ): ValidatedNel[KinesisMockException, (String, String)] = {
    val split = nextToken.split("::")
    if (split.length != 2)
      InvalidArgumentException(s"NextToken is improperly formatted").invalidNel
    else Valid((split.head, split(1)))
  }
  def validateShardFilter(
      shardFilter: ShardFilter
  ): ValidatedNel[KinesisMockException, ShardFilter] =
    shardFilter.`type` match {
      case ShardFilterType.AFTER_SHARD_ID if shardFilter.shardId.isEmpty =>
        InvalidArgumentException(
          "ShardId must be supplied in a ShardFilter with a Type of AFTER_SHARD_ID"
        ).invalidNel
      case ShardFilterType.AT_TIMESTAMP | ShardFilterType.FROM_TIMESTAMP
          if shardFilter.timestamp.isEmpty =>
        InvalidArgumentException(
          "Timestamp must be supplied in a ShardFilter with a Type of FROM_TIMESTAMP or AT_TIMESTAMP"
        ).invalidNel
      case _ => Valid(shardFilter)
    }
}
