addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.1")
addSbtPlugin("org.typelevel" % "sbt-typelevel-settings" % "0.4.22")
addSbtPlugin("org.typelevel" % "sbt-typelevel-github" % "0.4.22")
addSbtPlugin("org.typelevel" % "sbt-typelevel-versioning" % "0.4.22")
addSbtPlugin("org.typelevel" % "sbt-typelevel-ci" % "0.4.22")
addSbtPlugin("org.typelevel" % "sbt-typelevel-site" % "0.4.22")
addSbtPlugin("org.typelevel" % "sbt-typelevel-mergify" % "0.4.22")
addSbtPlugin("org.typelevel" % "sbt-typelevel-kernel" % "0.4.22")
addSbtPlugin("org.typelevel" % "sbt-typelevel-no-publish" % "0.4.22")
addSbtPlugin("org.portable-scala" % "sbt-crossproject" % "1.3.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.2")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.1")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0")
addSbtPlugin("io.chrisdavenport" %% "sbt-npm-package" % "0.1.2")
// Explicitly bumping until sbt-typelevel upgrades.
// Older versions exit sbt on compilation failures.
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.3.7")
libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-nop" % "2.0.7"
)
