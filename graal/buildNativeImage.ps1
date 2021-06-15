$JAR_FILE = $env:JAR_FILE ?? "kinesis-mock.jar";
$OUTPUT_FILE = $env:OUTPUT_FILE ?? "kinesis-mock-native";

native-image.cmd `
    --no-server `
    --static `
    --libc=musl `
    -J-Xmx7G `
    --no-fallback `
    --verbose `
    --enable-all-security-services `
    --enable-url-protocols=http,https `
    --initialize-at-build-time=scala `
    -H:ReflectionConfigurationFiles=graal/reflect-config.json `
    -H:ResourceConfigurationFiles=graal/resource-config.json `
    -H:+ReportExceptionStackTraces `
    -H:+AddAllCharsets `
    --report-unsupported-elements-at-runtime `
    --allow-incomplete-classpath `
    --install-exit-handlers `
    -jar $JAR_FILE `
    $OUTPUT_FILE