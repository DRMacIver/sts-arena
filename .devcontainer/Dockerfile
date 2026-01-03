FROM mcr.microsoft.com/devcontainers/base:ubuntu

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl \
    git \
    # X11 and graphics libraries for headless game testing with Xvfb
    xvfb \
    libfreetype6 \
    libfontconfig1 \
    libgl1 \
    libopengl0 \
    libxtst6 \
    libxi6 \
    libxrender1 \
    && rm -rf /var/lib/apt/lists/*

# Java 8 and Maven will be installed via the java feature in devcontainer.json
