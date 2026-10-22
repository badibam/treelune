<?php
/**
 * What page.php and the site's command-line checks share: reading the texts and the data, escaping,
 * and the list of every capture and video the page declares.
 */

const SITE_PRIVATE = __DIR__;
const SITE_PUBLIC = __DIR__ . '/../public_html';

/** A JSON file of private/, decoded; a file that does not decode fails the page. */
function readJson(string $name): array
{
    return json_decode(file_get_contents(SITE_PRIVATE . '/' . $name), true, 512, JSON_THROW_ON_ERROR);
}

/**
 * A text by its dotted path in one language's texts ('privacy.no_account.title'). A key missing
 * fails the page: a text left empty or shown in the other language would go unseen.
 */
function getSiteText(array $texts, string $path): string
{
    $node = $texts;
    foreach (explode('.', $path) as $key) {
        if (!is_array($node) || !array_key_exists($key, $node)) {
            throw new RuntimeException("No site text $path");
        }
        $node = $node[$key];
    }
    if (!is_string($node)) {
        throw new RuntimeException("The site text $path is not a text");
    }
    return $node;
}

function h(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES, 'UTF-8');
}

/**
 * A Lucide icon on a pill of its tag colour, as the default theme draws a tool's icon. The drawing
 * comes from generated.json, copied from third_party/lucide: trusted, written as it is.
 */
function pill(array $icons, string $name, ?string $color): string
{
    if (!isset($icons[$name])) {
        throw new RuntimeException("No icon $name in generated.json: ./scripts/make_site_data.py");
    }
    $style = $color ? ' style="--c: var(--' . h($color) . ')"' : '';
    return '<span class="pill"' . $style . '><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" '
        . 'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' . $icons[$name] . '</svg></span>';
}

/** The languages captures and videos are taken in, the order they are taken in. */
const MEDIA_LANGUAGES = ['en', 'fr'];

/**
 * The language whose captures a page shows: its own, but the English ones on the French page while
 * layout.json declares "fr_media": "en" (the French ones not taken yet).
 */
function mediaLanguage(array $layout, string $lang): string
{
    $frMedia = $layout['fr_media'] ?? null;
    if ($frMedia !== null && $frMedia !== 'en') {
        throw new RuntimeException('layout.json: fr_media is "en" or absent');
    }
    return $lang === 'fr' && $frMedia === 'en' ? 'en' : $lang;
}

/** The languages the deploy waits for: English, and French unless its page shows the English ones. */
function requiredMediaLanguages(array $layout): array
{
    return mediaLanguage($layout, 'fr') === 'fr' ? MEDIA_LANGUAGES : ['en'];
}

/** A file of layout.json in one language's folder: shots/x.webp → shots/en/x.webp. */
function localized(string $file, string $lang): string
{
    return dirname($file) . '/' . $lang . '/' . basename($file);
}

/** layout.json with every capture's and video's path in one language's folder. */
function localizeLayout(array $layout, string $lang): array
{
    $layout['zone']['shot']['file'] = localized($layout['zone']['shot']['file'], $lang);
    foreach ($layout['videos'] as &$video) {
        $video['file'] = localized($video['file'], $lang);
        $video['poster'] = localized($video['poster'], $lang);
    }
    unset($video);
    foreach ($layout['tools'] as &$tool) {
        $tool['shot']['file'] = localized($tool['shot']['file'], $lang);
    }
    unset($tool);
    $layout['media_language'] = $lang;
    return $layout;
}

/** The home screen's capture of a theme in a mode: the only captures taken in both modes. */
function homeShot(string $theme, string $mode, string $lang): string
{
    return localized("shots/home-$theme-$mode.webp", $lang);
}

/**
 * Every file the page shows in one language, published path → what it is, in the page's order. A
 * file of this list missing from public_html/ stands in locally and blocks the deploy.
 */
function declaredMedia(array $layout, string $lang): array
{
    $layout = localizeLayout($layout, $lang);
    $media = [];
    foreach ($layout['themes'] as $theme) {
        foreach (['light', 'dark'] as $mode) {
            $media[homeShot($theme['id'], $mode, $lang)] = "home screen, theme {$theme['id']}, $mode";
        }
    }
    $zone = $layout['zone']['shot'];
    $media[$zone['file']] = "the Italian zone, theme {$zone['theme']}, {$zone['mode']}";
    foreach ($layout['videos'] as $video) {
        $where = $video['theme'] ? "theme {$video['theme']}, {$video['mode']}" : 'the outside AI\'s screen';
        $media[$video['file']] = "video {$video['id']}, $where";
        $media[$video['poster']] = "first image of the video {$video['id']}";
    }
    foreach ($layout['tools'] as $tool) {
        $media[$tool['shot']['file']] = "tool {$tool['id']}, theme {$tool['shot']['theme']}, {$tool['shot']['mode']}";
    }
    return $media;
}

function mediaPresent(string $file): bool
{
    return is_file(SITE_PUBLIC . '/' . $file);
}

/**
 * What is missing in the given languages: each declared file, and each tile of the zone's capture
 * not yet framed (once, on the English capture), as one line each.
 */
function missingMedia(array $layout, array $languages): array
{
    $missing = [];
    foreach ($languages as $lang) {
        foreach (declaredMedia($layout, $lang) as $file => $what) {
            if (!mediaPresent($file)) {
                $missing[] = "$file  ($what)";
            }
        }
    }
    foreach ($layout['zone']['tiles'] as $tool => $frame) {
        if ($frame === null) {
            $missing[] = "layout.json zone.tiles.$tool  (its tile's frame on the zone's capture)";
        }
    }
    return $missing;
}

/** A published file's address, marked with its content so that a browser never keeps an old one. */
function asset(string $file): string
{
    return '/' . $file . '?v=' . substr(md5_file(SITE_PUBLIC . '/' . $file), 0, 8);
}
