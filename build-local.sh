#!/usr/bin/env bash
# Builds RadiantAchievements.
#  - With Maven (mvnw or mvn on PATH): mvn -q package
#  - Without Maven: javac against every jar in ~/.m2 + jar packaging into target/
# Usage: bash build-local.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ -x "$ROOT/mvnw" ]]; then
  echo "[build] Using Maven wrapper"
  "$ROOT/mvnw" -q package
  exit 0
fi
if command -v mvn >/dev/null 2>&1; then
  echo "[build] Using Maven"
  mvn -q package
  exit 0
fi

echo "[build] Maven not found -> javac fallback"
M2="${M2_REPO:-$HOME/.m2/repository}"
if [[ ! -d "$M2" ]]; then
  echo "[build] ERROR: $M2 not found" >&2
  exit 1
fi

VERSION="$(sed -n '/<artifactId>RadiantAchievements<\/artifactId>/{n;s/.*<version>\(.*\)<\/version>.*/\1/p;q}' pom.xml)"
DESCRIPTION="$(sed -n 's/.*<description>\(.*\)<\/description>.*/\1/p' pom.xml | head -1)"
VERSION="${VERSION:-1.0.0}"

BUILD="$ROOT/target/local-build"
CLASSES="$BUILD/classes"
STUBS_SRC="$BUILD/stubs-src"
STUBS_OUT="$BUILD/stubs"
rm -rf "$BUILD"
mkdir -p "$CLASSES" "$STUBS_SRC" "$STUBS_OUT"

# Windows javac needs C:/ style paths
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi; }
SEP=":"; [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* || "$(uname -s)" == CYGWIN* ]] && SEP=";"

CP=""
while IFS= read -r jar; do
  CP+="$(winpath "$jar")$SEP"
done < <(find "$M2" -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar")

# Compile-only stubs for provided APIs that may not be cached in ~/.m2 (never packaged).
has_class() { find "$M2" -path "*$1*" -name "*.jar" | grep -q .; }
if ! has_class "com/zaxxer/HikariCP"; then
  mkdir -p "$STUBS_SRC/com/zaxxer/hikari"
  cat > "$STUBS_SRC/com/zaxxer/hikari/HikariConfig.java" <<'EOF'
package com.zaxxer.hikari;
public class HikariConfig { public void setPoolName(String s){} public void setDriverClassName(String s){} public void setJdbcUrl(String s){} public void setMaximumPoolSize(int i){} public void setConnectionInitSql(String s){} public void setUsername(String s){} public void setPassword(String s){} public void setConnectionTimeout(long l){} }
EOF
  cat > "$STUBS_SRC/com/zaxxer/hikari/HikariDataSource.java" <<'EOF'
package com.zaxxer.hikari;
public class HikariDataSource implements AutoCloseable { public HikariDataSource(HikariConfig c){} public java.sql.Connection getConnection() throws java.sql.SQLException { throw new java.sql.SQLException("stub"); } public void close(){} }
EOF
  echo "[build] HikariCP not cached -> compile stub"
fi
if ! has_class "MilkBowl/VaultAPI"; then
  mkdir -p "$STUBS_SRC/net/milkbowl/vault/economy"
  cat > "$STUBS_SRC/net/milkbowl/vault/economy/EconomyResponse.java" <<'EOF'
package net.milkbowl.vault.economy;
public class EconomyResponse { public boolean transactionSuccess(){ return false; } }
EOF
  cat > "$STUBS_SRC/net/milkbowl/vault/economy/Economy.java" <<'EOF'
package net.milkbowl.vault.economy;
public interface Economy { EconomyResponse depositPlayer(org.bukkit.OfflinePlayer p, double a); String format(double a); }
EOF
  echo "[build] VaultAPI not cached -> compile stub"
fi

STUB_FILES=()
while IFS= read -r f; do STUB_FILES+=("$f"); done < <(find "$STUBS_SRC" -name "*.java")
if (( ${#STUB_FILES[@]} > 0 )); then
  javac --release 21 -proc:none -nowarn -encoding UTF-8 -d "$(winpath "$STUBS_OUT")" -cp "$CP" "${STUB_FILES[@]}"
  CP+="$(winpath "$STUBS_OUT")$SEP"
fi

SOURCES=()
while IFS= read -r f; do SOURCES+=("$f"); done < <(find src/main/java -name "*.java")
echo "[build] Compiling ${#SOURCES[@]} classes"
javac --release 21 -proc:none -Xlint:all -Xlint:-deprecation -Xlint:-removal -Xlint:-serial -Xlint:-processing \
  -Xlint:-classfile -Xlint:-options -Xlint:-path -encoding UTF-8 -d "$(winpath "$CLASSES")" -cp "$CP" "${SOURCES[@]}"

# Resources (plugin.yml filtered like Maven does)
cp -r src/main/resources/. "$CLASSES/"
sed -i "s|\${project.version}|$VERSION|g; s|\${project.description}|$DESCRIPTION|g" "$CLASSES/plugin.yml"
cp LICENSE "$CLASSES/LICENSE"

JAR="$ROOT/target/RadiantAchievements-$VERSION.jar"
rm -f "$JAR"
# The jar tool is often not on PATH (Windows javapath shims): resolve it from the JDK home.
JAR_TOOL="$(command -v jar || true)"
if [[ -z "$JAR_TOOL" ]]; then
  JHOME="${JAVA_HOME:-$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.home = //p' | tr -d '\r')}"
  for candidate in "$JHOME/bin/jar" "$JHOME/bin/jar.exe"; do
    [[ -f "$candidate" ]] && JAR_TOOL="$candidate" && break
  done
fi
if [[ -z "$JAR_TOOL" ]]; then
  echo "[build] ERROR: compiled OK but the 'jar' tool was not found (set JAVA_HOME to a JDK)" >&2
  exit 1
fi
"$JAR_TOOL" --create --file "$(winpath "$JAR")" -C "$(winpath "$CLASSES")" .
echo "[build] OK -> $JAR"
