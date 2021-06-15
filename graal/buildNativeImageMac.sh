JAR_FILE=${JAR_FILE:-kinesis-mock.jar}
OUTPUT_FILE=${OUTPUT_FILE:-kinesis-mock-native}

native-image \
    --no-server \
    -J-Xmx7G \
    --no-fallback \
    --verbose \
    --enable-all-security-services \
    --enable-url-protocols=http,https \
    --initialize-at-build-time=scala \
    -H:ReflectionConfigurationFiles=graal/reflect-config.json \
    -H:ResourceConfigurationFiles=graal/resource-config.json \
    -H:+ReportExceptionStackTraces \
    -H:+AddAllCharsets \
    --report-unsupported-elements-at-runtime \
    --allow-incomplete-classpath \
    --install-exit-handlers \
    -jar ${JAR_FILE} \
    ${OUTPUT_FILE}