/*
 * Copyright 2021-2026 io.github.etspaceman
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
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Response as HResponse
import org.http4s.dsl.io.*
import org.http4s.headers.*
import org.http4s.syntax.header.*
import org.typelevel.log4cats.SelfAwareStructuredLogger

import kinesis.mock.api.*
import kinesis.mock.cache.Cache
import kinesis.mock.instances.http4s.{given, *}
import kinesis.mock.models.{AwsAccountId, AwsRegion}

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
      for
        initLc <- LoggingContext.create
        _ <- logger.debug(initLc.context)("Received POST request")
        _ <- logger.trace(
          (initLc ++ request.headers.headers.map { h =>
            h.name.toString -> h.value
          }).context
        )("Logging input headers")
        _ <- logger.trace(
          (initLc ++
            queryAuthAlgorith.map(amazonAuthAlgorithm -> _).toVector ++
            queryAuthCredential.map(amazonAuthCredential -> _).toVector ++
            queryAuthSignature.map(amazonAuthSignature -> _).toVector ++
            queryAuthSignedHeaders.map(amazonAuthSignedHeaders -> _).toVector ++
            queryAmazonDate.map(amazonDateQuery -> _).toVector ++
            queryAction.map(amazonAction -> _.entryName).toVector).context
        )("Logging input query params")
        requestId <- UUIDGen.randomUUID[IO]
        requestIdHeader = AmazonRequestId(requestId)
        responseHeaders = buildPostResponseHeaders(request, requestIdHeader)
        action = resolveAction(request, queryAction)
        lcWithHeaders =
          initLc ++ headerContext(responseHeaders) ++
            action.map("action" -> _.entryName).toVector
        _ <- logger.debug(lcWithHeaders.context)("Assembled headers")
        response <- request.contentType match
          case None =>
            for
              _ <- logger.warn(lcWithHeaders.context)(
                "No contentType was provided"
              )
              resp <- NotFound(
                errorMessage("UnknownOperationException", None),
                responseHeaders*
              )
            yield resp
          case Some(contentType)
              if !validContentTypes.contains(contentType.mediaType) =>
            for
              _ <- logger.warn(
                lcWithHeaders.context + ("contentType" -> contentType.value)
              )(
                s"Content type '${contentType.value}' is invalid for this request"
              )
              resp <- NotFound(
                errorMessage("UnknownOperationException", None),
                responseHeaders*
              )
            yield resp
          case Some(contentType) =>
            given entityEncoder[A](using Encoder[A]): EntityEncoder[IO, A] =
              kinesisMockEntityEncoder(contentType.mediaType)
            val lcWithContentType =
              lcWithHeaders + ("contentType" -> contentType.value)

            (request.headers.get[AmazonAuthorization], queryAuthAlgorith) match
              case (Some(_), Some(_)) =>
                for
                  _ <- logger.warn(lcWithContentType.context)(
                    "Both authorization header and query-strings were provided with the request"
                  )
                  resp <- BadRequest(
                    ErrorResponse(
                      "InvalidSignatureException",
                      "Found both 'X-Amz-Algorithm' as a query-string param and 'Authorization' as HTTP header."
                    ),
                    responseHeaders*
                  )
                yield resp
              case (None, None) =>
                for
                  _ <- logger.warn(lcWithContentType.context)(
                    "Neither authorization header nor authorization query-strings were provided with the request"
                  )
                  resp <- BadRequest(
                    ErrorResponse(
                      "MissingAuthenticationTokenException",
                      "Missing Authentication Token"
                    ),
                    responseHeaders*
                  )
                yield resp
              case (Some(authHeader), _) =>
                processAuthorizationHeader(
                  request,
                  authHeader,
                  action,
                  responseHeaders,
                  contentType,
                  lcWithContentType
                )
              case (_, Some(_)) =>
                processAuthorizationQueryParams(
                  request,
                  queryAuthCredential,
                  queryAuthSignature,
                  queryAuthSignedHeaders,
                  queryAmazonDate,
                  action,
                  responseHeaders,
                  contentType,
                  lcWithContentType
                )
      yield response

    case request @ OPTIONS -> Root =>
      for
        requestIdAndLc <- IO.both(
          UUIDGen.randomUUID[IO],
          LoggingContext.create
        )
        (requestId, lc) = requestIdAndLc
        requestIdHeader = AmazonRequestId(requestId).toRaw1
        initContext = lc + ("requestId" -> requestIdHeader.value)
        _ <- logger.debug(initContext.context)("Received OPTIONS request")
        response <-
          if request.headers.get[Origin].isEmpty then
            for
              _ <- logger.warn(initContext.context)(
                "Missing required origin header for OPTIONS call"
              )
              resp <- BadRequest(
                errorMessage(
                  "AccessDeniedException",
                  Some(
                    "Unable to determine service/authorization name to be authorized"
                  )
                ),
                requestIdHeader
              )
            yield resp
          else
            val responseHeaders: Vector[Header.ToRaw] =
              Vector[Header.ToRaw](
                AccessControlAllowOrigin("*").toRaw1,
                AccessControlExposeHeaders(exposedHeaders).toRaw1,
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
                .toVector :+ requestIdHeader

            for
              _ <- logger.debug(
                (initContext ++ headerContext(responseHeaders)).context
              )("Successfully processed OPTIONS call")
              resp <- emptyOk(
                responseHeaders,
                request.contentType
                  .getOrElse(`Content-Type`(KinesisMockMediaTypes.amazonJson))
              )
            yield resp
      yield response
  }

  /** Builds the standard response headers for a POST request: an `x-amz-id-2`
    * (only for non-CORS requests), the CORS headers (only when an `Origin` is
    * present), and the request id.
    */
  private def buildPostResponseHeaders(
      request: Request[IO],
      requestIdHeader: AmazonRequestId
  ): Vector[Header.ToRaw] =
    val amazonId2Header: Vector[Header.ToRaw] = request.headers
      .get[Origin]
      .fold[Vector[Header.ToRaw]] {
        val bytes = new Array[Byte](72)
        Random.nextBytes(bytes)
        Vector(
          AmazonId2(new String(Base64.getEncoder.encode(bytes), "UTF-8")).toRaw1
        )
      }(_ => Vector.empty)

    val accessControlHeaders: Vector[Header.ToRaw] = request.headers
      .get[Origin]
      .fold(Vector.empty[Header.ToRaw])(_ =>
        Vector(
          AccessControlAllowOrigin("*").toRaw1,
          AccessControlExposeHeaders(exposedHeaders).toRaw1
        )
      )

    amazonId2Header ++ accessControlHeaders :+ requestIdHeader

  /** Resolves the requested [[KinesisAction]] from the query-string `Action`
    * param, falling back to the `X-Amz-Target` header.
    */
  private def resolveAction(
      request: Request[IO],
      queryAction: Option[KinesisAction]
  ): Option[KinesisAction] =
    queryAction.orElse {
      request.headers.get[AmazonTarget].flatMap { h =>
        val split =
          Try(h.value.split("\\.").toVector).toOption.toVector.flatten
        (split.headOption, split.get(1L)) match
          case (Some(service), Some(act)) if service == "Kinesis_20131202" =>
            KinesisAction.withNameOption(act)
          case _ => None
      }
    }

  /** Validates the SigV4 `Authorization` header and, when valid, dispatches the
    * request to [[KinesisMockRoutes.processAction]].
    */
  private def processAuthorizationHeader(
      request: Request[IO],
      authHeader: AmazonAuthorization,
      action: Option[KinesisAction],
      responseHeaders: Vector[Header.ToRaw],
      contentType: `Content-Type`,
      lcWithContentType: LoggingContext
  ): IO[HResponse[IO]] =
    given entityEncoder[A](using Encoder[A]): EntityEncoder[IO, A] =
      kinesisMockEntityEncoder(contentType.mediaType)

    /*
    Authorization=AWS4-HMAC-SHA256 Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6
     */
    val authParsed = parseAuthHeader(authHeader.value)
    val expectedAuthKeys =
      Vector("Credential", "Signature", "SignedHeaders")

    val missingKeys = expectedAuthKeys
      .diff(authParsed.keys.toVector.filter(expectedAuthKeys.contains))
    val missingKeysMsg: Option[String] =
      missingKeys.foldLeft(none[String]) { case (msg, k) =>
        val newMsg = s"Authorization header requires \\$k\\ parameter."
        msg.fold(Some(newMsg))(str => Some(s"$str $newMsg"))
      }

    val missingDateMsg =
      if request.headers.get[AmazonDateHeader].isEmpty &&
        request.headers.get[Date].isEmpty
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

    for
      _ <- logger.debug(lcWithContentType.context)("Parsing auth header")
      response <- authErrMsg match
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

          for
            _ <- logger.warn((lcWithContentType + missingAuthContext).context)(
              "Some required information was not provied with the authorization header"
            )
            resp <- BadRequest(
              ErrorResponse("IncompleteSignatureException", e),
              responseHeaders*
            )
          yield resp
        case None =>
          action match
            case Some(ac) =>
              processAction(
                request,
                ac,
                cache,
                responseHeaders,
                lcWithContentType,
                contentType,
                authParsed.get("Credential").flatMap(resolveRegion),
                resolveAccountId(authParsed.getOrElse("Credential", ""))
              )
            case None => noActionResponse(responseHeaders, lcWithContentType)
    yield response

  /** Validates the SigV4 authorization query-string params and, when valid,
    * dispatches the request to [[KinesisMockRoutes.processAction]].
    */
  private def processAuthorizationQueryParams(
      request: Request[IO],
      queryAuthCredential: Option[String],
      queryAuthSignature: Option[String],
      queryAuthSignedHeaders: Option[String],
      queryAmazonDate: Option[String],
      action: Option[KinesisAction],
      responseHeaders: Vector[Header.ToRaw],
      contentType: `Content-Type`,
      lcWithContentType: LoggingContext
  ): IO[HResponse[IO]] =
    given entityEncoder[A](using Encoder[A]): EntityEncoder[IO, A] =
      kinesisMockEntityEncoder(contentType.mediaType)

    val missing = Vector(
      queryAuthSignature.fold(
        Some(
          s"AWS query-string parameters must include \\$amazonAuthSignature\\."
        )
      )(_ => None),
      queryAuthCredential.fold(
        Some(
          s"AWS query-string parameters must include \\$amazonAuthCredential\\."
        )
      )(_ => None),
      queryAuthSignedHeaders.fold(
        Some(
          s"AWS query-string parameters must include \\$amazonAuthSignedHeaders\\."
        )
      )(_ => None),
      queryAmazonDate.fold(
        Some(
          s"AWS query-string parameters must include \\$amazonDateQuery\\."
        )
      )(_ => None)
    ).flatten

    for
      _ <- logger.debug(lcWithContentType.context)(
        "Parsing auth query parameters"
      )
      response <-
        if missing.nonEmpty then
          val missingAuthContext: (String, String) = (
            "missingAuthKeys",
            Vector(
              queryAuthSignature.as(amazonAuthSignature),
              queryAuthCredential.as(amazonAuthCredential),
              queryAuthSignedHeaders.as(amazonAuthSignedHeaders),
              queryAmazonDate.as(amazonDateQuery)
            ).flatten.mkString(", ")
          )
          for
            _ <- logger.warn((lcWithContentType + missingAuthContext).context)(
              "Some required information was not provied with the authorization query parameters"
            )
            resp <- BadRequest(
              ErrorResponse(
                "IncompleteSignatureException",
                s"${missing.mkString(" ")} Re-examine the query-string parameters."
              ),
              responseHeaders*
            )
          yield resp
        else
          action match
            case Some(ac) =>
              processAction(
                request,
                ac,
                cache,
                responseHeaders,
                lcWithContentType,
                contentType,
                queryAuthCredential.flatMap(resolveRegion),
                queryAuthCredential.flatMap(resolveAccountId)
              )
            case None => noActionResponse(responseHeaders, lcWithContentType)
    yield response

  private def noActionResponse(
      responseHeaders: Vector[Header.ToRaw],
      lcWithContentType: LoggingContext
  )(using EntityEncoder[IO, ErrorResponse]): IO[HResponse[IO]] =
    for
      _ <- logger.warn(lcWithContentType.context)(
        "No Action could be parsed from the request"
      )
      resp <- BadRequest(
        ErrorResponse(
          "AccessDeniedException",
          "Unable to determine service/operation name to be authorized"
        ),
        responseHeaders*
      )
    yield resp

object KinesisMockRoutes:
  /** The set of headers exposed to CORS clients on every Kinesis response. */
  val exposedHeaders: String =
    "x-amzn-RequestId,x-amzn-ErrorType,x-amz-request-id,x-amz-id-2,x-amzn-ErrorMessage,Date"

  /** Resolves the AWS account for a request from the access key id (the first
    * segment of the SigV4 `Credential`). A client selects an account by signing
    * with the 12-digit account id as its access key id; any other access key
    * (opaque, AWS-format, or malformed) yields `None`, so the caller falls back
    * to the configured `AWS_ACCOUNT_ID`.
    */
  def resolveAccountId(credential: String): Option[AwsAccountId] =
    Try(credential.split("/")(0)).toOption
      .filter(_.matches("\\d{12}"))
      .map(AwsAccountId(_))

  /** Resolves the region from the third segment of a SigV4 `Credential`
    * (`<akid>/<date>/<region>/<service>/aws4_request`).
    */
  def resolveRegion(credential: String): Option[AwsRegion] =
    Try(credential.split("/")(2)).toOption.flatMap(AwsRegion.withNameOption)

  /** Parses a SigV4 `Authorization` header value into its `key=value` pairs
    * (e.g. `Credential`, `SignedHeaders`, `Signature`).
    */
  def parseAuthHeader(value: String): Map[String, String] =
    value
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

  /** Flattens response headers into logging-context key/value pairs. */
  def headerContext(
      headers: Vector[Header.ToRaw]
  ): Vector[(String, String)] =
    headers.flatMap(_.values.toVector.map(h => h.name.toString -> h.value))

  def emptyOk(
      responseHeaders: Vector[Header.ToRaw],
      contentType: `Content-Type`
  ) =
    Ok("", responseHeaders).map(_.withContentType(contentType))

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
      contentType: `Content-Type`,
      region: Option[AwsRegion],
      accountId: Option[AwsAccountId]
  ): IO[HResponse[IO]] =
    val isCbor = contentType.mediaType == KinesisMockMediaTypes.amazonCbor

    given entityEncoder[A](using Encoder[A]): EntityEncoder[IO, A] =
      kinesisMockEntityEncoder(contentType.mediaType)

    /** Decodes the request body and runs an action whose success carries a JSON
      * response body.
      */
    def jsonAction[Req, Res](run: Req => IO[Response[Res]])(using
        EntityDecoder[IO, Req],
        EntityEncoder[IO, Res]
    ): IO[HResponse[IO]] =
      request
        .attemptAs[Req]
        .foldF(
          err => handleDecodeError(err, responseHeaders),
          req =>
            run(req).flatMap(
              _.fold(
                err => handleKinesisMockError(err, responseHeaders),
                res => Ok(res, responseHeaders*)
              )
            )
        )

    /** Decodes the request body and runs an action whose success has no
      * response body.
      */
    def emptyAction[Req](run: Req => IO[Response[?]])(using
        EntityDecoder[IO, Req]
    ): IO[HResponse[IO]] =
      request
        .attemptAs[Req]
        .foldF(
          err => handleDecodeError(err, responseHeaders),
          req =>
            run(req).flatMap(
              _.fold(
                err => handleKinesisMockError(err, responseHeaders),
                _ => emptyOk(responseHeaders, contentType)
              )
            )
        )

    action match
      case KinesisAction.AddTagsToStream =>
        emptyAction[AddTagsToStreamRequest](
          cache.addTagsToStream(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.CreateStream =>
        emptyAction[CreateStreamRequest](
          cache.createStream(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.DecreaseStreamRetentionPeriod =>
        emptyAction[DecreaseStreamRetentionPeriodRequest](
          cache.decreaseStreamRetention(
            _,
            loggingContext,
            isCbor,
            region,
            accountId
          )
        )
      case KinesisAction.DeleteStream =>
        emptyAction[DeleteStreamRequest](
          cache.deleteStream(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.DeregisterStreamConsumer =>
        emptyAction[DeregisterStreamConsumerRequest](
          cache.deregisterStreamConsumer(_, loggingContext, isCbor)
        )
      case KinesisAction.DescribeAccountSettings =>
        jsonAction[
          DescribeAccountSettingsRequest,
          DescribeAccountSettingsResponse
        ](
          cache.describeAccountSettings(_, loggingContext, isCbor, region)
        )
      case KinesisAction.DescribeLimits =>
        for
          _ <- request.as[Unit]
          response <- cache
            .describeLimits(loggingContext, region, accountId)
            .flatMap(
              _.fold(
                err => handleKinesisMockError(err, responseHeaders),
                res => Ok(res, responseHeaders*)
              )
            )
        yield response
      case KinesisAction.DescribeStream =>
        jsonAction[DescribeStreamRequest, DescribeStreamResponse](
          cache.describeStream(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.DescribeStreamConsumer =>
        jsonAction[
          DescribeStreamConsumerRequest,
          DescribeStreamConsumerResponse
        ](
          cache.describeStreamConsumer(_, loggingContext, isCbor)
        )
      case KinesisAction.DescribeStreamSummary =>
        jsonAction[
          DescribeStreamSummaryRequest,
          DescribeStreamSummaryResponse
        ](
          cache.describeStreamSummary(
            _,
            loggingContext,
            isCbor,
            region,
            accountId
          )
        )
      case KinesisAction.DisableEnhancedMonitoring =>
        jsonAction[
          DisableEnhancedMonitoringRequest,
          DisableEnhancedMonitoringResponse
        ](
          cache.disableEnhancedMonitoring(
            _,
            loggingContext,
            isCbor,
            region,
            accountId
          )
        )
      case KinesisAction.EnableEnhancedMonitoring =>
        jsonAction[
          EnableEnhancedMonitoringRequest,
          EnableEnhancedMonitoringResponse
        ](
          cache.enableEnhancedMonitoring(
            _,
            loggingContext,
            isCbor,
            region,
            accountId
          )
        )
      case KinesisAction.GetRecords =>
        jsonAction[GetRecordsRequest, GetRecordsResponse](
          cache.getRecords(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.GetShardIterator =>
        jsonAction[GetShardIteratorRequest, GetShardIteratorResponse](
          cache.getShardIterator(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.IncreaseStreamRetentionPeriod =>
        emptyAction[IncreaseStreamRetentionPeriodRequest](
          cache.increaseStreamRetention(
            _,
            loggingContext,
            isCbor,
            region,
            accountId
          )
        )
      case KinesisAction.ListShards =>
        jsonAction[ListShardsRequest, ListShardsResponse](
          cache.listShards(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.ListStreamConsumers =>
        jsonAction[ListStreamConsumersRequest, ListStreamConsumersResponse](
          cache.listStreamConsumers(_, loggingContext, isCbor)
        )
      case KinesisAction.ListStreams =>
        jsonAction[ListStreamsRequest, ListStreamsResponse](
          cache.listStreams(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.ListTagsForResource =>
        jsonAction[ListTagsForResourceRequest, ListTagsForResourceResponse](
          cache.listTagsForResource(_, loggingContext, isCbor, region)
        )
      case KinesisAction.ListTagsForStream =>
        jsonAction[ListTagsForStreamRequest, ListTagsForStreamResponse](
          cache.listTagsForStream(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.MergeShards =>
        emptyAction[MergeShardsRequest](
          cache.mergeShards(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.PutRecord =>
        jsonAction[PutRecordRequest, PutRecordResponse](
          cache.putRecord(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.PutRecords =>
        jsonAction[PutRecordsRequest, PutRecordsResponse](
          cache.putRecords(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.DeleteResourcePolicy =>
        emptyAction[DeleteResourcePolicyRequest](
          cache.deleteResourcePolicy(_, loggingContext, isCbor, region)
        )
      case KinesisAction.GetResourcePolicy =>
        jsonAction[GetResourcePolicyRequest, GetResourcePolicyResponse](
          cache.getResourcePolicy(_, loggingContext, isCbor, region)
        )
      case KinesisAction.PutResourcePolicy =>
        emptyAction[PutResourcePolicyRequest](
          cache.putResourcePolicy(_, loggingContext, isCbor, region)
        )
      case KinesisAction.RegisterStreamConsumer =>
        jsonAction[
          RegisterStreamConsumerRequest,
          RegisterStreamConsumerResponse
        ](
          cache.registerStreamConsumer(_, loggingContext, isCbor)
        )
      case KinesisAction.RemoveTagsFromStream =>
        emptyAction[RemoveTagsFromStreamRequest](
          cache.removeTagsFromStream(
            _,
            loggingContext,
            isCbor,
            region,
            accountId
          )
        )
      case KinesisAction.SplitShard =>
        emptyAction[SplitShardRequest](
          cache.splitShard(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.StartStreamEncryption =>
        emptyAction[StartStreamEncryptionRequest](
          cache.startStreamEncryption(
            _,
            loggingContext,
            isCbor,
            region,
            accountId
          )
        )
      case KinesisAction.StopStreamEncryption =>
        emptyAction[StopStreamEncryptionRequest](
          cache.stopStreamEncryption(
            _,
            loggingContext,
            isCbor,
            region,
            accountId
          )
        )
      case KinesisAction.SubscribeToShard =>
        request
          .attemptAs[SubscribeToShardRequest]
          .foldF(
            err => handleDecodeError(err, responseHeaders),
            req =>
              cache
                .subscribeToShard(req, loggingContext, isCbor, region)
                .map { byteStream =>
                  HResponse[IO](Ok)
                    .putHeaders(responseHeaders*)
                    .withContentType(
                      `Content-Type`(
                        org.http4s.MediaType
                          .unsafeParse("application/vnd.amazon.eventstream")
                      )
                    )
                    .withBodyStream(byteStream)
                }
          )
      case KinesisAction.TagResource =>
        emptyAction[TagResourceRequest](
          cache.tagResource(_, loggingContext, isCbor, region)
        )
      case KinesisAction.UntagResource =>
        emptyAction[UntagResourceRequest](
          cache.untagResource(_, loggingContext, isCbor, region)
        )
      case KinesisAction.UpdateAccountSettings =>
        emptyAction[UpdateAccountSettingsRequest](
          cache.updateAccountSettings(_, loggingContext, isCbor, region)
        )
      case KinesisAction.UpdateMaxRecordSize =>
        jsonAction[UpdateMaxRecordSizeRequest, UpdateMaxRecordSizeResponse](
          cache.updateMaxRecordSize(
            _,
            loggingContext,
            isCbor,
            region,
            accountId
          )
        )
      case KinesisAction.UpdateShardCount =>
        jsonAction[UpdateShardCountRequest, UpdateShardCountResponse](
          cache.updateShardCount(_, loggingContext, isCbor, region, accountId)
        )
      case KinesisAction.UpdateStreamMode =>
        emptyAction[UpdateStreamModeRequest](
          cache.updateStreamMode(_, loggingContext, isCbor)
        )
      case KinesisAction.UpdateStreamWarmThroughput =>
        jsonAction[
          UpdateStreamWarmThroughputRequest,
          UpdateStreamWarmThroughputResponse
        ](
          cache.updateStreamWarmThroughput(
            _,
            loggingContext,
            isCbor,
            region,
            accountId
          )
        )
