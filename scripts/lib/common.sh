# Shared helpers for OpenMultiTrack shell scripts.
# Source from other scripts: source "$(dirname "$0")/lib/common.sh"

# Use Java 11+ for Gradle/Android builds (AGP 8.7 requires it).
ensure_java_for_gradle() {
  if command -v java >/dev/null 2>&1; then
    local ver
    ver="$(java -version 2>&1 | head -1 || true)"
    if [[ "$ver" =~ version\ \"1\.([0-9]+) ]] && [[ "${BASH_REMATCH[1]}" -ge 11 ]]; then
      return 0
    fi
    if [[ "$ver" =~ version\ \"([0-9]+) ]] && [[ "${BASH_REMATCH[1]}" -ge 11 ]]; then
      return 0
    fi
  fi

  local candidates=(
    "${JAVA_HOME:-}"
    /usr/lib/jvm/java-17-openjdk-amd64
    /usr/lib/jvm/java-21-openjdk-amd64
    /usr/lib/jvm/java-11-openjdk-amd64
    /usr/lib/jvm/default-java
    /usr/lib/jvm/java-17-openjdk
    /usr/lib/jvm/java-21-openjdk
  )
  for jdk in "${candidates[@]}"; do
    [[ -n "$jdk" && -x "$jdk/bin/java" ]] || continue
    if "$jdk/bin/java" -version 2>&1 | grep -qE 'version \"(1\.(1[1-9]|[2-9][0-9])|[2-9][0-9])'; then
      export JAVA_HOME="$jdk"
      export PATH="$JAVA_HOME/bin:$PATH"
      echo "Using JAVA_HOME=$JAVA_HOME for Gradle"
      return 0
    fi
  done

  echo "Gradle requires Java 11 or newer. Current: $(java -version 2>&1 | head -1 || echo 'java not found')" >&2
  echo "Install OpenJDK 17+ or set JAVA_HOME, e.g. export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64" >&2
  return 1
}

# Parse adb serial and test mode from script args (order-independent).
# Sets globals: OMT_ADB_SERIAL, OMT_TEST_MODE
omt_parse_serial_and_mode() {
  OMT_ADB_SERIAL=""
  OMT_TEST_MODE="all"
  for arg in "$@"; do
    case "$arg" in
      fixtures|hardware|all) OMT_TEST_MODE="$arg" ;;
      -h|--help) OMT_SHOW_HELP=1 ;;
      *)
        if [[ -z "$OMT_ADB_SERIAL" ]]; then
          OMT_ADB_SERIAL="$arg"
        else
          echo "Unexpected extra argument: $arg" >&2
          return 1
        fi
        ;;
    esac
  done
}
