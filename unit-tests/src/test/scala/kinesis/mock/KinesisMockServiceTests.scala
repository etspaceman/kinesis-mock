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

import cats.effect.IO
import org.http4s._
import org.http4s.headers._
import org.http4s.syntax.all._
import org.scalacheck.effect.PropF

import kinesis.mock.api.CreateStreamRequest
import kinesis.mock.cache.{Cache, CacheConfig}
import kinesis.mock.instances.arbitrary._
import kinesis.mock.instances.http4s._
import kinesis.mock.models.StreamName

class KinesisMockServiceTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  test("it should accept healthcheck requests") {
    CacheConfig.read
      .resource[IO]
      .flatMap(Cache(_))
      .use { cache =>
        val app = new KinesisMockRoutes(cache).routes.orNotFound
        val request = Request[IO](
          method = Method.GET,
          uri = Uri(path = path"/healthcheck")
        )
        for {
          res <- app.run(request)
        } yield assert(res.status.isSuccess, res)
      }

  }

  test("it should accept valid OPTIONS requests") {
    CacheConfig.read
      .resource[IO]
      .flatMap(Cache(_))
      .use { cache =>
        val app = new KinesisMockRoutes(cache).routes.orNotFound
        val origin: Origin = Origin.Null
        val request = Request[IO](
          method = Method.OPTIONS,
          headers = Headers.empty.put(origin.toRaw1)
        )
        for {
          res <- app.run(request)
        } yield assert(res.status.isSuccess, res)
      }
  }

  test("it should reject OPTIONS requests without an Origin header") {
    CacheConfig.read
      .resource[IO]
      .flatMap(Cache(_))
      .use { cache =>
        val app = new KinesisMockRoutes(cache).routes.orNotFound
        val request = Request[IO](
          method = Method.OPTIONS
        )
        for {
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, res)
      }
  }

  test("it should accept valid CBOR requests using headers") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                origin.toRaw1,
                AmazonAuthorization(
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ).toRaw1,
                AmazonDateHeader("20150830T123600Z").toRaw1,
                AmazonTarget("Kinesis_20131202.CreateStream").toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(res.status.isSuccess, res)
        }
    }
  }

  test("it should accept auth headers without spaces between commas") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                origin.toRaw1,
                AmazonAuthorization(
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request," +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target," +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ).toRaw1,
                AmazonDateHeader("20150830T123600Z").toRaw1,
                AmazonTarget("Kinesis_20131202.CreateStream").toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(res.status.isSuccess, res)

        }
    }
  }

  test("it should accept valid JSON requests using headers") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                origin.toRaw1,
                AmazonAuthorization(
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ).toRaw1,
                AmazonDateHeader("20150830T123600Z").toRaw1,
                AmazonTarget("Kinesis_20131202.CreateStream").toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonJson).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonJson
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(res.status.isSuccess, res)

        }
    }
  }

  test("it should accept valid CBOR requests using query params") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            uri = Uri(
              path = path"/",
              query = Query(
                KinesisMockQueryParams.amazonAction -> Some("CreateStream"),
                KinesisMockQueryParams.amazonAuthAlgorithm -> Some(
                  "AWS4-HMAC-SHA256"
                ),
                KinesisMockQueryParams.amazonAuthCredential -> Some(
                  "mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                ),
                KinesisMockQueryParams.amazonAuthSignedHeaders -> Some(
                  "amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target"
                ),
                KinesisMockQueryParams.amazonAuthSignature -> Some(
                  "4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ),
                KinesisMockQueryParams.amazonDateQuery -> Some(
                  "20150830T123600Z"
                )
              )
            ),
            headers = Headers(
              List(
                origin.toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(res.status.isSuccess, request.uri)
        }
    }
  }

  test("it should accept valid JSON requests using query params") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            uri = Uri(
              path = path"/",
              query = Query(
                KinesisMockQueryParams.amazonAction -> Some("CreateStream"),
                KinesisMockQueryParams.amazonAuthAlgorithm -> Some(
                  "AWS4-HMAC-SHA256"
                ),
                KinesisMockQueryParams.amazonAuthCredential -> Some(
                  "mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                ),
                KinesisMockQueryParams.amazonAuthSignedHeaders -> Some(
                  "amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target"
                ),
                KinesisMockQueryParams.amazonAuthSignature -> Some(
                  "4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ),
                KinesisMockQueryParams.amazonDateQuery -> Some(
                  "20150830T123600Z"
                )
              )
            ),
            headers = Headers(
              List(
                origin.toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonJson).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonJson
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(res.status.isSuccess, request.uri)
        }
    }
  }

  test("it should return amazonId2 header if no Origin is supplied") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                AmazonAuthorization(
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ).toRaw1,
                AmazonDateHeader("20150830T123600Z").toRaw1,
                AmazonTarget("Kinesis_20131202.CreateStream").toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(
            res.status.isSuccess && res.headers.get[AmazonId2].nonEmpty,
            res
          )
        }
    }
  }

  test("it should reject if no authorization is found") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                origin.toRaw1,
                AmazonDateHeader("20150830T123600Z").toRaw1,
                AmazonTarget("Kinesis_20131202.CreateStream").toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(!res.status.isSuccess, res)
        }
    }
  }

  test("it should reject if no date is found") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                origin.toRaw1,
                AmazonAuthorization(
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ).toRaw1,
                AmazonTarget("Kinesis_20131202.CreateStream").toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(!res.status.isSuccess, res)
        }
    }
  }

  test("it should reject if some auth headers aren't found") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                origin.toRaw1,
                AmazonAuthorization(
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                ).toRaw1,
                AmazonDateHeader("20150830T123600Z").toRaw1,
                AmazonTarget("Kinesis_20131202.CreateStream").toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(!res.status.isSuccess, res)
        }
    }
  }

  test("it should reject if some auth headers and date aren't found") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                origin.toRaw1,
                AmazonAuthorization(
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                ).toRaw1,
                AmazonTarget("Kinesis_20131202.CreateStream").toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(!res.status.isSuccess, res)
        }
    }
  }

  test("it reject if some auth query params aren't found") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            uri = Uri(
              path = path"/",
              query = Query(
                KinesisMockQueryParams.amazonAction -> Some("CreateStream"),
                KinesisMockQueryParams.amazonAuthAlgorithm -> Some(
                  "AWS4-HMAC-SHA256"
                ),
                KinesisMockQueryParams.amazonAuthCredential -> Some(
                  "mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                ),
                KinesisMockQueryParams.amazonDateQuery -> Some(
                  "20150830T123600Z"
                )
              )
            ),
            headers = Headers(
              List(
                origin.toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(!res.status.isSuccess, request)
        }
    }
  }

  test("it reject if some auth query params and date aren't found") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            uri = Uri(
              path = path"/",
              query = Query(
                KinesisMockQueryParams.amazonAction -> Some("CreateStream"),
                KinesisMockQueryParams.amazonAuthAlgorithm -> Some(
                  "AWS4-HMAC-SHA256"
                ),
                KinesisMockQueryParams.amazonAuthCredential -> Some(
                  "mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                )
              )
            ),
            headers = Headers(
              List(
                origin.toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(!res.status.isSuccess, request)
        }
    }
  }

  test("it should reject if no action is found") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                origin.toRaw1,
                AmazonAuthorization(
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ).toRaw1,
                AmazonDateHeader("20150830T123600Z").toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(!res.status.isSuccess, res)
        }
    }
  }

  test("it should reject if action is malformed") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                origin.toRaw1,
                AmazonAuthorization(
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ).toRaw1,
                AmazonDateHeader("20150830T123600Z").toRaw1,
                AmazonTarget("thisisntright").toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(!res.status.isSuccess, res)
        }
    }
  }

  test("it should reject if content-type is not provided") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                origin.toRaw1,
                AmazonAuthorization(
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ).toRaw1,
                AmazonDateHeader("20150830T123600Z").toRaw1,
                AmazonTarget("Kinesis_20131202.CreateStream").toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(!res.status.isSuccess, res)
        }
    }
  }

  test("it should reject if both auth headers and query strings are provided") {
    PropF.forAllF { (streamName: StreamName) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(Cache(_))
        .use { cache =>
          val app = new KinesisMockRoutes(cache).routes.orNotFound
          val origin: Origin = Origin.Null
          val request = Request(
            method = Method.POST,
            uri = Uri(
              path = path"/",
              query = Query(
                KinesisMockQueryParams.amazonAction -> Some("CreateStream"),
                KinesisMockQueryParams.amazonAuthAlgorithm -> Some(
                  "AWS4-HMAC-SHA256"
                ),
                KinesisMockQueryParams.amazonAuthCredential -> Some(
                  "mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                ),
                KinesisMockQueryParams.amazonAuthSignedHeaders -> Some(
                  "amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target"
                ),
                KinesisMockQueryParams.amazonAuthSignature -> Some(
                  "4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ),
                KinesisMockQueryParams.amazonDateQuery -> Some(
                  "20150830T123600Z"
                )
              )
            ),
            headers = Headers(
              List(
                origin.toRaw1,
                AmazonAuthorization(
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ).toRaw1,
                AmazonDateHeader("20150830T123600Z").toRaw1,
                AmazonTarget("Kinesis_20131202.CreateStream").toRaw1,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(Some(1), None, streamName)).body
          )
          for {
            res <- app.run(request)
          } yield assert(!res.status.isSuccess, res)
        }
    }
  }

  test("it should reject requests with invalid bodies") {
    CacheConfig.read
      .resource[IO]
      .flatMap(Cache(_))
      .use { cache =>
        val app = new KinesisMockRoutes(cache).routes.orNotFound
        val origin: Origin = Origin.Null
        val request = Request(
          method = Method.POST,
          headers = Headers(
            List(
              origin.toRaw1,
              AmazonAuthorization(
                "AWS4-HMAC-SHA256 " +
                  "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                  "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                  "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
              ).toRaw1,
              AmazonDateHeader("20150830T123600Z").toRaw1,
              AmazonTarget("Kinesis_20131202.CreateStream").toRaw1,
              `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
            )
          ),
          body = EntityEncoder[IO, String].toEntity("thisisn'tright").body
        )
        for {
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, res)
      }
  }

  test("it should reject requests with invalid API requests") {
    CacheConfig.read
      .resource[IO]
      .flatMap(Cache(_))
      .use { cache =>
        val app = new KinesisMockRoutes(cache).routes.orNotFound
        val origin: Origin = Origin.Null
        val request = Request(
          method = Method.POST,
          headers = Headers(
            List(
              origin.toRaw1,
              AmazonAuthorization(
                "AWS4-HMAC-SHA256 " +
                  "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                  "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                  "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
              ).toRaw1,
              AmazonDateHeader("20150830T123600Z").toRaw1,
              AmazonTarget("Kinesis_20131202.CreateStream").toRaw1,
              `Content-Type`(KinesisMockMediaTypes.amazonCbor).toRaw1
            )
          ),
          body = kinesisMockEntityEncoder[CreateStreamRequest](
            KinesisMockMediaTypes.amazonCbor
          ).toEntity(CreateStreamRequest(Some(1), None, StreamName(""))).body
        )
        for {
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, res)
      }
  }

}
