#!/usr/bin/env bash
# Installs Temurin 25 to ~/.local/jdk25 if it is not there yet and registers it with Gradle.
# Idempotent: safe to run again. Usage: scripts/setup-jdk.sh
set -euo pipefail

JDK_DIR="${HOME}/.local/jdk25"
GRADLE_PROPS="${HOME}/.gradle/gradle.properties"
# Optional pinning for reproducible environments (both empty = newest Temurin 25):
#   JDK_VERSION, e.g. "jdk-25.0.1+8" (a release name of Temurin)
#   JDK_SHA256   expected SHA-256 of the archive; if set it is used instead of the checksum published next to the download
if [[ -n "${JDK_VERSION:-}" ]]; then
    URL="https://api.adoptium.net/v3/binary/version/${JDK_VERSION}/linux/x64/jdk/hotspot/normal/eclipse"
else
    URL="https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"
fi

if [[ -x "${JDK_DIR}/bin/java" ]] && "${JDK_DIR}/bin/java" -version 2>&1 | grep -q '"25'; then
    echo "JDK 25 already installed in ${JDK_DIR}"
else
    tmp="$(mktemp -d)"
    trap 'rm -rf "${tmp}"' EXIT
    echo "Downloading Temurin 25 ..."
    # The API redirects to the GitHub release asset, which has a .sha256.txt next to it
    release_url="$(curl -fsSI "${URL}" | tr -d '\r' | awk 'tolower($1)=="location:" {print $2; exit}')"
    if [[ -z "${release_url}" ]]; then
        echo "Could not find the download address (no redirect from ${URL})." >&2
        exit 1
    fi
    curl -fsSL -o "${tmp}/jdk.tar.gz" "${release_url}"
    if [[ -n "${JDK_SHA256:-}" ]]; then
        expected="${JDK_SHA256}"
    else
        expected="$(curl -fsSL "${release_url}.sha256.txt" | awk '{print $1}')"
    fi
    actual="$(sha256sum "${tmp}/jdk.tar.gz" | awk '{print $1}')"
    if [[ -z "${expected}" || "${expected}" != "${actual}" ]]; then
        echo "Checksum mismatch, aborting." >&2
        exit 1
    fi
    mkdir -p "${tmp}/x"
    tar -xzf "${tmp}/jdk.tar.gz" -C "${tmp}/x" --strip-components=1
    rm -rf "${JDK_DIR}"
    mkdir -p "$(dirname "${JDK_DIR}")"
    mv "${tmp}/x" "${JDK_DIR}"
    echo "Installed JDK 25 to ${JDK_DIR}"
fi

# Gradle toolchains do not find ~/.local on their own
mkdir -p "$(dirname "${GRADLE_PROPS}")"
touch "${GRADLE_PROPS}"
if ! grep -q 'org.gradle.java.installations.paths=.*jdk25' "${GRADLE_PROPS}"; then
    echo "org.gradle.java.installations.paths=${JDK_DIR}" >> "${GRADLE_PROPS}"
    echo "Registered ${JDK_DIR} in ${GRADLE_PROPS}"
fi

echo "Done. Optional: export JAVA_HOME=${JDK_DIR}"
