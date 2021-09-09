package kinesis.mock.cache

import enumeratum.scalacheck._
import org.scalacheck.{Gen, Prop, Test}

import kinesis.mock.api.CreateStreamRequest
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models.{AwsRegion, StreamName}
import kinesis.mock.syntax.scalacheck._

class CacheConfigTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test(
    "It should properly parse valid INITIALIZE_STREAMS string with a single stream"
  )(Prop.forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      val res = CacheConfig
        .initializeStreamsReader(awsRegion, s"$streamName:3")
      val expected = Map(
        awsRegion -> List(
          CreateStreamRequest(3, streamName)
        )
      )

      assert(res == Right(expected), s"$res")
  })

  test(
    "It should properly parse valid INITIALIZE_STREAMS string with multiple streams"
  )(Prop.forAll {
    (
        streamName1: StreamName,
        streamName2: StreamName,
        streamName3: StreamName,
        awsRegion: AwsRegion
    ) =>
      val res = CacheConfig
        .initializeStreamsReader(
          awsRegion,
          s"$streamName1:3,$streamName2:2,$streamName3:1"
        )

      val expected = Map(
        awsRegion -> List(
          CreateStreamRequest(3, streamName1),
          CreateStreamRequest(2, streamName2),
          CreateStreamRequest(1, streamName3)
        )
      )

      assert(res == Right(expected), s"$res")
  })

  test(
    "It should properly parse valid INITIALIZE_STREAMS string with multiple streams and regions"
  )(Prop.forAll {
    (
        streamName1: StreamName,
        streamName2: StreamName,
        streamName3: StreamName,
        streamName4: StreamName,
        awsRegion: AwsRegion
    ) =>
      val testRegion1 =
        Gen.oneOf(AwsRegion.values.filterNot(_ == awsRegion)).one
      val testRegion2 = Gen
        .oneOf(
          AwsRegion.values.filterNot(x => x == awsRegion && x == testRegion1)
        )
        .one
      val res = CacheConfig
        .initializeStreamsReader(
          awsRegion,
          s"$streamName1:3:${testRegion1.entryName},$streamName2:2,$streamName3:1:${testRegion1.entryName},$streamName4:1:${testRegion2.entryName}"
        )

      val expected = Map(
        awsRegion -> List(
          CreateStreamRequest(2, streamName2)
        ),
        testRegion1 -> List(
          CreateStreamRequest(3, streamName1),
          CreateStreamRequest(1, streamName3)
        ),
        testRegion2 -> List(CreateStreamRequest(1, streamName4))
      )

      assert(res == Right(expected), s"$res")
  })

  test("It should not parse INITIALIZE_STREAMS string without shardCount")(
    Prop.forAll {
      (
          streamName: StreamName,
          awsRegion: AwsRegion
      ) =>
        val res = CacheConfig
          .initializeStreamsReader(awsRegion, streamName.toString)

        assert(res.isLeft, s"$res")
    }
  )

  test("It should not parse INITIALIZE_STREAMS string with invalid shardCount")(
    Prop.forAll {
      (
          streamName: StreamName,
          awsRegion: AwsRegion
      ) =>
        val res = CacheConfig
          .initializeStreamsReader(awsRegion, s"$streamName:badShard")

        assert(res.isLeft, s"$res")
    }
  )

  test("It should not parse INITIALIZE_STREAMS with an empty string") {
    val res = CacheConfig
      .initializeStreamsReader(AwsRegion.US_EAST_1, "")

    assert(res.isLeft, s"$res")
  }

  test("It should not parse INITIALIZE_STREAMS with invalid delimiters")(
    Prop.forAll {
      (
          streamName1: StreamName,
          streamName2: StreamName,
          awsRegion: AwsRegion
      ) =>
        val res = List(
          CacheConfig
            .initializeStreamsReader(awsRegion, s":$streamName1"),
          CacheConfig
            .initializeStreamsReader(awsRegion, s"$streamName1:"),
          CacheConfig
            .initializeStreamsReader(awsRegion, s",$streamName1:3"),
          CacheConfig
            .initializeStreamsReader(
              awsRegion,
              s"$streamName1:3,$streamName2::2"
            ),
          CacheConfig
            .initializeStreamsReader(
              awsRegion,
              s"$streamName1:3,,$streamName2:2"
            )
        )
        assert(res.forall(_.isLeft), s"${res.map(_.isLeft)}")
    }
  )
}
