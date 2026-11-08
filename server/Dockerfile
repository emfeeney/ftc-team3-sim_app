# ==============================================================================
# Hugging Face Spaces Dockerfile for FTC Simulation API
# Runs on free 16 GB RAM / 2 vCPU environment.
# ==============================================================================
FROM eclipse-temurin:17-jdk-jammy

ENV DEBIAN_FRONTEND=noninteractive
ENV PORT=7860

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      python3 \
      ca-certificates && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Copy workspace files
COPY . .

# Ensure permissions
RUN chmod +x scripts/*.sh 2>/dev/null || true

EXPOSE 7860

CMD ["python3", "server/server.py"]
