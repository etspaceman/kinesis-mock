package kinesis.mock

sealed trait KinesisMockException extends Exception {
  def msg: String
  override def getMessage(): String = msg
}

final case class InvalidArgumentException(msg: String)
    extends KinesisMockException
final case class LimitExceededException(msg: String)
    extends KinesisMockException
final case class ResourceInUseException(msg: String)
    extends KinesisMockException
final case class ResourceNotFoundException(msg: String)
    extends KinesisMockException
