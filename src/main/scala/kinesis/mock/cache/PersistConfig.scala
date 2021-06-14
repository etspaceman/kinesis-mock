package kinesis.mock.cache

import scala.concurrent.duration.FiniteDuration

import io.circe.Encoder
import io.circe.derivation._
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

import kinesis.mock.instances.circe._

final case class PersistConfig(
    loadIfExists: Boolean,
    shouldPersist: Boolean,
    path: String,
    fileName: String,
    interval: FiniteDuration
) {

  private def createPath(starting: os.Path): os.Path = {
    val split = path.split("/").toList
    split match {
      case Nil      => starting
      case h :: Nil => starting / h
      case h :: t   => t.foldLeft(starting / h) { case (acc, x) => acc / x }
    }
  }

  def osPath: os.Path = if (path.isEmpty) os.pwd
  else {
    if (!path.startsWith("/")) {
      createPath(os.pwd)
    } else {
      createPath(os.root)
    }
  }
  def osFile = osPath / fileName
}

object PersistConfig {
  implicit val persistConfigCirceEncoder: Encoder[PersistConfig] = deriveEncoder
  implicit val persistConfigConfigReader: ConfigReader[PersistConfig] =
    deriveReader
}
