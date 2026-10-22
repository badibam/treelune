<?php

/**
 * Router for the local `php -S` mirror (./run site).
 *
 * It adds one thing to what Apache serves: /placeholder/<n>.png, the F-Droid captures that stand in
 * for a capture not taken yet. They live in fastlane/, out of the served folder, and never reach
 * the host: the deploy refuses while a capture is missing. Returning false hands every other
 * request back to the built-in server, which serves it as Apache would.
 */

$path = (string) parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH);

if (preg_match('#^/placeholder/([1-9][0-9]?)\.png$#', $path, $m)) {
    $file = __DIR__ . '/../../fastlane/metadata/android/en-US/images/phoneScreenshots/' . $m[1] . '.png';
    if (!is_file($file)) {
        http_response_code(404);
        return true;
    }
    header('Content-Type: image/png');
    readfile($file);
    return true;
}

return false;
