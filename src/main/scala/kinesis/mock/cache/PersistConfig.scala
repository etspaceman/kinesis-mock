package kinesis.mock.cache

import scala.concurrent.duration.FiniteDuration

import io.circe.Encoder
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

  private def createPath(starting: os.Path, p: String): os.Path = {
    val split = p.split("/").toList
    split match {
      case Nil      => starting
      case h :: Nil => starting / h
      case h :: t   => t.foldLeft(starting / h) { case (acc, x) => acc / x }
    }
  }

  def osPath: os.Path = if (path.isEmpty) os.pwd
  else {
    if (!path.startsWith("/")) {
      createPath(os.pwd, path)
    } else {
      createPath(os.root, path.drop(1))
    }
  }
  def osFile = osPath / fileName
}

object PersistConfig {
  implicit val persistConfigCirceEncoder: Encoder[PersistConfig] =
    Encoder.forProduct5(
      "loadIfExists",
      "shouldPersist",
      "path",
      "fileName",
      "interval"
    )(x => (x.loadIfExists, x.shouldPersist, x.path, x.fileName, x.interval))
  implicit val persistConfigConfigReader: ConfigReader[PersistConfig] =
    deriveReader
}
