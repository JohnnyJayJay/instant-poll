FROM johnnyjayjay/leiningen:openjdk11 AS build
WORKDIR /usr/src/instant-poll
COPY . .
RUN lein uberjar

FROM openjdk:11
ARG jar=instant-poll-*-standalone.jar
WORKDIR /usr/app/instant-poll
COPY --from=build /usr/src/instant-poll/target/$jar .
ENV jar=$jar
CMD java -jar --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED $jar
