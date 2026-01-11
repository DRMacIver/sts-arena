FROM mcr.microsoft.com/devcontainers/base:ubuntu

# Add GitHub CLI repository
RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg \
    && chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | tee /etc/apt/sources.list.d/github-cli.list > /dev/null

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl \
    git \
    gh \
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
    # rsync for copying config files in postStartCommand
    rsync \
    && rm -rf /var/lib/apt/lists/*

# Install uv for Python package management (as vscode user)
USER vscode
RUN curl -LsSf https://astral.sh/uv/install.sh | sh
ENV PATH="/home/vscode/.local/bin:$PATH"
USER root

# Install just (command runner)
RUN curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh | bash -s -- --to /usr/local/bin

# Java 8 and Maven will be installed via the java feature in devcontainer.json
