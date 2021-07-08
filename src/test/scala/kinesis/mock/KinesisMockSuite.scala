package kinesis.mock

import scala.concurrent.ExecutionContext

trait KinesisMockSuite
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  override val munitExecutionContext = ExecutionContext.global
}
