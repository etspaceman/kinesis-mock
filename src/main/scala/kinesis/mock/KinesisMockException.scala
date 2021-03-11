package kinesis.mock

import cats.data.NonEmptyList

sealed trait KinesisMockException extends Exception {
  def msg: String
  override def getMessage(): String = msg
}

object KinesisMockException {
  def aggregate(
      exceptions: NonEmptyList[KinesisMockException]
  ): KinesisMockException =
    if (exceptions.tail.nonEmpty) AggregatedExceptions(exceptions)
    else exceptions.head
}

final case class InvalidArgumentException(msg: String)
    extends KinesisMockException
final case class LimitExceededException(msg: String)
    extends KinesisMockException
final case class ResourceInUseException(msg: String)
    extends KinesisMockException
final case class ResourceNotFoundException(msg: String)
    extends KinesisMockException
final case class AggregatedExceptions(
    exceptions: NonEmptyList[KinesisMockException]
) extends KinesisMockException {
  override def msg =
    s"Multiple errors found with the request:\n\t${exceptions.toList.map(_.msg).mkString("\n\t")}"
}
