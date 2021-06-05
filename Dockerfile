FROM johnnyjayjay/leiningen:openjdk11 AS build
WORKDIR /usr/src/instant-poll
COPY . .
RUN apt-get update
RUN apt-get -y --no-install-recommends install libsodium-dev
RUN lein deps
RUN lein uberjar

FROM build
ARG version
ARG jar=instant-poll-$version-standalone.jar
WORKDIR /usr/app/instant-poll
COPY --from=build /usr/src/instant-poll/target/$jar .
ENV jar=$jar
CMD java -jar $jar
