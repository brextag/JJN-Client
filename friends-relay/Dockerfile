# Multi-stage build for JJN Friends Relay Server (Java 21, zero Gradle/Loom overhead)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src

RUN apk add --no-cache curl \
 && mkdir -p /out/lib /out/classes \
 && curl -fsSL "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar" -o /out/lib/gson.jar

COPY src/main/java /src/src/main/java

RUN javac --release 21 -cp /out/lib/gson.jar -d /out/classes $(find /src/src/main/java -name "*.java") \
 && jar --create --file /out/friends-relay.jar --main-class com.jjn.relay.JjnRelayServer -C /out/classes .

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /out/friends-relay.jar /app/friends-relay.jar
COPY --from=build /out/lib/gson.jar /app/gson.jar

RUN mkdir -p /data
VOLUME ["/data"]

ENV JJN_RELAY_PORT=25580
ENV JJN_RELAY_DATA=/data/relay-data.json

EXPOSE 25580

ENTRYPOINT ["java", "-Xms32m", "-Xmx192m", "-cp", "/app/friends-relay.jar:/app/gson.jar", "com.jjn.relay.JjnRelayServer"]
