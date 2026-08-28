#!/bin/bash

# a lancer à la racine
set -eo pipefail

mvn -q -B clean package
cp target/sheril.jar ./sheril.jar

echo "✅ sheril.jar JAR créé avec succès"
