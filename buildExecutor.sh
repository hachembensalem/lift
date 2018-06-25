#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

set -e

function check_command() {
  echo -ne "Checking for ${1}... "
  if ! type "$1" > /dev/null; then
    echo "[failed]"
    echo "Missing command $1" >&2
    exit -1
  fi
  echo "[ok]"
}

function configure() {
  check_command "cmake"
  check_command "g++"

  mkdir -p lib/Executor/build
  #(cd lib/Executor/build && cmake -DCMAKE_INSTALL_PREFIX=$DIR/src/main/resources -DCMAKE_BUILD_TYPE=Debug ..)
  (cd lib/Executor/build && cmake -DCMAKE_INSTALL_PREFIX=$DIR/src/main/resources -DCMAKE_BUILD_TYPE=Release ..)
}

function build() {
  check_command "g++"
  check_command "cmake"

  (cd lib/Executor/build &&  make -j 4 install)
}

echo "Configure Executor"
configure

echo "Build executor"
build

mkdir ./target/scala-2.11/classes/lib
cp ./src/main/resources/lib/libexecutor-jni.so ./target/scala-2.11/classes/lib/
