# syntax=docker/dockerfile:1.7
#
# Shared multi-stage Dockerfile for ALL 8 deployable Java services.
# Parameterised by --build-arg SERVICE (Maven module name) and --build-arg PORT.
#
#   docker build --build-arg SERVICE=user-service --build-arg PORT=3001 \
#                -t badmintonhub/user-service:dev .
#
# Why `COPY . .` instead of copying only the needed modules: the root pom.xml is an
# aggregator that lists all 15 modules. Maven parses every <module> before it knows
# which one you asked for, so a partial copy fails immediately with
# "Child module /app/matchmaking-service does not exist". The BuildKit cache mount on
# /root/.m2 buys back most of the layer-cache we lose by copying the whole tree.
#
# NOTE: images built here are native to the build host (arm64 on Apple Silicon).
# Anything pushed to ECR for EKS must be built with:
#   docker buildx build --platform linux/amd64 --push ...

# ---------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /app

COPY . .

# -pl <svc> -am also builds common / common-security / common-test. Safe: those three
# modules declare no spring-boot-maven-plugin, so the `repackage` goal never runs on
# them (it would fail — they have no main class).
RUN --mount=type=cache,target=/root/.m2 \
    test -n "$SERVICE" || (echo "ERROR: --build-arg SERVICE=<module> is required" && exit 1) \
 && mvn -B -pl "$SERVICE" -am -DskipTests package

# Collapse the jar to a fixed path HERE, while $SERVICE is still in scope.
# `target/*.jar` is unambiguous: spring-boot-maven-plugin renames the plain jar to
# *.jar.original during repackage, so exactly one *.jar remains.
RUN cp "$SERVICE"/target/*.jar /app/app.jar

# -------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre
ARG PORT=8080

# curl: the JRE image ships neither curl nor wget, and the compose healthcheck needs
# one. Also makes it possible to debug from inside the container.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 1001 -m spring

WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER spring

# The JVM defaults to 25% of the container memory limit for the heap, which starves
# Spring Boot + Hibernate + Kafka clients while the container still has RAM to spare.
# (UseContainerSupport is on by default since JDK 10 — no need to pass it.)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE ${PORT}
ENTRYPOINT ["java","-jar","/app/app.jar"]
