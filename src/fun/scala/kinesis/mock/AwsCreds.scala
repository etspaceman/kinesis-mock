package kinesis.mock

import software.amazon.awssdk.auth.credentials.{
  AwsCredentials,
  AwsCredentialsProvider
}
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider

final case class AwsCreds(accessKey: String, secretKey: String)
    extends AwsCredentials
    with AwsCredentialsProvider
    with AWSCredentials
    with AWSCredentialsProvider {
  override def accessKeyId(): String = accessKey
  override def secretAccessKey(): String = secretKey
  override def resolveCredentials(): AwsCredentials = this
  override def getAWSAccessKeyId(): String = accessKey
  override def getAWSSecretKey(): String = secretKey
  override def getCredentials(): AWSCredentials = this
  override def refresh(): Unit = ()
}

object AwsCreds {
  val LocalCreds =
    AwsCreds("mock-kinesis-access-key", "mock-kinesis-secret-key")
}
