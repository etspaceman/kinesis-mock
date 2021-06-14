package kinesis.mock.cache

import com.typesafe.config.ConfigValueFactory
import org.scalacheck.{Prop, Test}

import kinesis.mock.api.CreateStreamRequest
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models.StreamName

class CacheConfigTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test(
    "It should properly parse valid INITIALIZE_STREAMS string with a single stream"
  )(Prop.forAll {
    (
      streamName: StreamName
    ) =>
      val res = CacheConfig.initializeStreamsReader.from(
        ConfigValueFactory.fromAnyRef(s"$streamName:3")
      )
      val expected = List(
        CreateStreamRequest(3, streamName)
      )

      assert(res == Right(expected), s"$res")
  })

  test(
    "It should properly parse valid INITIALIZE_STREAMS string with multiple streams"
  )(Prop.forAll {
    (
        streamName1: StreamName,
        streamName2: StreamName,
        streamName3: StreamName
    ) =>
      val res = CacheConfig.initializeStreamsReader.from(
        ConfigValueFactory.fromAnyRef(
          s"$streamName1:3,$streamName2:2,$streamName3:1"
        )
      )
      val expected = List(
        CreateStreamRequest(3, streamName1),
        CreateStreamRequest(2, streamName2),
        CreateStreamRequest(1, streamName3)
      )

      assert(res == Right(expected), s"$res")
  })

  test("It should not parse INITIALIZE_STREAMS string without shardCount")(
    Prop.forAll {
      (
        streamName: StreamName
      ) =>
        val res = CacheConfig.initializeStreamsReader.from(
          ConfigValueFactory.fromAnyRef(streamName.toString)
        )
        assert(res.isLeft, s"$res")
    }
  )

  test("It should not parse INITIALIZE_STREAMS string with invalid shardCount")(
    Prop.forAll {
      (
        streamName: StreamName
      ) =>
        val res = CacheConfig.initializeStreamsReader.from(
          ConfigValueFactory.fromAnyRef(s"$streamName:badShard")
        )
        assert(res.isLeft, s"$res")
    }
  )

  test("It should not parse INITIALIZE_STREAMS with an empty string") {
    val res = CacheConfig.initializeStreamsReader.from(
      ConfigValueFactory.fromAnyRef("")
    )
    assert(res.isLeft, s"$res")
  }

  test("It should not parse INITIALIZE_STREAMS with invalid delimiters")(
    Prop.forAll {
      (
          streamName1: StreamName,
          streamName2: StreamName
      ) =>
        val res = List(
          CacheConfig.initializeStreamsReader.from(
            ConfigValueFactory.fromAnyRef(s":$streamName1")
          ),
          CacheConfig.initializeStreamsReader.from(
            ConfigValueFactory.fromAnyRef(s"$streamName1:")
          ),
          CacheConfig.initializeStreamsReader.from(
            ConfigValueFactory.fromAnyRef(s",$streamName1:3")
          ),
          CacheConfig.initializeStreamsReader.from(
            ConfigValueFactory.fromAnyRef(s"$streamName1:3,$streamName2::2")
          ),
          CacheConfig.initializeStreamsReader.from(
            ConfigValueFactory.fromAnyRef(s"$streamName1:3,,$streamName2:2")
          )
        )
        assert(res.forall(_.isLeft), s"${res.map(_.isLeft)}")
    }
  )
}
