FROM docker.1ms.run/debian:12-slim

ENV TZ=Asia/Shanghai

RUN mkdir -p  /app/data

WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends tzdata ca-certificates && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone && \
    mkdir -p /app /app/data && \
    rm -rf /var/lib/apt/lists/*

COPY ./server/build/native/nativeCompile/server /app/server

RUN chmod 755 /app/server

ENTRYPOINT ["/app/server"]