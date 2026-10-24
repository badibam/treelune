#!/bin/sh
# Store the hosting password in the KDE wallet: only once it has opened the account, and only
# announced once the wallet gives it back.
set -eu

bin=$(cd "$(dirname "$0")" && pwd)
if [ ! -r "$bin/deploy_config.sh" ]; then
    echo "No site/bin/deploy_config.sh: copy deploy_config.sh.example and fill it." >&2
    exit 1
fi
. "$bin/deploy_config.sh"
. "$bin/wallet.sh"
wallet_tool

if [ ! -t 0 ]; then
    echo "Run it from a terminal." >&2
    exit 1
fi

trap 'stty echo' EXIT INT TERM
printf 'SFTP password for %s: ' "$(wallet_entry)"
stty -echo
IFS= read -r PASS || PASS=''
stty echo
echo

if [ -z "$PASS" ]; then
    echo "Empty password, nothing stored." >&2
    exit 1
fi
if ! curl_auth | curl --silent --fail --config - --output /dev/null "$(remote_base)"; then
    echo "Login refused for $(wallet_entry), nothing stored." >&2
    exit 1
fi

printf '%s' "$PASS" | wallet write
if [ "$(wallet read)" != "$PASS" ]; then
    echo "The KDE wallet did not keep the password, nothing stored." >&2
    exit 1
fi
echo "Stored: SFTP password for $(wallet_entry) in the KDE wallet."
