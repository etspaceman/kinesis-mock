package kinesis.mock

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io._

class KinesisMockRoutes {
  // check headers / query params (see what kinesalite does)
  // create a sharded stream cache
  // create service that has methods for each action
  def routes = HttpRoutes.of[IO] {
    case POST -> Root    => Ok("post")
    case OPTIONS -> Root => Ok("options")
  }
}
