FROM debian:bookworm-slim

# Offline packet analysis and scoped capture tools, separate from the application image.
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        tcpdump tshark ffmpeg \
    && rm -rf /var/lib/apt/lists/*

CMD ["tshark", "--version"]
