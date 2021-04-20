package kinesis.mock

import cats.effect.IO
import org.http4s._
import org.http4s.headers._
import org.http4s.syntax.kleisli._
import org.http4s.util.CaseInsensitiveString
import org.scalacheck.effect.PropF

import kinesis.mock.api.CreateStreamRequest
import kinesis.mock.cache.{Cache, CacheConfig}
import kinesis.mock.instances.arbitrary._
import kinesis.mock.instances.http4s._
import kinesis.mock.models.StreamName
import cats.effect.Resource

class KinesisMockServiceTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  test("it should accept healthcheck requests") {
    Resource.unit[IO].use(blocker =>
      for {
        cacheConfig <- CacheConfig.read(blocker)
        cache <- Cache(cacheConfig)
        app = new KinesisMockRoutes(cache).routes.orNotFound
        request = Request[IO](
          method = Method.GET,
          uri = Uri(path = "/healthcheck")
        )
        res <- app.run(request)
      } yield assert(res.status.isSuccess, res)
    )
  }

  test("it should accept valid OPTIONS requests") {
    Resource.unit[IO].use(blocker =>
      for {
        cacheConfig <- CacheConfig.read(blocker)
        cache <- Cache(cacheConfig)
        app = new KinesisMockRoutes(cache).routes.orNotFound
        request = Request[IO](
          method = Method.OPTIONS,
          headers = Headers(List(Origin.Null))
        )
        res <- app.run(request)
      } yield assert(res.status.isSuccess, res)
    )
  }

  test("it should reject OPTIONS requests without an Origin header") {
    Resource.unit[IO].use(blocker =>
      for {
        cacheConfig <- CacheConfig.read(blocker)
        cache <- Cache(cacheConfig)
        app = new KinesisMockRoutes(cache).routes.orNotFound
        request = Request[IO](
          method = Method.OPTIONS
        )
        res <- app.run(request)
      } yield assert(!res.status.isSuccess, res)
    )
  }

  test("it should accept valid CBOR requests using headers") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                Origin.Null,
                Header(
                  "Authorization",
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ),
                Header(KinesisMockHeaders.amazonDate, "20150830T123600Z"),
                Header(
                  KinesisMockHeaders.amazonTarget,
                  "Kinesis_20131202.CreateStream"
                ),
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(res.status.isSuccess, res)
      )
    }
  }

  test("it should accept valid JSON requests using headers") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                Origin.Null,
                Header(
                  "Authorization",
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ),
                Header(KinesisMockHeaders.amazonDate, "20150830T123600Z"),
                Header(
                  KinesisMockHeaders.amazonTarget,
                  "Kinesis_20131202.CreateStream"
                ),
                `Content-Type`(KinesisMockMediaTypes.amazonJson)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonJson
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(res.status.isSuccess, res)
      )
    }
  }

  test("it should accept valid CBOR requests using query params") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            uri = Uri(
              path = "/",
              query = Query(
                (KinesisMockQueryParams.amazonAction -> Some("CreateStream")),
                (KinesisMockQueryParams.amazonAuthAlgorithm -> Some(
                  "AWS4-HMAC-SHA256"
                )),
                (KinesisMockQueryParams.amazonAuthCredential -> Some(
                  "mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                )),
                (KinesisMockQueryParams.amazonAuthSignedHeaders -> Some(
                  "amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target"
                )),
                (KinesisMockQueryParams.amazonAuthSignature -> Some(
                  "4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                )),
                (KinesisMockQueryParams.amazonDateQuery -> Some(
                  "20150830T123600Z"
                ))
              )
            ),
            headers = Headers(
              List(
                Origin.Null,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(res.status.isSuccess, request.uri)
      )
    }
  }

  test("it should accept valid JSON requests using query params") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            uri = Uri(
              path = "/",
              query = Query(
                (KinesisMockQueryParams.amazonAction -> Some("CreateStream")),
                (KinesisMockQueryParams.amazonAuthAlgorithm -> Some(
                  "AWS4-HMAC-SHA256"
                )),
                (KinesisMockQueryParams.amazonAuthCredential -> Some(
                  "mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                )),
                (KinesisMockQueryParams.amazonAuthSignedHeaders -> Some(
                  "amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target"
                )),
                (KinesisMockQueryParams.amazonAuthSignature -> Some(
                  "4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                )),
                (KinesisMockQueryParams.amazonDateQuery -> Some(
                  "20150830T123600Z"
                ))
              )
            ),
            headers = Headers(
              List(
                Origin.Null,
                `Content-Type`(KinesisMockMediaTypes.amazonJson)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonJson
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(res.status.isSuccess, request.uri)
      )
    }
  }

  test("it should return amazonId2 header if no Origin is supplied") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                Header(
                  "Authorization",
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ),
                Header(KinesisMockHeaders.amazonDate, "20150830T123600Z"),
                Header(
                  KinesisMockHeaders.amazonTarget,
                  "Kinesis_20131202.CreateStream"
                ),
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(
          res.status.isSuccess && res.headers
            .get(CaseInsensitiveString(KinesisMockHeaders.amazonId2))
            .nonEmpty,
          res
        )
      )
    }
  }

  test("it should reject if no authorization is found") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                Origin.Null,
                Header(KinesisMockHeaders.amazonDate, "20150830T123600Z"),
                Header(
                  KinesisMockHeaders.amazonTarget,
                  "Kinesis_20131202.CreateStream"
                ),
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, res)
      )
    }
  }

  test("it should reject if no date is found") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                Origin.Null,
                Header(
                  "Authorization",
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ),
                Header(
                  KinesisMockHeaders.amazonTarget,
                  "Kinesis_20131202.CreateStream"
                ),
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, res)
      )
    }
  }

  test("it should reject if some auth headers aren't found") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                Origin.Null,
                Header(
                  "Authorization",
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                ),
                Header(KinesisMockHeaders.amazonDate, "20150830T123600Z"),
                Header(
                  KinesisMockHeaders.amazonTarget,
                  "Kinesis_20131202.CreateStream"
                ),
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, res)
      )
    }
  }

  test("it should reject if some auth headers and date aren't found") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                Origin.Null,
                Header(
                  "Authorization",
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                ),
                Header(
                  KinesisMockHeaders.amazonTarget,
                  "Kinesis_20131202.CreateStream"
                ),
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, res)
      )
    }
  }

  test("it reject if some auth query params aren't found") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            uri = Uri(
              path = "/",
              query = Query(
                (KinesisMockQueryParams.amazonAction -> Some("CreateStream")),
                (KinesisMockQueryParams.amazonAuthAlgorithm -> Some(
                  "AWS4-HMAC-SHA256"
                )),
                (KinesisMockQueryParams.amazonAuthCredential -> Some(
                  "mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                )),
                (KinesisMockQueryParams.amazonDateQuery -> Some(
                  "20150830T123600Z"
                ))
              )
            ),
            headers = Headers(
              List(
                Origin.Null,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, request)
      )
    }
  }

  test("it reject if some auth query params and date aren't found") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            uri = Uri(
              path = "/",
              query = Query(
                (KinesisMockQueryParams.amazonAction -> Some("CreateStream")),
                (KinesisMockQueryParams.amazonAuthAlgorithm -> Some(
                  "AWS4-HMAC-SHA256"
                )),
                (KinesisMockQueryParams.amazonAuthCredential -> Some(
                  "mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                ))
              )
            ),
            headers = Headers(
              List(
                Origin.Null,
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, request)
      )
    }
  }

  test("it should reject if no action is found") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                Origin.Null,
                Header(
                  "Authorization",
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ),
                Header(KinesisMockHeaders.amazonDate, "20150830T123600Z"),
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, res)
      )
    }
  }

  test("it should reject if action is malformed") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                Origin.Null,
                Header(
                  "Authorization",
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ),
                Header(KinesisMockHeaders.amazonDate, "20150830T123600Z"),
                Header(
                  KinesisMockHeaders.amazonTarget,
                  "thisisntright"
                ),
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, res)
      )
    }
  }

  test("it should reject if content-type is not provided") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            headers = Headers(
              List(
                Origin.Null,
                Header(
                  "Authorization",
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ),
                Header(KinesisMockHeaders.amazonDate, "20150830T123600Z"),
                Header(
                  KinesisMockHeaders.amazonTarget,
                  "Kinesis_20131202.CreateStream"
                )
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, res)
      )
    }
  }

  test("it should reject if both auth headers and query strings are provided") {
    PropF.forAllF { (streamName: StreamName) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          app = new KinesisMockRoutes(cache).routes.orNotFound
          request = Request(
            method = Method.POST,
            uri = Uri(
              path = "/",
              query = Query(
                (KinesisMockQueryParams.amazonAction -> Some("CreateStream")),
                (KinesisMockQueryParams.amazonAuthAlgorithm -> Some(
                  "AWS4-HMAC-SHA256"
                )),
                (KinesisMockQueryParams.amazonAuthCredential -> Some(
                  "mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request"
                )),
                (KinesisMockQueryParams.amazonAuthSignedHeaders -> Some(
                  "amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target"
                )),
                (KinesisMockQueryParams.amazonAuthSignature -> Some(
                  "4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                )),
                (KinesisMockQueryParams.amazonDateQuery -> Some(
                  "20150830T123600Z"
                ))
              )
            ),
            headers = Headers(
              List(
                Origin.Null,
                Header(
                  "Authorization",
                  "AWS4-HMAC-SHA256 " +
                    "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                    "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                    "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
                ),
                Header(KinesisMockHeaders.amazonDate, "20150830T123600Z"),
                Header(
                  KinesisMockHeaders.amazonTarget,
                  "Kinesis_20131202.CreateStream"
                ),
                `Content-Type`(KinesisMockMediaTypes.amazonCbor)
              )
            ),
            body = kinesisMockEntityEncoder[CreateStreamRequest](
              KinesisMockMediaTypes.amazonCbor
            ).toEntity(CreateStreamRequest(1, streamName)).body
          )
          res <- app.run(request)
        } yield assert(!res.status.isSuccess, res)
      )
    }
  }

  test("it should reject requests with invalid bodies") {
    Resource.unit[IO].use(blocker =>
      for {
        cacheConfig <- CacheConfig.read(blocker)
        cache <- Cache(cacheConfig)
        app = new KinesisMockRoutes(cache).routes.orNotFound
        request = Request(
          method = Method.POST,
          headers = Headers(
            List(
              Origin.Null,
              Header(
                "Authorization",
                "AWS4-HMAC-SHA256 " +
                  "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                  "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                  "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
              ),
              Header(KinesisMockHeaders.amazonDate, "20150830T123600Z"),
              Header(
                KinesisMockHeaders.amazonTarget,
                "Kinesis_20131202.CreateStream"
              ),
              `Content-Type`(KinesisMockMediaTypes.amazonCbor)
            )
          ),
          body = EntityEncoder[IO, String].toEntity("thisisn'tright").body
        )
        res <- app.run(request)
      } yield assert(!res.status.isSuccess, res)
    )
  }

  test("it should reject requests with invalid API requests") {
    Resource.unit[IO].use(blocker =>
      for {
        cacheConfig <- CacheConfig.read(blocker)
        cache <- Cache(cacheConfig)
        app = new KinesisMockRoutes(cache).routes.orNotFound
        request = Request(
          method = Method.POST,
          headers = Headers(
            List(
              Origin.Null,
              Header(
                "Authorization",
                "AWS4-HMAC-SHA256 " +
                  "Credential=mock-kinesis-access-key/20210402/us-east-1/kinesis/aws4_request, " +
                  "SignedHeaders=amz-sdk-invocation-id;amz-sdk-request;content-length;content-type;host;x-amz-date;x-amz-target, " +
                  "Signature=4a789f84587c3592d3ebd2fcc25e2cdcbc01bc3312771f5170b253ab6a5fedb6"
              ),
              Header(KinesisMockHeaders.amazonDate, "20150830T123600Z"),
              Header(
                KinesisMockHeaders.amazonTarget,
                "Kinesis_20131202.CreateStream"
              ),
              `Content-Type`(KinesisMockMediaTypes.amazonCbor)
            )
          ),
          body = kinesisMockEntityEncoder[CreateStreamRequest](
            KinesisMockMediaTypes.amazonCbor
          ).toEntity(CreateStreamRequest(1, StreamName(""))).body
        )
        res <- app.run(request)
      } yield assert(!res.status.isSuccess, res)
    )
  }

}
