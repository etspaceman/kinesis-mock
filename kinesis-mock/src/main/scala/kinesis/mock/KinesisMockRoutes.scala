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

import scala.util.Random
import scala.util.Try

import java.util.Base64

import cats.effect.IO
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import org.http4s.DecodeFailure
import org.http4s.EntityEncoder
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Response as HResponse
//import org.http4s._
import org.http4s.dsl.io.*
import org.http4s.headers.*
import org.http4s.syntax.header.*
import org.typelevel.log4cats.SelfAwareStructuredLogger

import kinesis.mock.api.*
import kinesis.mock.cache.Cache
import kinesis.mock.instances.http4s.*
import kinesis.mock.models.AwsRegion

class KinesisMockRoutes(
    cache: Cache,
    logLevel: ConsoleLogger.LogLevel = ConsoleLogger.LogLevel.Error
):
  val logger: SelfAwareStructuredLogger[IO] =
    new ConsoleLogger(logLevel, this.getClass().getName())

  import KinesisMockMediaTypes.*
  import KinesisMockQueryParams.*
  import KinesisMockRoutes.*
  // check headers / query params (see what kinesalite does)
  // create a sharded stream cache
  // create service that has methods for each action
  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "healthcheck" => Ok()

    case request @ POST -> Root :?
        AmazonAuthAlgorithm(queryAuthAlgorith) :?
        AmazonAuthCredential(queryAuthCredential) :?
        AmazonAuthSignature(queryAuthSignature) :?
        AmazonAuthSignedHeaders(queryAuthSignedHeaders) :?
        AmazonDate(queryAmazonDate) :?
        Action(queryAction) =>
      LoggingContext.create.flatMap(initLc =>
        logger.debug(initLc.context)("Received POST request") *>
          logger.trace((initLc ++ request.headers.headers.map { h =>
            h.name.toString -> h.value
          }).context)(
            "Logging input headers"
          ) *>
          logger.trace(
            (initLc ++
              queryAuthAlgorith.map(x => amazonAuthAlgorithm -> x).toVector ++
              queryAuthCredential
                .map(x => amazonAuthCredential -> x)
                .toVector ++
              queryAuthSignature.map(x => amazonAuthSignature -> x).toVector ++
              queryAuthSignedHeaders
                .map(x => amazonAuthSignedHeaders -> x)
                .toVector ++
              queryAmazonDate.map(x => amazonDateQuery -> x).toVector ++
              queryAction.map(x => amazonAction -> x.entryName).toVector).context
          )(
            "Logging input query params"
          ) *> UUIDGen.randomUUID[IO].flatMap { requestId =>
            val requestIdHeader = AmazonRequestId(requestId)

            val amazonId2Header: Vector[Header.ToRaw] = request.headers
              .get[Origin]
              .fold[Vector[Header.ToRaw]] {
                val bytes = new Array[Byte](72)
                Random.nextBytes(bytes)
                Vector(
                  AmazonId2(
                    new String(Base64.getEncoder.encode(bytes), "UTF-8")
                  ).toRaw1
                )
              }(_ => Vector.empty)

            val accessControlHeaders: Vector[Header.ToRaw] = request.headers
              .get[Origin]
              .fold(Vector.empty[Header.ToRaw])(_ =>
                Vector(
                  AccessControlAllowOrigin("*").toRaw1,
                  AccessControlExposeHeaders(
                    "x-amzn-RequestId,x-amzn-ErrorType,x-amz-request-id,x-amz-id-2,x-amzn-ErrorMessage,Date"
                  ).toRaw1
                )
              )

            val responseHeaders: Vector[Header.ToRaw] =
              amazonId2Header ++ accessControlHeaders :+ requestIdHeader

            val authorizationHeader =
              request.headers.get[AmazonAuthorization]

            val action: Option[KinesisAction] = queryAction.orElse {
              request.headers.get[AmazonTarget].flatMap { h =>
                val split =
                  Try(h.value.split("\\.").toVector).toOption.toVector.flatten
                (split.headOption, split.get(1L)) match
                  case (Some(service), Some(act))
                      if service == "Kinesis_20131202" =>
                    KinesisAction.withNameOption(act)
                  case _ => None
              }
            }

            val lcWithHeaders =
              initLc ++ responseHeaders.flatMap(tr =>
                tr.values.toVector.map(h => h.name.toString -> h.value)
              ) ++ action
                .map(x => "action" -> x.entryName)
                .toVector

            logger.debug(lcWithHeaders.context)("Assembled headers") *> {

              (
                request.contentType,
                authorizationHeader,
                queryAuthAlgorith
              ) match
                case (None, _, _) =>
                  logger.warn(lcWithHeaders.context)(
                    "No contentType was provided"
                  ) *>
                    NotFound(
                      errorMessage("UnknownOperationException", None),
                      responseHeaders*
                    )
                case (Some(contentType), _, _)
                    if !validContentTypes.contains(contentType.mediaType) =>
                  logger.warn(
                    lcWithHeaders.context + ("contentType" -> contentType.value)
                  )(
                    s"Content type '${contentType.value}' is invalid for this request"
                  ) *>
                    NotFound(
                      errorMessage("UnknownOperationException", None),
                      responseHeaders*
                    )
                case (Some(contentType), Some(_), Some(_)) =>
                  implicit def entityEncoder[A: Encoder]: EntityEncoder[IO, A] =
                    kinesisMockEntityEncoder(contentType.mediaType)
                  logger.warn(
                    lcWithHeaders.context + ("contentType" -> contentType.value)
                  )(
                    s"Both authorization header and query-strings were provided with the request"
                  ) *>
                    BadRequest(
                      ErrorResponse(
                        "InvalidSignatureException",
                        "Found both \'X-Amz-Algorithm\' as a query-string param and \'Authorization\' as HTTP header."
                      ),
                      responseHeaders*
                    )
                case (Some(contentType), None, None) =>
                  implicit def entityEncoder[A: Encoder]: EntityEncoder[IO, A] =
                    kinesisMockEntityEncoder(contentType.mediaType)
                  logger.warn(
                    lcWithHeaders.context + ("contentType" -> contentType.value)
                  )(
                    s"Neither authorization header nor authorization query-strings were provided with the request"
                  ) *>
                    BadRequest(
                      ErrorResponse(
                        "MissingAuthenticationTokenException",
                        "Missing Authentication Token"
                      ),
                      responseHeaders*
                    )
                case (Some(contentType), Some(authHeader), _) =>
                  implicit def entityEncoder[A: Encoder]: EntityEncoder[IO, A] =
                    kinesisMockEntityEncoder(contentType.mediaType)
                  val lcWithContentType =
                    lcWithHeaders + ("contentType" -> contentType.value)
                  logger.debug(lcWithContentType.context)(
                    "Parsing auth header"
                  ) *> {
                    /*
                  Authorization=AWS4-HMAC-SHA256 Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6
                     */
                    val authParsed = authHeader.value
                      .replace(",", ", ")
                      .split(" ")
                      .toVector
                      .map(_.replace(",", ""))
                      .filter(_.nonEmpty)
                      .map { x =>
                        val keyVal = x.trim().split("=").toVector
                        keyVal.headOption -> keyVal.get(1L)
                      }
                      .flatMap {
                        case (Some(k), Some(v)) => Vector(k -> v)
                        case _                  => Vector.empty
                      }
                      .toMap
                    val expectedAuthKeys =
                      Vector("Credential", "Signature", "SignedHeaders")

                    val missingKeys = expectedAuthKeys
                      .diff(
                        authParsed.keys.toVector
                          .filter(expectedAuthKeys.contains)
                      )
                    val missingKeysMsg: Option[String] =
                      missingKeys.foldLeft(none[String]) { case (msg, k) =>
                        val newMsg =
                          s"Authorization header requires \\$k\\ parameter."
                        msg.fold(Some(newMsg))(str => Some(s"$str $newMsg"))
                      }

                    val missingDateMsg =
                      if request.headers
                          .get[AmazonDateHeader]
                          .isEmpty && request.headers.get[Date].isEmpty
                      then
                        Some(
                          "Authorization header requires existence of either a \\'X-Amz-Date\\' or a \\'Date\\' header."
                        )
                      else None

                    val authErrMsg = (missingKeysMsg, missingDateMsg) match
                      case (Some(x), Some(y)) => Some(s"$x $y")
                      case (Some(x), _)       => Some(x)
                      case (_, Some(y))       => Some(y)
                      case _                  => None

                    authErrMsg match
                      case Some(e) =>
                        val missingAuthContext: (String, String) = (
                          "missingAuthKeys",
                          (missingKeys ++ missingDateMsg
                            .fold(Vector.empty[String])(_ =>
                              Vector(
                                AmazonDateHeader.name.toString,
                                Date.headerInstance.name.toString
                              )
                            )).mkString(", ")
                        )

                        logger.warn(
                          (lcWithContentType + missingAuthContext).context
                        )(
                          "Some required information was not provied with the authorization header"
                        ) *>
                          BadRequest(
                            ErrorResponse("IncompleteSignatureException", e),
                            responseHeaders*
                          )
                      case None =>
                        action match
                          case Some(ac) =>
                            processAction(
                              request,
                              ac,
                              cache,
                              responseHeaders,
                              lcWithContentType,
                              contentType match
                                case ct
                                    if ct.mediaType == KinesisMockMediaTypes.amazonCbor =>
                                  true
                                case _ => false
                              ,
                              Try(
                                authParsed("Credential").split("/")(2)
                              ).toOption.flatMap(AwsRegion.withNameOption)
                            )
                          case None =>
                            logger.warn(lcWithContentType.context)(
                              "No Action could be parsed from the request"
                            ) *>
                              BadRequest(
                                ErrorResponse(
                                  "AccessDeniedException",
                                  "Unable to determine service/operation name to be authorized"
                                ),
                                responseHeaders*
                              )
                  }
                case (Some(contentType), _, Some(_)) =>
                  implicit def entityEncoder[A: Encoder]: EntityEncoder[IO, A] =
                    kinesisMockEntityEncoder(contentType.mediaType)

                  val lcWithContentType =
                    lcWithHeaders + ("contentType" -> contentType.value)
                  logger
                    .debug(lcWithContentType.context)(
                      "Parsing auth query parameters"
                    ) *> {

                    val missing = Vector(
                      queryAuthSignature.fold[Option[String]](
                        Some(
                          s"AWS query-string parameters must include \\$amazonAuthSignature\\."
                        )
                      )(_ => None),
                      queryAuthCredential.fold[Option[String]](
                        Some(
                          s"AWS query-string parameters must include \\$amazonAuthCredential\\."
                        )
                      )(_ => None),
                      queryAuthSignedHeaders.fold[Option[String]](
                        Some(
                          s"AWS query-string parameters must include \\$amazonAuthSignedHeaders\\."
                        )
                      )(_ => None),
                      queryAmazonDate.fold[Option[String]](
                        Some(
                          s"AWS query-string parameters must include \\$amazonDateQuery\\."
                        )
                      )(_ => None)
                    ).flatMap {
                      case Some(msg) => Vector(msg)
                      case None      => Vector.empty
                    }

                    if missing.nonEmpty then
                      val missingAuthContext: (String, String) =
                        (
                          "missingAuthKeys",
                          Vector(
                            queryAuthSignature.as(amazonAuthSignature),
                            queryAuthCredential.as(amazonAuthCredential),
                            queryAuthSignature.as(amazonAuthSignature),
                            queryAmazonDate.as(amazonDateQuery)
                          ).flatMap(_.toVector).mkString(", ")
                        )
                      logger
                        .warn(
                          (lcWithContentType + missingAuthContext).context
                        )(
                          "Some required information was not provied with the authorization query parameters"
                        ) *> BadRequest(
                        ErrorResponse(
                          "IncompleteSignatureException",
                          s"${missing.mkString(" ")} Re-examine the query-string parameters."
                        ),
                        responseHeaders*
                      )
                    else
                      action match
                        case Some(ac) =>
                          processAction(
                            request,
                            ac,
                            cache,
                            responseHeaders,
                            lcWithContentType,
                            contentType match
                              case ct
                                  if ct.mediaType == KinesisMockMediaTypes.amazonCbor =>
                                true
                              case _ => false
                            ,
                            queryAuthCredential.flatMap(x =>
                              Try(x.split("/")(2)).toOption
                                .flatMap(AwsRegion.withNameOption)
                            )
                          )
                        case None =>
                          logger.warn(lcWithContentType.context)(
                            "No Action could be parsed from the request"
                          ) *>
                            BadRequest(
                              ErrorResponse(
                                "AccessDeniedException",
                                "Unable to determine service/operation name to be authorized"
                              ),
                              responseHeaders*
                            )
                  }
            }

          }
      )

    case request @ OPTIONS -> Root =>
      IO.both(UUIDGen.randomUUID[IO], LoggingContext.create).flatMap {
        case (requestId, lc) =>
          val requestIdHeader = AmazonRequestId(requestId).toRaw1

          val initContext =
            lc + ("requestId" -> requestIdHeader.value)
          logger.debug(initContext.context)("Received OPTIONS request") *> {
            if request.headers.get[Origin].isEmpty then
              logger.warn(initContext.context)(
                "Missing required origin header for OPTIONS call"
              ) *>
                BadRequest(
                  errorMessage(
                    "AccessDeniedException",
                    Some(
                      "Unable to determine service/authorization name to be authorized"
                    )
                  ),
                  requestIdHeader
                )
            else
              val responseHeaders: Vector[Header.ToRaw] =
                request.headers
                  .get[Origin]
                  .fold[Vector[Header.ToRaw]](Vector.empty)(_ =>
                    Vector[Header.ToRaw](
                      AccessControlAllowOrigin("*").toRaw1,
                      AccessControlExposeHeaders(
                        "x-amzn-RequestId,x-amzn-ErrorType,x-amz-request-id,x-amz-id-2,x-amzn-ErrorMessage,Date"
                      ).toRaw1,
                      AccessControlMaxAge("172800").toRaw1
                    ) ++ request.headers
                      .get[AccessControlRequestHeaders]
                      .map[Header.ToRaw](h =>
                        AccessControlAllowHeaders(h.value).toRaw1
                      )
                      .toVector ++ request.headers
                      .get[AccessControlRequestMethod]
                      .map[Header.ToRaw](h =>
                        AccessControlAllowMethods(h.value).toRaw1
                      )
                      .toVector
                  ) :+ requestIdHeader

              logger.debug(
                (initContext ++ responseHeaders.flatMap(tr =>
                  tr.values.map(h => h.name.toString -> h.value)
                )).context
              )("Successfully processed OPTIONS call")

              Ok("", responseHeaders*)
          }
      }
  }

object KinesisMockRoutes:
  def errorMessage(`type`: String, message: Option[String]): String =
    message match
      case Some(msg) => s"<${`type`}>\n <Message>$msg</Message>\n</${`type`}>\n"
      case None      => s"<${`type`}/>\n"

  def handleDecodeError(
      err: DecodeFailure,
      responseHeaders: Vector[Header.ToRaw]
  )(using
      entityEncoder: EntityEncoder[IO, ErrorResponse]
  ): IO[HResponse[IO]] =
    BadRequest(
      ErrorResponse("SerializationException", err.getMessage),
      responseHeaders*
    )

  def handleKinesisMockError(
      err: KinesisMockException,
      responseHeaders: Vector[Header.ToRaw]
  )(using
      entityEncoder: EntityEncoder[IO, ErrorResponse]
  ): IO[HResponse[IO]] =
    BadRequest(
      ErrorResponse(err.getClass.getSimpleName, err.getMessage),
      responseHeaders*
    )

  def processAction(
      request: Request[IO],
      action: KinesisAction,
      cache: Cache,
      responseHeaders: Vector[Header.ToRaw],
      loggingContext: LoggingContext,
      isCbor: Boolean,
      region: Option[AwsRegion]
  )(using
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
      registerConsumerEE: EntityEncoder[IO, RegisterStreamConsumerResponse],
      updateShardCountEE: EntityEncoder[IO, UpdateShardCountResponse]
  ): IO[HResponse[IO]] =
    action match
      case KinesisAction.AddTagsToStream =>
        request
          .attemptAs[AddTagsToStreamRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .addTagsToStream(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
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
                .createStream(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
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
                .decreaseStreamRetention(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
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
                .deleteStream(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
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
                .deregisterStreamConsumer(req, loggingContext, isCbor)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
                  )
                )
          )
      case KinesisAction.DescribeLimits =>
        request.as[Unit] *>
          cache
            .describeLimits(loggingContext, region)
            .flatMap(
              _.fold(
                err => handleKinesisMockError(err, responseHeaders),
                res => Ok(res, responseHeaders*)
              )
            )
      case KinesisAction.DescribeStream =>
        request
          .attemptAs[DescribeStreamRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .describeStream(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .describeStreamConsumer(req, loggingContext, isCbor)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .describeStreamSummary(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .disableEnhancedMonitoring(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .enableEnhancedMonitoring(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .getRecords(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .getShardIterator(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .increaseStreamRetention(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
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
                .listShards(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .listStreamConsumers(req, loggingContext, isCbor)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .listStreams(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .listTagsForStream(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .mergeShards(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
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
                .putRecord(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .putRecords(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .registerStreamConsumer(req, loggingContext, isCbor)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
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
                .removeTagsFromStream(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
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
                .splitShard(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
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
                .startStreamEncryption(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
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
                .stopStreamEncryption(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
                  )
                )
          )
      case KinesisAction.SubscribeToShard =>
        NotFound(
          ErrorResponse(
            "ApiNotImplemented",
            "SubscribeToShard is not yet supported"
          ),
          responseHeaders*
        )
      case KinesisAction.UpdateShardCount =>
        request
          .attemptAs[UpdateShardCountRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .updateShardCount(req, loggingContext, isCbor, region)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    res => Ok(res, responseHeaders*)
                  )
                )
          )
      case KinesisAction.UpdateStreamMode =>
        request
          .attemptAs[UpdateStreamModeRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .updateStreamMode(req, loggingContext, isCbor)
                .flatMap(
                  _.fold(
                    err => handleKinesisMockError(err, responseHeaders),
                    _ => Ok("", responseHeaders*)
                  )
                )
          )
