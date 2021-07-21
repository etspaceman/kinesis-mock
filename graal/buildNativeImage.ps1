$JAR_FILE = $env:JAR_FILE;
$OUTPUT_FILE = $env:OUTPUT_FILE;
$STATIC_ARG = if($env:STATIC_TYPE -eq "mostly-static") { "-H:+StaticExecutableWithDynamicLibC" } else { "" }

native-image.cmd `
    --no-server `
    $STATIC_ARG `
    -J-Xmx7G `
    --no-fallback `
    --verbose `
    --enable-all-security-services `
    --enable-url-protocols=http,https `
    --initialize-at-build-time=scala `
    -H:ReflectionConfigurationFiles=graal\reflect-config.json `
    -H:ResourceConfigurationFiles=graal\resource-config.json `
    -H:+ReportExceptionStackTraces `
    -H:+AddAllCharsets `
    --report-unsupported-elements-at-runtime `
    --allow-incomplete-classpath `
    --install-exit-handlers `
    -jar $JAR_FILE `
    $OUTPUT_FILE