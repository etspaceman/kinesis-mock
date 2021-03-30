package kinesis.mock

import scala.util.Try

import java.security.SecureRandom
import java.util.Base64

import cats.effect.{ContextShift, IO, Timer}
import cats.syntax.all._
import com.github.f4b6a3.uuid.UuidCreator
import io.circe.Encoder
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.{Origin, _}
import org.http4s.util.CaseInsensitiveString

import kinesis.mock.api._
import kinesis.mock.cache.Cache
import kinesis.mock.instances.http4s._

class KinesisMockRoutes(cache: Cache)(implicit
    T: Timer[IO],
    CS: ContextShift[IO]
) {
  import KinesisMockHeaders._
  import KinesisMockRoutes._
  import KinesisMockMediaTypes._
  import KinesisMockQueryParams._
  // check headers / query params (see what kinesalite does)
  // create a sharded stream cache
  // create service that has methods for each action
  def routes = HttpRoutes.of[IO] {
    case request @ POST -> Root :?
        AmazonAuthAlgorithm(queryAuthAlgorith) :?
        AmazonAuthCredential(queryAuthCredential) :?
        AmazonAuthSignature(queryAuthSignature) :?
        AmazonAuthSignedHeaders(queryAuthSignedHeaders) :?
        AmazonDate(queryAmazonDate) :?
        Action(queryAction) =>
      val requestIdHeader = Header(
        amazonRequestId,
        UuidCreator.toString(UuidCreator.getTimeBased)
      )

      val amazonId2Header = request.headers
        .get(Origin)
        .fold {
          val bytes = new Array[Byte](72)
          new SecureRandom().nextBytes(bytes)
          List(
            Header(
              amazonId2,
              new String(Base64.getEncoder.encode(bytes), "UTF-8")
            )
          )
        }(_ => List.empty)

      val accessControlHeaders = request.headers
        .get(Origin)
        .fold(List.empty[Header])(_ =>
          List(
            Header(accessControlAllowOrigin, "*"),
            Header(
              accessControlExposeHeaders,
              "x-amzn-RequestId,x-amzn-ErrorType,x-amz-request-id,x-amz-id-2,x-amzn-ErrorMessage,Date"
            )
          )
        )

      val responseHeaders =
        amazonId2Header ++ accessControlHeaders :+ requestIdHeader

      val authorizationHeader = request.headers.get(Authorization)

      val action: Option[KinesisAction] = queryAction.orElse {
        request.headers.get(CaseInsensitiveString(amazonTarget)).flatMap { h =>
          val split = Try(h.value.split(".").toList).toOption.toList.flatten
          (split.headOption, split.get(1L)) match {
            case (Some(service), Some(act)) if service == "Kinesis_20131202" =>
              KinesisAction.withNameOption(act)
            case _ => None
          }
        }
      }

      (request.contentType, authorizationHeader, queryAuthAlgorith) match {
        case (None, _, _) =>
          NotFound(
            errorMessage("UnknownOperationException", None),
            responseHeaders: _*
          )
        case (Some(contentType), _, _)
            if !validContentTypes.contains(contentType.mediaType) =>
          NotFound(
            errorMessage("UnknownOperationException", None),
            responseHeaders: _*
          )
        case (Some(contentType), Some(_), Some(_)) =>
          implicit def entityEncoder[A: Encoder] =
            kinesisMockEntityEncoder(contentType.mediaType)
          BadRequest(
            ErrorResponse(
              "InvalidSignatureException",
              "Found both \'X-Amz-Algorithm\' as a query-string param and \'Authorization\' as HTTP header."
            ),
            responseHeaders: _*
          )
        case (Some(contentType), None, None) =>
          implicit def entityEncoder[A: Encoder] =
            kinesisMockEntityEncoder(contentType.mediaType)
          BadRequest(
            ErrorResponse(
              "MissingAuthenticationTokenException",
              "Missing Authentication Token"
            ),
            responseHeaders: _*
          )
        case (Some(contentType), Some(authHeader), _) =>
          implicit def entityEncoder[A: Encoder] =
            kinesisMockEntityEncoder(contentType.mediaType)
          val authParsed = authHeader.value
            .split("/")
            .toList
            .map(_.replace(",", ""))
            .filter(_.nonEmpty)
            .map { x =>
              val keyVal = x.trim().split("=").toList
              keyVal.headOption -> keyVal.get(1L)
            }
            .flatMap {
              case (Some(k), Some(v)) => List(k -> v)
              case _                  => List.empty
            }
            .toMap
          val expectedAuthKeys =
            List("Credential", "Signature", "SignedHeaders")

          val missingKeysMsg: Option[String] = expectedAuthKeys
            .diff(
              authParsed.keys.toList.filter(expectedAuthKeys.contains)
            )
            .foldLeft(none[String]) { case (msg, k) =>
              val newMsg = s"Authorization header requires \\$k\\ parameter."
              msg.fold(Some(newMsg))(str => Some(s"$str $newMsg"))
            }

          val missingDateMsg =
            if (
              request.headers
                .get(CaseInsensitiveString(amazonDate))
                .isEmpty && request.headers.get(Date).isEmpty
            )
              Some(
                "Authorization header requires existence of either a \\'X-Amz-Date\\' or a \\'Date\\' header."
              )
            else None

          val authErrMsg = (missingKeysMsg, missingDateMsg) match {
            case (Some(x), Some(y)) => Some(s"$x $y")
            case (Some(x), _)       => Some(x)
            case (_, Some(y))       => Some(y)
            case _                  => None
          }

          authErrMsg match {
            case Some(e) =>
              BadRequest(
                ErrorResponse("IncompleteSignatureException", e),
                responseHeaders: _*
              )
            case None =>
              action match {
                case Some(ac) =>
                  processAction(request, ac, cache, responseHeaders)
                case None =>
                  BadRequest(
                    ErrorResponse(
                      "AccessDeniedException",
                      "Unable to determine service/operation name to be authorized"
                    ),
                    responseHeaders: _*
                  )
              }
          }
        case (Some(contentType), _, Some(_)) =>
          implicit def entityEncoder[A: Encoder] =
            kinesisMockEntityEncoder(contentType.mediaType)

          val missing = List(
            queryAuthSignature.as(
              s"AWS query-string parameters must include \\${amazonAuthSignature}\\."
            ),
            queryAuthCredential.as(
              s"AWS query-string parameters must include \\${amazonAuthCredential}\\."
            ),
            queryAuthSignedHeaders.as(
              s"AWS query-string parameters must include \\${amazonAuthSignedHeaders}\\."
            ),
            queryAmazonDate.as(
              s"AWS query-string parameters must include \\${amazonDate}\\."
            )
          ).flatMap {
            case Some(msg) => List(msg)
            case None      => List.empty
          }

          if (missing.nonEmpty) {
            BadRequest(
              ErrorResponse(
                "IncompleteSignatureException",
                s"${missing.mkString(" ")} Re-examine the query-string parameters."
              ),
              responseHeaders: _*
            )
          } else {
            action match {
              case Some(ac) =>
                processAction(request, ac, cache, responseHeaders)
              case None =>
                BadRequest(
                  ErrorResponse(
                    "AccessDeniedException",
                    "Unable to determine service/operation name to be authorized"
                  ),
                  responseHeaders: _*
                )
            }
          }

      }

    case request @ OPTIONS -> Root =>
      val requestIdHeader = Header(
        amazonRequestId,
        UuidCreator.toString(UuidCreator.getTimeBased)
      )
      if (request.headers.get(Origin).isEmpty)
        BadRequest(
          errorMessage(
            "AccessDeniedException",
            Some(
              "Unable to determine service/authorization name to be authorized"
            )
          ),
          requestIdHeader
        )
      else {
        val responseHeaders: List[Header] =
          request.headers
            .get(Origin)
            .fold(List.empty[Header])(_ =>
              List(
                Header(accessControlAllowOrigin, "*"),
                Header(
                  accessControlExposeHeaders,
                  "x-amzn-RequestId,x-amzn-ErrorType,x-amz-request-id,x-amz-id-2,x-amzn-ErrorMessage,Date"
                ),
                Header(accessControlMaxAge, "172800")
              ) ++ request.headers
                .get(CaseInsensitiveString(accessControlRequestHeaders))
                .map(h => Header(accessControlAllowHeaders, h.value))
                .toList ++ request.headers
                .get(CaseInsensitiveString(accessControlRequestMethod))
                .map(h => Header(accessControlAllowMethods, h.value))
                .toList
            ) :+ requestIdHeader

        Ok("", responseHeaders: _*)
      }
  }
}

object KinesisMockRoutes {
  def errorMessage(`type`: String, message: Option[String]): String =
    message match {
      case Some(msg) => s"<${`type`}>\n <Message>$msg</Message>\n</${`type`}>\n"
      case None      => s"<${`type`}/>\n"
    }

  def handleDecodeError(err: DecodeFailure, responseHeaders: List[Header])(
      implicit entityEncoder: EntityEncoder[IO, ErrorResponse]
  ) =
    BadRequest(
      ErrorResponse("SerializationException", err.getMessage),
      responseHeaders: _*
    )

  def handleKinesisMockError(
      err: KinesisMockException,
      responseHeaders: List[Header]
  )(implicit
      entityEncoder: EntityEncoder[IO, ErrorResponse]
  ) =
    BadRequest(
      ErrorResponse(err.getClass.getName, err.getMessage),
      responseHeaders: _*
    )

  def processAction(
      request: Request[IO],
      action: KinesisAction,
      cache: Cache,
      responseHeaders: List[Header]
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO],
      errEE: EntityEncoder[IO, ErrorResponse],
      descLimitsEE: EntityEncoder[IO, DescribeLimitsResponse],
      descStreamEE: EntityEncoder[IO, DescribeStreamResponse],
      descStreamConsumerEE: EntityEncoder[IO, DescribeStreamConsumerResponse],
      descStreamSummaryEE: EntityEncoder[IO, DescribeStreamSummaryResponse],
      disableMonitoringEE: EntityEncoder[IO, DisableEnhancedMonitoringResponse],
      enableMonitoringEE: EntityEncoder[IO, EnableEnhancedMonitoringResponse],
      getRecordsEE: EntityEncoder[IO, GetRecordsResponse],
      getShardIteratorEE: EntityEncoder[IO, GetShardIteratorResponse],
      listShardsEE: EntityEncoder[IO, ListShardsResponse],
      listStreamConsumersEE: EntityEncoder[IO, ListStreamConsumersResponse],
      listStreamsEE: EntityEncoder[IO, ListStreamsResponse],
      listTagsEE: EntityEncoder[IO, ListTagsForStreamResponse],
      putRecordEE: EntityEncoder[IO, PutRecordResponse],
      putRecordsEE: EntityEncoder[IO, PutRecordsResponse],
      registerConsumerEE: EntityEncoder[IO, RegisterStreamConsumerResponse]
  ): IO[Response[IO]] =
    action match {
      case KinesisAction.AddTagsToStream =>
        request
          .attemptAs[AddTagsToStreamRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .addTagsToStream(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.CreateStream =>
        request
          .attemptAs[CreateStreamRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .createStream(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.DecreaseStreamRetentionPeriod =>
        request
          .attemptAs[DecreaseStreamRetentionPeriodRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .decreaseStreamRetention(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.DeleteStream =>
        request
          .attemptAs[DeleteStreamRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .deleteStream(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.DeregisterStreamConsumer =>
        request
          .attemptAs[DeregisterStreamConsumerRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .deregisterStreamConsumer(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.DescribeLimits =>
        cache.describeLimits.flatMap(
          _.fold(
            err => handleKinesisMockError(err, responseHeaders),
            res => Ok(res, responseHeaders: _*)
          )
        )
      case KinesisAction.DescribeStream =>
        request
          .attemptAs[DescribeStreamRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .describeStream(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.DescribeStreamConsumer =>
        request
          .attemptAs[DescribeStreamConsumerRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .describeStreamConsumer(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.DescribeStreamSummary =>
        request
          .attemptAs[DescribeStreamSummaryRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .describeStreamSummary(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.DisableEnhancedMonitoring =>
        request
          .attemptAs[DisableEnhancedMonitoringRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .disableEnhancedMonitoring(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.EnableEnhancedMonitoring =>
        request
          .attemptAs[EnableEnhancedMonitoringRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .enableEnhancedMonitoring(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.GetRecords =>
        request
          .attemptAs[GetRecordsRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .getRecords(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.GetShardIterator =>
        request
          .attemptAs[GetShardIteratorRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .getShardIterator(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.IncreaseStreamRetentionPeriod =>
        request
          .attemptAs[IncreaseStreamRetentionPeriodRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .increaseStreamRetention(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.ListShards =>
        request
          .attemptAs[ListShardsRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .listShards(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.ListStreamConsumers =>
        request
          .attemptAs[ListStreamConsumersRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .listStreamConsumers(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.ListStreams =>
        request
          .attemptAs[ListStreamsRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .listStreams(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.ListTagsForStream =>
        request
          .attemptAs[ListTagsForStreamRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .listTagsForStream(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.MergeShards =>
        request
          .attemptAs[MergeShardsRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .mergeShards(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.PutRecord =>
        request
          .attemptAs[PutRecordRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .putRecord(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.PutRecords =>
        request
          .attemptAs[PutRecordsRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .putRecords(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.RegisterStreamConsumer =>
        request
          .attemptAs[RegisterStreamConsumerRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .registerStreamConsumer(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.RemoveTagsFromStream =>
        request
          .attemptAs[RemoveTagsFromStreamRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .removeTagsFromStream(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.SplitShard =>
        request
          .attemptAs[SplitShardRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .splitShard(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.StartStreamEncryption =>
        request
          .attemptAs[StartStreamEncryptionRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .startStreamEncryption(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.StopStreamEncryption =>
        request
          .attemptAs[StopStreamEncryptionRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .stopStreamEncryption(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
      case KinesisAction.SubscribeToShard =>
        NotFound(
          ErrorResponse(
            "ApiNotImplemented",
            "SubscribeToShard is not yet supported"
          ),
          responseHeaders: _*
        )
      case KinesisAction.UpdateShardCount =>
        request
          .attemptAs[UpdateShardCountRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .updateShardCount(req)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders: _*)
                  )
                )
          )
    }
}
