#!/bin/bash
set -o errexit -o nounset -o pipefail
cd "`dirname $0`/.."

npx shadow-cljs compile app
node target/test.js