FROM mcr.microsoft.com/devcontainers/base:ubuntu

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl \
    git \
    # X11 and graphics libraries for headless game testing with Xvfb
    xvfb \
    x11-xserver-utils \
    libfreetype6 \
    libfontconfig1 \
    libgl1 \
    libopengl0 \
    libxtst6 \
    libxi6 \
    libxrender1 \
    # Audio libraries (OpenAL for LWJGL)
    libopenal1 \
    libopenal-dev \
    # Python for acceptance tests
    python3 \
    && rm -rf /var/lib/apt/lists/*

# Install uv for Python package management
RUN curl -LsSf https://astral.sh/uv/install.sh | sh
ENV PATH="/root/.local/bin:$PATH"

# Java 8 and Maven will be installed via the java feature in devcontainer.json
