<?php
/**
 * Lists, one per line, every capture and video layout.json declares that is not in public_html/
 * yet, and every tile of the zone's capture not framed yet; exits 1 when there is one.
 *
 *   php missing_media.php        what the deploy waits for (English, and French unless
 *                                layout.json's fr_media shows the English ones on the French page)
 *   php missing_media.php fr     what is missing in one language (./run site-shots fr)
 *
 * site/bin/build.sh runs it bare: the site is never sent with a frame still standing in.
 */

require __DIR__ . '/../private/lib.php';

$layout = readJson('layout.json');
$languages = isset($argv[1]) ? [$argv[1]] : requiredMediaLanguages($layout);
foreach ($languages as $lang) {
    if (!in_array($lang, MEDIA_LANGUAGES, true)) {
        fwrite(STDERR, "Unknown language $lang: " . implode(' or ', MEDIA_LANGUAGES) . "\n");
        exit(2);
    }
}
$missing = missingMedia($layout, $languages);
foreach ($missing as $line) {
    echo $line, "\n";
}
exit($missing ? 1 : 0);
