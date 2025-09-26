#!/bin/bash

# Variables
CATALINA_HOME="/home/cedric/Java/tomcat/tomcat"
SRC_DIR="src"
BIN_DIR="bin"
JAR_NAME="framework.jar"
TEST_LIB="../Test/lib"   # chemin vers lib de Test

# Nettoyage
rm -rf "$BIN_DIR"
mkdir -p "$BIN_DIR"

# Compilation avec classpath incluant Tomcat
javac -cp "$CATALINA_HOME/lib/*" -d "$BIN_DIR" $(find $SRC_DIR -name "*.java")

# Création du JAR
jar cf "$JAR_NAME" -C "$BIN_DIR" .

echo "Framework compilé : $JAR_NAME"

# Copie automatique dans Test/lib
if [ -d "$TEST_LIB" ]; then
    cp "$JAR_NAME" "$TEST_LIB/"
    echo "Copié dans $TEST_LIB/"
else
    echo "Attention : dossier $TEST_LIB introuvable, copie annulée."
fi

