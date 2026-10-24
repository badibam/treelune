#!/bin/sh
# Send the site to the host over SFTP, with curl alone: build tmp/site-deploy/ (site/bin/build.sh,
# which refuses a site not ready to be seen), then upload the files whose bytes changed since the
# last deploy. Host and login in site/bin/deploy_config.sh, out of git; the password in the KDE
# wallet (site/bin/wallet.sh).
#
#   --dry-run  list what would be sent, send nothing
#   --all      send every file, the record of the last deploy ignored
#   --yes      no confirmation asked
set -eu

root=$(cd "$(dirname "$0")/../.." && pwd)
bin="$root/site/bin"
out="$root/tmp/site-deploy"

dry=''
send_all=''
assume_yes=''
for arg in "$@"; do
    case "$arg" in
        --dry-run) dry=1 ;;
        --all) send_all=1 ;;
        --yes) assume_yes=1 ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

if [ ! -r "$bin/deploy_config.sh" ]; then
    echo "No site/bin/deploy_config.sh: copy deploy_config.sh.example and fill it." >&2
    exit 1
fi
. "$bin/deploy_config.sh"
. "$bin/wallet.sh"
wallet_password

"$bin/build.sh"
base=$(remote_base)
files=$(cd "$out" && find . -type f | sed 's|^\./||' | sort)

# What was last sent, as a digest per file, kept beside the site and out of every served folder.
# Digests rather than dates say what actually differs: a rebuilt file with the same bytes is no
# change, and a clock that disagrees between two machines decides nothing.
manifest="$root/tmp/site-deploy.manifest"
manifest_remote="$root/tmp/site-deploy.manifest.remote"
manifest_url="${base}private/deploy-manifest.txt"
: > "$manifest"
for file in $files; do
    printf '%s %s\n' "$(sha256sum "$out/$file" | cut -d' ' -f1)" "$file" >> "$manifest"
done
rm -f "$manifest_remote"
if [ -z "$send_all" ]; then
    # No record yet is the ordinary state of a first deploy: everything is sent.
    curl_auth | curl --silent --fail --config - --output "$manifest_remote" "$manifest_url" 2>/dev/null || rm -f "$manifest_remote"
fi

to_send=''
skipped=0
for file in $files; do
    if [ -f "$manifest_remote" ]; then
        local_hash=$(awk -v f="$file" '$2 == f { print $1 }' "$manifest")
        remote_hash=$(awk -v f="$file" '$2 == f { print $1 }' "$manifest_remote")
        if [ -n "$remote_hash" ] && [ "$local_hash" = "$remote_hash" ]; then
            skipped=$((skipped + 1))
            continue
        fi
    fi
    to_send="$to_send $file"
done
count=$(printf '%s\n' $to_send | grep -c . || true)

echo "Sending $count files to $base"
if [ "$skipped" -gt 0 ]; then
    echo "  $skipped unchanged since the last deploy, skipped (--all sends them again)."
fi
if [ -n "$dry" ]; then
    printf '%s\n' $to_send | sed 's/^/  /'
    exit 0
fi
if [ "$count" -eq 0 ]; then
    echo "Nothing to send: the server already has these $skipped files."
    exit 0
fi
if [ -z "$assume_yes" ]; then
    printf 'Send? [y/N] '
    read -r answer
    case "$answer" in
        y|Y|o|O) ;;
        *) echo 'Cancelled.'; exit 0 ;;
    esac
fi

sent=0
for file in $to_send; do
    if curl_auth | curl --silent --show-error --fail --ftp-create-dirs --config - \
            --upload-file "$out/$file" "$base$file"; then
        sent=$((sent + 1))
        printf '  %s\n' "$file"
    else
        printf '  FAILED  %s\n' "$file" >&2
        # Every file goes to the same server the same way: what stopped the first stops them all,
        # and the same error repeated for each would bury it.
        echo "Stopped at the first failure, $sent files sent before it." >&2
        echo "If the error is about the host key: connect once by hand to check its fingerprint and record it in ~/.ssh/known_hosts" >&2
        echo "  sftp $DEPLOY_USER@$(echo "$DEPLOY_URL" | sed 's|^sftp://||; s|/.*||')" >&2
        exit 1
    fi
done

# The record is written only once everything else arrived: a partial send leaves the old one, so
# the next run repeats it rather than believing in files it never delivered.
curl_auth | curl --silent --show-error --fail --ftp-create-dirs --config - \
    --upload-file "$manifest" "$manifest_url" \
    || echo "Sent, but the record could not be written: the next deploy will send everything again." >&2

echo "$sent files sent."
