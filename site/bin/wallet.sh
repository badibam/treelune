# Sourced, after deploy_config.sh, by every script that opens the hosting account. The password
# lives in the KDE wallet "kdewallet", folder "sftp", entry <DEPLOY_USER>@<host>: one entry per
# hosting account, shared by every site on it. It never sits in a file and never reaches a command
# line.

for var in DEPLOY_URL DEPLOY_USER DEPLOY_REMOTE_ROOT; do
    eval "value=\${$var:-}"
    if [ -z "$value" ]; then
        echo "$var is not set in site/bin/deploy_config.sh" >&2
        exit 1
    fi
done
case "$DEPLOY_URL" in
    sftp://*) ;;
    *) echo "DEPLOY_URL must start with sftp:// (no plaintext transfer): $DEPLOY_URL" >&2; exit 1 ;;
esac

wallet_entry() {
    host=${DEPLOY_URL#sftp://}
    printf '%s@%s' "$DEPLOY_USER" "${host%%/*}"
}

wallet_tool() {
    python3 -c 'import dbus' 2>/dev/null || {
        echo "The KDE wallet is reached over D-Bus: python3 and its dbus module are needed (package python3-dbus)." >&2
        exit 1
    }
}

# `read` prints the entry, nothing when absent; `write` stores stdin. Over D-Bus rather than
# kwallet-query, whose write exits 0 and stores nothing.
wallet() {
    python3 -c '
import sys
import dbus

APP = "treelune"
mode, folder, entry = sys.argv[1:4]
try:
    kwallet = dbus.Interface(dbus.SessionBus().get_object("org.kde.kwalletd6", "/modules/kwalletd6"), "org.kde.KWallet")
    handle = kwallet.open("kdewallet", 0, APP)
    if handle < 0:
        sys.exit("The KDE wallet does not open.")
    if mode == "write":
        if not kwallet.hasFolder(handle, folder, APP) and not kwallet.createFolder(handle, folder, APP):
            sys.exit("Cannot create the wallet folder " + folder + ".")
        if kwallet.writePassword(handle, folder, entry, sys.stdin.read(), APP) != 0:
            sys.exit("Cannot write the wallet entry " + entry + ".")
    elif kwallet.hasFolder(handle, folder, APP) and kwallet.hasEntry(handle, folder, entry, APP):
        print(kwallet.readPassword(handle, folder, entry, APP), end="")
except dbus.DBusException as e:
    sys.exit("KDE wallet unreachable: " + e.get_dbus_message())
' "$1" sftp "$(wallet_entry)"
}

# Sets PASS from the wallet, or refuses: a silent wallet is never covered by a prompt, which would
# hide that it stopped answering.
wallet_password() {
    wallet_tool
    PASS=$(wallet read) || exit 1
    if [ -z "$PASS" ]; then
        echo "No password for $(wallet_entry) in the KDE wallet: run ./run sftp-password first." >&2
        exit 1
    fi
}

# The login, as a curl config read on stdin (`curl --config -`): printf is a shell builtin, so the
# password never shows in the process list.
curl_auth() {
    escaped=$(printf '%s' "$DEPLOY_USER:$PASS" | sed 's/\\/\\\\/g; s/"/\\"/g')
    printf 'user = "%s"\n' "$escaped"
}

# The account's root as an SFTP address: under SFTP a single slash after the host is already the
# filesystem's root.
remote_base() {
    printf '%s/%s/' "${DEPLOY_URL%/}" "${DEPLOY_REMOTE_ROOT#/}"
}
