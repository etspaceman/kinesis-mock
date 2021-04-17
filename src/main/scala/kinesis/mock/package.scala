package kinesis

import cats.data.ValidatedNel

package object mock {
  type ValidatedResponse[A] = ValidatedNel[KinesisMockException, A]
  type EitherResponse[A] = Either[KinesisMockException, A]
}
