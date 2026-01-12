#! /bin/bash
# ----------------------------------------------------------------------------
#  Copyright 2025 WSO2, LLC. http://www.wso2.org
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

set -eu
set -o pipefail

# ======= FIPS (to install in default mode) =======
BC_FIPS_VERSION="2.0.1"
BCPKIX_FIPS_VERSION="2.0.10"
BCTLS_FIPS_VERSION="2.0.19"
BCUTIL_FIPS_VERSION="2.0.2"

# Official SHA-1 checksums (Maven Central)
EXPECTED_BC_FIPS_CHECKSUM="67cf4d43d0e86b8a493cfdfe266c226ff7ffc410"
EXPECTED_BCPKIX_FIPS_CHECKSUM="4cc5a8607f3dd6cd3fb0ee5abc2e7a068adf2cf1"
EXPECTED_BCTLS_FIPS_CHECKSUM="9cc33650ede63bc1a8281ed5c8e1da314d50bc76"
EXPECTED_BCUTIL_FIPS_CHECKSUM="c11996822d9d0f831b340bf4ea4d9d3e87a8e9de"

LEGACY_BCPROV_VERSION="1.81.0"

PRG="$0"
PRGDIR=$(dirname "$PRG")
if [ -z "${CARBON_HOME:-}" ]; then
  CARBON_HOME=$(cd "$PRGDIR/.." && pwd)
fi

LOCAL_DIR=""
MAVEN_BASE="https://repo1.maven.org/maven2"

# Parse options first
while getopts ":f:m:" opt; do
  case "$opt" in
    f) LOCAL_DIR="$OPTARG" ;;
    m) MAVEN_BASE="$OPTARG" ;;
    *) echo "Invalid option"; exit 1 ;;
  esac
done
shift $((OPTIND-1))

ARGUMENT="${1:-}"

# Directories
BACKUP_DIR="${HOME}/.wso2mi/backup"
RUNTIME_LIB_DIR="$CARBON_HOME/wso2/lib"
PLUGINS_DIR="$CARBON_HOME/wso2/components/plugins"

mkdir -p "$BACKUP_DIR" "$RUNTIME_LIB_DIR" "$PLUGINS_DIR"

sha1_cmd() {
  if command -v shasum >/dev/null 2>&1; then
    echo "shasum"
  elif command -v sha1sum >/dev/null 2>&1; then
    echo "sha1sum"
  else
    echo "ERROR: need shasum or sha1sum" >&2
    exit 1
  fi
}

verify_checksum() {
  file="$1"
  expected="$2"
  if [ -z "$expected" ]; then
    echo "Skipping checksum for $(basename "$file")"
    return 0
  fi
  tool=$(sha1_cmd)
  got=$($tool "$file" | awk '{print $1}')
  if [ "$got" != "$expected" ]; then
    echo "Checksum mismatch for $(basename "$file")"
    return 1
  fi
  echo "Checksum OK for $(basename "$file")"
}

# ---------- Backup & remove any NON-FIPS BC jars in a dir ----------
backup_and_remove_bc_jars_in_dir() {
  dir="$1"
  patterns="bcprov-*.jar bcpkix-*.jar bctls-*.jar bcutil-*.jar bcprov-jdk*.jar"

  any="false"
  for p in $patterns; do
    if ls "$dir"/$p >/dev/null 2>&1; then
      any="true"
      break
    fi
  done
  [ "$any" = "true" ] || { echo "No legacy BC jars in $dir"; return 0; }

  mkdir -p "$BACKUP_DIR"
  echo "Backing up legacy BC jars from $dir -> $BACKUP_DIR"
  for p in $patterns; do
    for f in "$dir"/$p; do
      [ -f "$f" ] || continue
      base=$(basename "$f")
      mv -f "$f" "$BACKUP_DIR/$base"
      echo "  Moved $base"
    done
  done
}

# ---------- Backup & remove specific legacy BC jars from RUNTIME_LIB_DIR ----------
backup_and_remove_specific_legacy_in_runtime() {
  mkdir -p "$BACKUP_DIR"

  LEGACY_RUNTIME_JARS=(
    "bcprov-jdk18on-${LEGACY_BCPROV_VERSION}.jar"
    "bctls-jdk18on-${LEGACY_BCPROV_VERSION}.jar"
    "bcpkix-jdk18on-${LEGACY_BCPROV_VERSION}.jar"
    "bcutil-jdk18on-${LEGACY_BCPROV_VERSION}.jar"
  )

  local found_any="false"
  for j in "${LEGACY_RUNTIME_JARS[@]}"; do
    if [ -f "$RUNTIME_LIB_DIR/$j" ]; then
      found_any="true"
      echo "Found legacy runtime jar: $j"
      mv -f "$RUNTIME_LIB_DIR/$j" "$BACKUP_DIR/$j"
      echo "  Moved $j -> $BACKUP_DIR"
    fi
  done

  if [ "$found_any" = "false" ]; then
    echo "No specified legacy BC jars found in $RUNTIME_LIB_DIR"
  else
    echo "Legacy BC jars removed from $RUNTIME_LIB_DIR"
  fi
}

download_to_path() {
  artifact="$1"  # e.g., bc-fips
  version="$2"
  dest="$3"      # absolute jar path to write
  url="$MAVEN_BASE/org/bouncycastle/$artifact/$version/$(basename "$dest")"
  echo "Downloading $url"
  curl -fL -o "$dest" "$url"
}

# ---------- Stage FIPS jar into BACKUP_DIR (cache), then install to runtime ----------
stage_fips_in_backup() {
  artifact="$1"
  version="$2"
  expected="$3"

  staged="$BACKUP_DIR/${artifact}-${version}.jar"

  if [ -n "$LOCAL_DIR" ] && [ -f "$LOCAL_DIR/${artifact}-${version}.jar" ]; then
    echo "Using local $LOCAL_DIR/${artifact}-${version}.jar -> $staged"
    cp -f "$LOCAL_DIR/${artifact}-${version}.jar" "$staged"
  elif [ ! -f "$staged" ]; then
    echo "Staging $artifact-$version into backup folder: $staged"
    download_to_path "$artifact" "$version" "$staged"
  else
    echo "Found staged in backup: $staged"
  fi

  verify_checksum "$staged" "$expected" || { rm -f "$staged"; echo "Removed bad staged jar: $staged"; exit 1; }
}

install_from_backup_to_runtime() {
  artifact="$1"
  version="$2"

  staged="$BACKUP_DIR/${artifact}-${version}.jar"
  [ -f "$staged" ] || { echo "ERROR: staged jar missing: $staged"; exit 1; }

  dest="$RUNTIME_LIB_DIR/${artifact}-${version}.jar"
  cp -f "$staged" "$dest"
  echo "Installed $(basename "$staged") -> $RUNTIME_LIB_DIR"
}

# Remove all FIPS jars from runtime
remove_fips_artifact() {
  artifact="$1" # bc-fips / bcpkix-fips / bctls-fips / bcutil-fips
  rm -f "$RUNTIME_LIB_DIR/$artifact"-*.jar 2>/dev/null || true
  echo "Removed $artifact jars from $RUNTIME_LIB_DIR"
}

# ---------- Restore legacy (non-FIPS) BC jars ONLY to a given dir ----------
restore_legacy_to_dir() {
  local target_dir="$1"
  local ver="$LEGACY_BCPROV_VERSION"

  mkdir -p "$BACKUP_DIR" "$target_dir"

  local legacy_jars=(
    "bcprov-jdk18on-${ver}.jar"
    "bcpkix-jdk18on-${ver}.jar"
    "bctls-jdk18on-${ver}.jar"
    "bcutil-jdk18on-${ver}.jar"
  )

  for jar in "${legacy_jars[@]}"; do
    local target_path="$target_dir/$jar"

    # If the jar already exists in target, skip everything for this jar
    if [ -f "$target_path" ]; then
      echo "Already present: $jar in $target_dir (skipping)"
      continue
    fi

    # Prefer restore from backup
    if [ -f "$BACKUP_DIR/$jar" ]; then
      cp -f "$BACKUP_DIR/$jar" "$target_path"
      echo "Restored $jar -> $target_dir"
      continue
    fi

    # Otherwise, download from Maven Central
    local base="${jar%%-*}"  # bcprov / bcpkix / bctls / bcutil
    local url="$MAVEN_BASE/org/bouncycastle/${base}-jdk18on/${ver}/${jar}"
    local staged="$BACKUP_DIR/$jar"

    echo "Downloading $url"
    curl -fL -o "$staged" "$url"
    cp -f "$staged" "$target_path"
    echo "Installed $jar -> $target_dir"
  done
}

# ---------- Restore WSO2-orbit bcprov plugin jar to components/plugins ----------
# Expects: bcprov-jdk18on_<ver>.wso2v1.jar in BACKUP_DIR or LOCAL_DIR
restore_orbit_bcprov_plugin() {
  local ver="$LEGACY_BCPROV_VERSION"
  local plugin_jar="bcprov-jdk18on_${ver}.wso2v1.jar"
  local target_dir="$PLUGINS_DIR"
  local target_path="$target_dir/$plugin_jar"

  mkdir -p "$BACKUP_DIR" "$target_dir"

  # If already present in plugins, skip and stay quiet
  if [ -f "$target_path" ]; then
    echo "Already present: $plugin_jar in $target_dir (skipping)"
    return 0
  fi

  # Prefer restoring from backup
  if [ -f "$BACKUP_DIR/$plugin_jar" ]; then
    cp -f "$BACKUP_DIR/$plugin_jar" "$target_path"
    echo "Restored $plugin_jar -> $target_dir"
    return 0
  fi

  # If provided, use local folder (-f /path/to/jars)
  if [ -n "$LOCAL_DIR" ] && [ -f "$LOCAL_DIR/$plugin_jar" ]; then
    cp -f "$LOCAL_DIR/$plugin_jar" "$target_path"
    echo "Installed $plugin_jar from LOCAL_DIR -> $target_dir"
    return 0
  fi

  # Otherwise warn (no public Maven URL for orbit jars)
  echo "WARNING: $plugin_jar not found in backup, LOCAL_DIR, or target; skipping plugin restore."
  echo "         Provide it via -f <dir> if you need it restored."
}

# ---------- Backup & remove all legacy BC from plugins ----------
backup_and_remove_all_legacy_bc() {
  for d in "$PLUGINS_DIR"; do
    [ -d "$d" ] || continue
    backup_and_remove_bc_jars_in_dir "$d"
  done
  if [ -d "$BACKUP_DIR" ] && ls "$BACKUP_DIR"/*.jar >/dev/null 2>&1; then
    echo "Backup present in $BACKUP_DIR"
  else
    echo "No legacy BC jars were found to back up."
  fi
}

# ---------------------------- Main ----------------------------
ARG_UP=$(printf %s "$ARGUMENT" | tr '[:lower:]' '[:upper:]')

if [ "$ARG_UP" = "DISABLE" ]; then
  echo "Disabling FIPS jars..."
  for a in bc-fips bcpkix-fips bctls-fips bcutil-fips; do
    remove_fips_artifact "$a"
  done

  echo "Restoring legacy BC jars to $RUNTIME_LIB_DIR..."
  restore_legacy_to_dir "$RUNTIME_LIB_DIR"

  echo "Restoring WSO2-orbit plugin jar to $PLUGINS_DIR..."
  restore_orbit_bcprov_plugin

  echo "Done. Please restart the server."
  exit 0

elif [ "$ARG_UP" = "VERIFY" ]; then
  # 0) Fail if legacy bcprov-jdk18on* in plugins
  if ls "$PLUGINS_DIR"/bcprov-jdk18on*.jar >/dev/null 2>&1; then
    echo "Verification failed: Found legacy bcprov-jdk18on* in $PLUGINS_DIR."
    echo "Please run this script without arguments to install FIPS jars."
    exit 1
  fi

  # 1) Fail if any legacy libs exist in wso2/lib
  LEGACY_LIB_JARS=(
    "bcprov-jdk18on-${LEGACY_BCPROV_VERSION}.jar"
    "bcpkix-jdk18on-${LEGACY_BCPROV_VERSION}.jar"
    "bctls-jdk18on-${LEGACY_BCPROV_VERSION}.jar"
    "bcutil-jdk18on-${LEGACY_BCPROV_VERSION}.jar"
  )
  for jar in "${LEGACY_LIB_JARS[@]}"; do
    if [ -f "$RUNTIME_LIB_DIR/$jar" ]; then
      echo "Verification failed: Found legacy $jar in $RUNTIME_LIB_DIR."
      echo "Please run this script without arguments to install FIPS jars."
      exit 1
    fi
  done

  # 2) Check presence of required FIPS jars (and verify checksum)
  declare -a FIPS_SPECS=(
    "bc-fips $BC_FIPS_VERSION $EXPECTED_BC_FIPS_CHECKSUM"
    "bcpkix-fips $BCPKIX_FIPS_VERSION $EXPECTED_BCPKIX_FIPS_CHECKSUM"
    "bctls-fips $BCTLS_FIPS_VERSION $EXPECTED_BCTLS_FIPS_CHECKSUM"
    "bcutil-fips $BCUTIL_FIPS_VERSION $EXPECTED_BCUTIL_FIPS_CHECKSUM"
  )

  ok=true
  for spec in "${FIPS_SPECS[@]}"; do
    set -- $spec
    artifact=$1; version=$2; expected=$3
    jar="${artifact}-${version}.jar"
    path="$RUNTIME_LIB_DIR/$jar"
    if [ ! -f "$path" ]; then
      echo "Verification failed: Missing $jar in $RUNTIME_LIB_DIR"
      ok=false
    else
      if ! verify_checksum "$path" "$expected"; then
        echo "Verification failed: Checksum mismatch for $jar in $RUNTIME_LIB_DIR"
        ok=false
      fi
    fi
  done

  if [ "$ok" = true ]; then
    echo "Verification successful: All FIPS dependencies present; no non-FIPS jars detected."
    exit 0
  else
    echo "Verification failed: One or more FIPS jars missing or invalid."
    exit 1
  fi

else
  echo "Step 1/4: Backing up & removing legacy (non-FIPS) BC jars from plugins..."
  backup_and_remove_all_legacy_bc

  echo "Step 1a/4: Backing up & removing specific legacy BC jars from $RUNTIME_LIB_DIR..."
  backup_and_remove_specific_legacy_in_runtime

  echo "Step 2/4: Staging FIPS jars into backup folder (cache) if missing..."
  stage_fips_in_backup bc-fips     "$BC_FIPS_VERSION"     "$EXPECTED_BC_FIPS_CHECKSUM"
  stage_fips_in_backup bcpkix-fips "$BCPKIX_FIPS_VERSION" "$EXPECTED_BCPKIX_FIPS_CHECKSUM"
  stage_fips_in_backup bctls-fips  "$BCTLS_FIPS_VERSION"  "$EXPECTED_BCTLS_FIPS_CHECKSUM"
  stage_fips_in_backup bcutil-fips "$BCUTIL_FIPS_VERSION" "$EXPECTED_BCUTIL_FIPS_CHECKSUM"

  echo "Step 3/4: Installing FIPS jars from backup into $RUNTIME_LIB_DIR..."
  install_from_backup_to_runtime bc-fips     "$BC_FIPS_VERSION"
  install_from_backup_to_runtime bcpkix-fips "$BCPKIX_FIPS_VERSION"
  install_from_backup_to_runtime bctls-fips  "$BCTLS_FIPS_VERSION"
  install_from_backup_to_runtime bcutil-fips "$BCUTIL_FIPS_VERSION"

  echo "Step 4/4: Done. Please restart the server."
fi
