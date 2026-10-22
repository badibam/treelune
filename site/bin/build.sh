#!/bin/sh
# Assemble, under tmp/site-deploy/, exactly what goes onto the host, and nothing else: the
# committed files of site/public_html/ and site/private/, laid out as on the account's root, where
# public_html/ is the served folder and private/ sits beside it, out of every served folder.
#
# Refused while the site is not ready to be seen: a declared capture or video missing, the data
# drawn from the app stale, or a file of the site changed and not committed (what is sent is what
# git holds).
set -eu

root=$(cd "$(dirname "$0")/../.." && pwd)
out="$root/tmp/site-deploy"
cd "$root"

if ! missing=$(php site/bin/missing_media.php); then
    echo "Not ready to send: $(printf '%s\n' "$missing" | grep -c .) captures, videos or tile frames are missing (./run site-shots):" >&2
    printf '%s\n' "$missing" | sed 's/^/  /' >&2
    exit 1
fi
if ! stale=$(./scripts/make_site_data.py --check); then
    echo "Not ready to send, the site's data is stale:" >&2
    printf '%s\n' "$stale" | sed 's/^/  /' >&2
    exit 1
fi
changed=$(git status --porcelain --untracked-files=all -- site/public_html site/private)
if [ -n "$changed" ]; then
    echo "Not ready to send: commit the site's files first, what is sent is what git holds:" >&2
    printf '%s\n' "$changed" | sed 's/^/  /' >&2
    exit 1
fi

rm -rf "$out.part" "$out"
mkdir -p "$out.part"
git ls-files -- site/public_html site/private | while IFS= read -r file; do
    target="$out.part/${file#site/}"
    mkdir -p "$(dirname "$target")"
    cp "$file" "$target"
done
mv "$out.part" "$out"

echo "Ready to send: tmp/site-deploy/ ($(find "$out" -type f | wc -l) files, $(du -sh "$out" | cut -f1))"
