package kinesis.mock

import scala.util.Try

import java.util.UUID

import cats.syntax.all._
import com.github.f4b6a3.uuid.UuidCreator
import org.http4s.{Header, ParseFailure}
import org.typelevel.ci._

final case class AmazonAuthorization(value: String)
object AmazonAuthorization {
  implicit val amazonAuthorizationHeaderInstance
      : Header[AmazonAuthorization, Header.Single] =
    Header.create(
      ci"Authorization",
      _.value,
      x => Right(AmazonAuthorization(x))
    )
}

final case class AmazonId2(value: String)
object AmazonId2 {
  implicit val amazonId2HeaderInstance: Header[AmazonId2, Header.Single] =
    Header.create(
      ci"x-amz-id-2",
      _.value,
      x => Right(AmazonId2(x))
    )
}

final case class AmazonRequestId(value: UUID)
object AmazonRequestId {
  implicit val amazonRequestIdHeaderInstance
      : Header[AmazonRequestId, Header.Single] =
    Header.create(
      ci"x-amzn-RequestId",
      x => UuidCreator.toString(x.value),
      x =>
        Try(UuidCreator.fromString(x)).toEither
          .bimap(e => ParseFailure(e.getMessage(), ""), AmazonRequestId.apply)
    )
}

final case class AmazonTarget(value: String)
object AmazonTarget {
  implicit val amazonTargetHeaderInstance: Header[AmazonTarget, Header.Single] =
    Header.create(
      ci"x-amz-target",
      _.value,
      x => Right(AmazonTarget(x))
    )
}

final case class AmazonDateHeader(value: String)
object AmazonDateHeader {
  implicit val amazonDateHeaderInstance
      : Header[AmazonDateHeader, Header.Single] =
    Header.create(
      ci"x-amz-date",
      _.value,
      x => Right(AmazonDateHeader(x))
    )
}

final case class AccessControlRequestHeaders(value: String)
object AccessControlRequestHeaders {
  implicit val accessControlRequestHeadersHeaderInstance
      : Header[AccessControlRequestHeaders, Header.Single] =
    Header.create(
      ci"access-control-request-headers",
      _.value,
      x => Right(AccessControlRequestHeaders(x))
    )
}

final case class AccessControlRequestMethod(value: String)
object AccessControlRequestMethod {
  implicit val accessControlRequestMethodHeaderInstance
      : Header[AccessControlRequestMethod, Header.Single] =
    Header.create(
      ci"access-control-request-method",
      _.value,
      x => Right(AccessControlRequestMethod(x))
    )
}

final case class AccessControlExposeHeaders(value: String)
object AccessControlExposeHeaders {
  implicit val accessControlExposeHeadersHeaderInstance
      : Header[AccessControlExposeHeaders, Header.Single] =
    Header.create(
      ci"Access-Control-Expose-Headers",
      _.value,
      x => Right(AccessControlExposeHeaders(x))
    )
}

final case class AccessControlAllowOrigin(value: String)
object AccessControlAllowOrigin {
  implicit val accessControlAllowOriginHeaderInstance
      : Header[AccessControlAllowOrigin, Header.Single] =
    Header.create(
      ci"Access-Control-Allow-Origin",
      _.value,
      x => Right(AccessControlAllowOrigin(x))
    )
}

final case class AccessControlAllowHeaders(value: String)
object AccessControlAllowHeaders {
  implicit val accessControlAllowHeadersHeaderInstance
      : Header[AccessControlAllowHeaders, Header.Single] =
    Header.create(
      ci"Access-Control-Allow-Headers",
      _.value,
      x => Right(AccessControlAllowHeaders(x))
    )
}

final case class AccessControlAllowMethods(value: String)
object AccessControlAllowMethods {
  implicit val accessControlAllowMethodsHeaderInstance
      : Header[AccessControlAllowMethods, Header.Single] =
    Header.create(
      ci"Access-Control-Allow-Methods",
      _.value,
      x => Right(AccessControlAllowMethods(x))
    )
}

final case class AccessControlMaxAge(value: String)
object AccessControlMaxAge {
  implicit val accessControlMaxAgeHeaderInstance
      : Header[AccessControlMaxAge, Header.Single] =
    Header.create(
      ci"Access-Control-Max-Age",
      _.value,
      x => Right(AccessControlMaxAge(x))
    )
}
