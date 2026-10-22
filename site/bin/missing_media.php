<?php
/**
 * Lists, one per line, every capture and video layout.json declares that is not in public_html/
 * yet, and every tile of the zone's capture not framed yet; exits 1 when there is one.
 * site/bin/build.sh runs it: the site is never sent with a frame still standing in.
 */

require __DIR__ . '/../private/lib.php';

$missing = missingMedia(readJson('layout.json'));
foreach ($missing as $line) {
    echo $line, "\n";
}
exit($missing ? 1 : 0);
