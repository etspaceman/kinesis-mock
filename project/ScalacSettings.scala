object ScalacSettings {
  val settings = Seq(
    "-explain", // Explain type errors in more detail.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros", // Allow macro definition (besides implementation and application)
    "-language:higherKinds", // Allow higher-kinded types
    "-language:implicitConversions", // Allow definition of implicit functions called views
    "-Xfatal-warnings" // Fail the compilation if there are any warnings.
  )
}
