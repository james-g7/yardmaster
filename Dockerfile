# Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests and style checks run in CI, not in the image build
RUN mvn -B -q -DskipTests -Dcheckstyle.skip package

# Run
FROM eclipse-temurin:24-jre
RUN useradd --system --uid 10001 yardmaster \
 && mkdir -p /data && chown yardmaster /data
WORKDIR /app
COPY --from=build /src/target/yardmaster-*.jar /app/yardmaster.jar
USER 10001
ENV YARDMASTER_DATA_DIR=/data
# 8080 public API, 9090 private admin and metrics
EXPOSE 8080 9090
VOLUME ["/data"]
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s \
  CMD ["bash", "-c","exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /healthz HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q ok <&3"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/yardmaster.jar"]
