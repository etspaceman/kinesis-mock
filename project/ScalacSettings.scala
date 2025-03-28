object ScalacSettings {
  val settings = Seq(
    "-explaintypes", // Explain type errors in more detail.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros", // Allow macro definition (besides implementation and application)
    "-language:higherKinds", // Allow higher-kinded types
    "-language:implicitConversions", // Allow definition of implicit functions called views
    "-Xfatal-warnings", // Fail the compilation if there are any warnings.
    "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
    "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
    "-Ybackend-parallelism",
    "8" // Enable paralellisation — change to desired number!
  )
}
