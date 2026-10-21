#!/bin/bash

# Generates the keystore used to sign the release APKs, outside the repository.
# Usage: ./generate_keystore.sh

set -e

# The key lives here rather than in the repository: a gitignored file at the root would
# still be swept away by `git clean -xdf`, and this key cannot be regenerated -- it is the
# app's identity to every phone that installed it.
CONFIG_DIR="$HOME/.config/treelune"
KEYSTORE_FILE="$CONFIG_DIR/release.keystore"
SIGNING_ENV="$CONFIG_DIR/signing.env"
KEY_ALIAS="treelune-release"

echo "Generating the release key for Treelune"

mkdir -p "$CONFIG_DIR"
chmod 700 "$CONFIG_DIR"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "A key already exists: $KEYSTORE_FILE"
    echo "Replacing it means no installed copy of the app can ever be updated again:"
    echo "Android refuses an update signed with a different key."
    read -p "Replace it? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Cancelled"
        exit 0
    fi
    rm "$KEYSTORE_FILE"
fi

echo "Signing credentials:"
read -s -p "Keystore password: " KEYSTORE_PASSWORD
echo
read -s -p "Key password: " KEY_PASSWORD
echo

echo "Certificate details:"
read -p "Full name: " CN
read -p "Organization: " O
read -p "City: " L
read -p "State or province: " ST
read -p "Country code (2 letters): " C

keytool -genkeypair \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 25000 \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=$CN, OU=Android Development, O=$O, L=$L, ST=$ST, C=$C"

# The passwords go to the session's keyring, where ./run release reads them: never retyped,
# never printed, never in a file
printf %s "$KEYSTORE_PASSWORD" | secret-tool store --label 'treelune : TREELUNE_KEYSTORE_PASSWORD' projet treelune var TREELUNE_KEYSTORE_PASSWORD
printf %s "$KEY_PASSWORD" | secret-tool store --label 'treelune : TREELUNE_KEY_PASSWORD' projet treelune var TREELUNE_KEY_PASSWORD

# What ./run release reads besides: the keystore and its alias
cat > "$SIGNING_ENV" <<EOF
# La clé de release de Treelune. Hors du dépôt, et hors de sa portée : un fichier
# gitignoré à la racine se ferait quand même emporter par un \`git clean -xdf\`.
#
#   ./run release
#
# Cette clé est l'identité de l'app pour qui l'installe depuis GitHub. Perdue, plus
# aucune mise à jour ne s'installe par-dessus — il faut désinstaller, ce qui efface
# les données. À sauvegarder ailleurs que sur ce disque.
# Ses deux mots de passe sont au trousseau de la session (secret-tool, projet treelune).
export TREELUNE_KEYSTORE=$KEYSTORE_FILE
export TREELUNE_KEY_ALIAS=$KEY_ALIAS
EOF

chmod 600 "$KEYSTORE_FILE" "$SIGNING_ENV"

echo "Key generated: $KEYSTORE_FILE"
echo "Its passwords are in the keyring (secret-tool lookup projet treelune var TREELUNE_KEYSTORE_PASSWORD)."
echo ""
echo "Back the keystore and its passwords up somewhere other than this disk. Lost, the app can never be"
echo "updated on any phone that installed it: only uninstalled, which erases its data."
echo ""
echo "Contents:"
keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS"
