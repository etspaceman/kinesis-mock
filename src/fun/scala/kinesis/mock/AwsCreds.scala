package kinesis.mock

import software.amazon.awssdk.auth.credentials.{
  AwsCredentials,
  AwsCredentialsProvider
}

final case class AwsCreds(accessKey: String, secretKey: String)
    extends AwsCredentials
    with AwsCredentialsProvider {
  override def accessKeyId(): String = accessKey
  override def secretAccessKey(): String = secretKey
  override def resolveCredentials(): AwsCredentials = this
}

object AwsCreds {
  val LocalCreds =
    AwsCreds("mock-kinesis-access-key", "mock-kinesis-secret-key")
}
