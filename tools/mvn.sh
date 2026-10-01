#!/usr/bin/env bash
# Maven 启动器
# 背景：Git Bash(MSYS) 会把 mvn 脚本里的 -classpath 路径做 POSIX->Windows 转换，
#       导致 "找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher"。
#       这里显式用 Windows 风格路径调用 java，彻底绕开该问题。
#
# 用法: bash tool/mvn.sh <项目目录> <maven 参数...>
# 例:   bash tool/mvn.sh retail-erp/backend clean package -DskipTests
set -euo pipefail

MAVEN_HOME_DIR="D:/hadooplearn/apache-maven-3.9.5"
BOOT_JAR="$MAVEN_HOME_DIR/boot/plexus-classworlds-2.7.0.jar"

if [ $# -lt 1 ]; then
  echo "用法: bash tool/mvn.sh <项目目录> <maven 参数...>" >&2
  exit 1
fi

PROJECT_DIR="$1"
shift

cd "$PROJECT_DIR"
# pwd -W 输出 Windows 风格绝对路径(如 C:/Users/...)，Maven 需要这种格式
PROJECT_WIN="$(pwd -W 2>/dev/null || pwd)"

exec java \
  -classpath "$BOOT_JAR" \
  "-Dclassworlds.conf=$MAVEN_HOME_DIR/bin/m2.conf" \
  "-Dmaven.home=$MAVEN_HOME_DIR" \
  "-Dmaven.multiModuleProjectDirectory=$PROJECT_WIN" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
