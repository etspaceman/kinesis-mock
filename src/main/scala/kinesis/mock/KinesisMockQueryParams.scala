package kinesis.mock

import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

object KinesisMockQueryParams {
  val amazonAuthAlgorithm = "X-Amz-Algorithm"
  val amazonAuthCredential = "X-Amz-Credential"
  val amazonAuthSignature = "X-Amz-Signature"
  val amazonAuthSignedHeaders = "X-Amz-SignedHeaders"
  val amazonDateQuery = "X-Amz-Date"
  val action = "Action"

  object AmazonAuthAlgorithm
      extends OptionalQueryParamDecoderMatcher[String](amazonAuthAlgorithm)

  object AmazonAuthCredential
      extends OptionalQueryParamDecoderMatcher[String](amazonAuthCredential)

  object AmazonAuthSignature
      extends OptionalQueryParamDecoderMatcher[String](amazonAuthSignature)

  object AmazonAuthSignedHeaders
      extends OptionalQueryParamDecoderMatcher[String](amazonAuthSignedHeaders)

  object AmazonDate
      extends OptionalQueryParamDecoderMatcher[String](amazonDateQuery)

  object Action extends OptionalQueryParamDecoderMatcher[KinesisAction](action)
}
