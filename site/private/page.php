<?php
/**
 * The site's one page (docs/design/website.md), in the language $lang the caller sets: 'en' at /,
 * 'fr' at /fr/. Its words come from texts.php, its layout from layout.json, what it shows of the
 * app from generated.json.
 *
 * Every section is rendered here, its first choice included, so the page reads without its script;
 * site.js makes the choices change what the phone shows. A capture or a video declared and not yet
 * taken stands in locally, marked as such (an F-Droid capture of its theme, served by the local
 * router only, or an empty frame), and blocks the deploy.
 */

require_once __DIR__ . '/lib.php';

const SITE_URL = 'https://treelune.app';
const LANGUAGE_PATHS = ['en' => '/', 'fr' => '/fr/'];

$texts = (require __DIR__ . '/texts.php')[$lang];
// The captures and videos this page shows: its language's, or the English ones (layout.json fr_media)
$layout = readJson('layout.json');
$layout = localizeLayout($layout, mediaLanguage($layout, $lang));
$generated = readJson('generated.json');
$icons = $generated['icons'];

/** A text of this language, escaped for HTML; %s and {android} filled when asked. */
$t = function (string $path, string ...$values) use ($texts, $generated): string {
    $text = str_replace('{android}', $generated['android'], getSiteText($texts, $path));
    return h($values ? sprintf($text, ...$values) : $text);
};

$themeNames = [];
foreach ($generated['themes'] as $theme) {
    $themeNames[$theme['id']] = $theme['name'][$lang];
}
$fdroidShots = array_column($layout['themes'], 'fdroid', 'id');
$toolTypes = array_column($generated['tools'], null, 'id');
$otherLang = $lang === 'en' ? 'fr' : 'en';

/** The address a capture is shown from: its own file, or locally the F-Droid capture of its theme. */
function shotSource(string $file, string $theme, array $fdroidShots): array
{
    if (mediaPresent($file)) {
        return ['src' => asset($file), 'stand_in' => false];
    }
    return ['src' => '/placeholder/' . $fdroidShots[$theme] . '.png', 'stand_in' => true];
}

/** A note on a frame whose file is not taken yet; never deployed, the deploy refusing it. */
$standIn = fn(string $file): string => '<span class="stand-in">' . $t('common.stand_in', $file) . '</span>';

/** A capture in a phone frame, enlarged when touched. */
$phoneShot = function (array $shot, string $alt) use ($fdroidShots, $standIn): string {
    $source = shotSource($shot['file'], $shot['theme'], $fdroidShots);
    return '<div class="phone"><img class="zoomable" src="' . h($source['src']) . '" alt="' . $alt . '" loading="lazy">'
        . ($source['stand_in'] ? $standIn($shot['file']) : '') . '</div>';
};

/** A video in its frame, a phone or a computer, silent and looping, enlarged when touched. */
$videoFrame = function (array $video) use ($standIn): string {
    $frame = $video['frame'] === 'computer' ? 'computer' : 'phone';
    if (!mediaPresent($video['file']) || !mediaPresent($video['poster'])) {
        $file = mediaPresent($video['file']) ? $video['poster'] : $video['file'];
        return '<div class="' . $frame . '"><div class="empty"></div>' . $standIn($file) . '</div>';
    }
    return '<div class="' . $frame . '"><video class="zoomable" src="' . h(asset($video['file'])) . '" poster="'
        . h(asset($video['poster'])) . '" autoplay muted loop playsinline preload="metadata"></video></div>';
};

/**
 * The site's pattern: choices beside a phone, choosing changes what the phone shows. Each choice
 * carries its pill, its name, an optional tag, its text and theme, and either its own stage or the
 * tile it outlines on a shared stage.
 */
function explorer(string $set, array $items, string $stage, callable $t): string
{
    $first = $items[0];
    $list = '';
    foreach ($items as $i => $item) {
        $tag = $item['tag'] !== '' ? '<small>' . h($item['tag']) . '</small>' : '';
        $list .= '<button type="button" data-index="' . $i . '" aria-pressed="' . ($i === 0 ? 'true' : 'false') . '">'
            . $item['pill'] . '<span>' . h($item['name']) . $tag . '</span></button>';
    }
    $templates = '';
    foreach ($items as $i => $item) {
        if ($item['stage'] !== null) {
            $templates .= '<template data-stage="' . $i . '">' . $item['stage'] . '</template>';
        }
    }
    $data = array_map(fn($item) => [
        'name' => $item['name'], 'pill' => $item['pill'], 'text' => $item['text'],
        'theme' => $item['theme'], 'tile' => $item['tile'],
    ], $items);
    return '<div class="explorer" data-set="' . h($set) . '">'
        . '<div class="ex-side">'
        . '<div class="carousel">'
        . '<button type="button" class="arrow" data-step="-1" aria-label="' . $t('common.previous') . '">‹</button>'
        . '<div class="current">' . $first['pill'] . '<b>' . h($first['name']) . '</b><span class="rank">1 / ' . count($items) . '</span></div>'
        . '<button type="button" class="arrow" data-step="1" aria-label="' . $t('common.next') . '">›</button>'
        . '</div>'
        . '<div class="ex-list">' . $list . '</div>'
        . '<div class="ex-caption" aria-live="polite"><h3>' . h($first['name']) . '</h3><p>' . h($first['text']) . '</p>'
        . '<span class="theme-label">' . h($first['theme']) . '</span></div>'
        . '</div>'
        . '<div class="ex-stage">' . $stage . '</div>'
        . $templates
        . '<script type="application/json" class="ex-items">'
        . json_encode($data, JSON_HEX_TAG | JSON_HEX_AMP | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR)
        . '</script>'
        . '</div>';
}

$plainTheme = fn(?string $theme): string => $theme ? sprintf(getSiteText($texts, 'common.theme'), $themeNames[$theme]) : '';

// 3. The Italian zone: its tools and its routine, outlined on one capture
$zone = $generated['zone'];
$zoneShot = $layout['zone']['shot'];
$zoneItems = [];
foreach ($zone['tools'] as $tool) {
    $zoneItems[] = [
        'name' => $tool['name'][$lang],
        'tag' => $toolTypes[$tool['type']]['name'][$lang],
        'pill' => pill($icons, $tool['icon'], $tool['color'] ?? $layout['zone']['colors'][$tool['id']]),
        'text' => getSiteText($texts, 'zone.' . $tool['id']),
        'theme' => $plainTheme($zoneShot['theme']),
        'stage' => null,
        'tile' => $tool['id'],
    ];
}
$routine = $zone['routine'];
$zoneItems[] = [
    'name' => $routine['name'][$lang],
    'tag' => getSiteText($texts, 'zone.routine'),
    'pill' => pill($icons, $routine['icon'], $routine['color']),
    'text' => getSiteText($texts, 'zone.' . $routine['id']),
    'theme' => $plainTheme($zoneShot['theme']),
    'stage' => null,
    'tile' => null,
];
$tiles = array_filter($layout['zone']['tiles']);
if (mediaPresent($zoneShot['file']) && count($tiles) === count($layout['zone']['tiles'])) {
    // The capture, each tile a button over it: touching one chooses its tool. Never enlarged.
    $hits = '';
    foreach ($zone['tools'] as $tool) {
        [$left, $top, $width, $height] = $layout['zone']['tiles'][$tool['id']];
        $hits .= sprintf(
            '<button type="button" class="tile-hit" data-tile="%s" aria-label="%s" style="left:%.3f%%;top:%.3f%%;width:%.3f%%;height:%.3f%%"></button>',
            h($tool['id']), h($tool['name'][$lang]), $left * 100, $top * 100, $width * 100, $height * 100
        );
    }
    $zoneStage = '<div class="phone"><div class="zone-frame"><img src="' . h(asset($zoneShot['file'])) . '" alt="'
        . $t('zone.shot_alt') . '">' . $hits . '</div></div>';
} else {
    // Not taken yet: the zone drawn from the demo, its tiles touchable the same way.
    $drawn = '';
    foreach ($zone['tools'] as $tool) {
        $drawn .= '<button type="button" class="tile" data-tile="' . h($tool['id']) . '">' . h($tool['name'][$lang])
            . '<small>' . h($toolTypes[$tool['type']]['name'][$lang]) . '</small></button>';
    }
    $zoneStage = '<div class="phone"><div class="zone-screen"><h4>' . h($zone['name'][$lang]) . '</h4><div class="tiles">'
        . $drawn . '</div></div>' . $standIn($zoneShot['file']) . '</div>';
}

// 4. In action: one video at a time, in a phone, or in a computer for the outside AI
$actionItems = [];
foreach ($layout['videos'] as $video) {
    $actionItems[] = [
        'name' => getSiteText($texts, "action.{$video['id']}.name"),
        'tag' => '',
        'pill' => pill($icons, $video['icon'], $video['color']),
        'text' => getSiteText($texts, "action.{$video['id']}.text"),
        'theme' => $plainTheme($video['theme']),
        'stage' => $videoFrame($video),
        'tile' => null,
    ];
}

// 5. The tool types: each one's capture and tagline
$toolItems = [];
foreach ($layout['tools'] as $tool) {
    $type = $toolTypes[$tool['id']];
    $toolItems[] = [
        'name' => $type['name'][$lang],
        'tag' => '',
        'pill' => pill($icons, $type['icon'], $tool['color']),
        'text' => $type['tagline'][$lang],
        'theme' => $plainTheme($tool['shot']['theme']),
        'stage' => $phoneShot($tool['shot'], $t('tools.shot_alt', $type['name'][$lang])),
        'tile' => null,
    ];
}

// 1. The opening band: each theme's ground and ink in both modes, its font, its two home captures
$bandThemes = [];
foreach ($layout['themes'] as $theme) {
    $shots = [];
    foreach (['light', 'dark'] as $mode) {
        $file = homeShot($theme['id'], $mode, $layout['media_language']);
        $shots[$mode] = shotSource($file, $theme['id'], $fdroidShots) + ['file' => $file];
    }
    $bandThemes[] = [
        'id' => $theme['id'], 'name' => $themeNames[$theme['id']], 'family' => $theme['family'],
        'light' => $theme['light'], 'dark' => $theme['dark'], 'grid' => $theme['grid'] ?? false,
        'shots' => $shots, 'alt' => sprintf(getSiteText($texts, 'band.shot_alt'), $themeNames[$theme['id']]),
    ];
}
$bandFirst = array_values(array_filter($bandThemes, fn($theme) => $theme['id'] === $layout['band_theme']))[0];
$bandIndex = array_search($bandFirst, $bandThemes, true);
$fontFamily = fn(string $family): string => $family === 'system-ui' ? 'system-ui, sans-serif' : '"' . $family . '", system-ui, sans-serif';

$links = $layout['links'];

// The app's mark, drawn by scripts/make_launcher_icon.py from the launcher icon's shapes, made a
// symbol the page reuses small and large; the file is trusted, written by that script.
preg_match('#<svg[^>]*viewBox="([^"]+)"[^>]*>(.*)</svg>#s', file_get_contents(SITE_PUBLIC . '/mark.svg'), $mark);
$markSymbol = '<symbol id="mark" viewBox="' . h($mark[1]) . '">' . $mark[2] . '</symbol>';
?>
<!doctype html>
<html lang="<?= h($lang) ?>">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title><?= $t('meta.title') ?></title>
<meta name="description" content="<?= $t('meta.description') ?>">
<link rel="canonical" href="<?= h(SITE_URL . LANGUAGE_PATHS[$lang]) ?>">
<?php foreach (LANGUAGE_PATHS as $code => $path): ?>
<link rel="alternate" hreflang="<?= h($code) ?>" href="<?= h(SITE_URL . $path) ?>">
<?php endforeach ?>
<link rel="alternate" hreflang="x-default" href="<?= h(SITE_URL . LANGUAGE_PATHS['en']) ?>">
<link rel="icon" href="<?= h(asset('mark.svg')) ?>" type="image/svg+xml">
<script>
  // The mode the visitor chose before, set before the first paint so the page never flashes
  try { var m = localStorage.getItem("mode"); if (m === "light" || m === "dark") document.documentElement.dataset.theme = m; } catch (e) {}
</script>
<style>
  /* The phone's screen, width over height: the captures' own proportion (layout.json "screen") */
  :root { --screen: calc(<?= (int) $layout['screen'][0] ?> / <?= (int) $layout['screen'][1] ?>); }
<?php foreach ($layout['themes'] as $theme): if ($theme['font']): ?>
  @font-face { font-family: "<?= h($theme['family']) ?>"; src: url("<?= h(asset('fonts/' . $theme['font'])) ?>") format("truetype"); font-display: swap; }
<?php endif; endforeach ?>
</style>
<link rel="stylesheet" href="<?= h(asset('site.css')) ?>">
</head>
<body>

<svg width="0" height="0" style="position:absolute" aria-hidden="true">
  <defs><?= $markSymbol ?></defs>
</svg>

<header>
  <div class="wrap">
    <a class="brand" id="brand" href="<?= h(LANGUAGE_PATHS[$lang]) ?>"><svg width="28" height="28" aria-hidden="true"><use href="#mark"/></svg><span class="brand-name">Treelune</span><span class="where" id="where" aria-hidden="true"></span><span class="where-rank" id="where-rank" aria-hidden="true"></span></a>
    <!-- Every section but the opening's pitch, which the brand leads back to; on a phone, Install and the other language only -->
    <nav id="nav">
      <a href="#showcase"><?= $t('nav.themes') ?></a>
      <a href="#data"><?= $t('nav.data') ?></a>
      <a href="#zone"><?= $t('nav.discover') ?></a>
      <a href="#action"><?= $t('nav.action') ?></a>
      <a href="#tools"><?= $t('nav.tools') ?></a>
      <a href="#pieces"><?= $t('nav.more') ?></a>
      <a class="always" href="#install"><?= $t('nav.install') ?></a>
      <a class="always" href="<?= h(LANGUAGE_PATHS[$otherLang]) ?>" hreflang="<?= h($otherLang) ?>" lang="<?= h($otherLang) ?>" title="<?= $t('meta.other_language') ?>"><?= h(strtoupper($otherLang)) ?></a>
    </nav>
  </div>
</header>

<main>
<!-- 1. Opening -->
<div class="band" id="band" style="background: <?= h($bandFirst['light'][0]) ?>; color: <?= h($bandFirst['light'][1]) ?>; --band-bg: <?= h($bandFirst['light'][0]) ?>">
  <div class="wrap hero">
    <div class="pitch">
      <div class="pitch-mark"><svg aria-hidden="true"><use href="#mark"/></svg><span>Treelune</span></div>
      <h1><?= $t('band.pitch') ?></h1>
      <p class="ai"><?= $t('band.ai') ?></p>
      <div class="buttons"><a class="btn" href="#install"><?= $t('band.install') ?></a></div>
      <button type="button" class="more" id="more" aria-label="<?= $t('band.more') ?>"><span aria-hidden="true">↓</span></button>
    </div>
    <div class="showcase" id="showcase">
      <div class="phone-box"><div class="phone"><img class="zoomable" id="band-shot" src="<?= h($bandFirst['shots']['light']['src']) ?>" alt="<?= h($bandFirst['alt']) ?>"><?php if ($bandFirst['shots']['light']['stand_in']): ?><?= $standIn($bandFirst['shots']['light']['file']) ?><?php endif ?></div></div>
      <div class="picker" id="picker" role="group" aria-label="<?= $t('band.picker') ?>">
        <p class="picker-intro"><?= $t('band.picker_intro') ?></p>
        <div class="carousel" id="band-carousel">
          <button type="button" class="arrow" data-step="-1" aria-label="<?= $t('band.previous') ?>">‹</button>
          <div class="current"><b id="band-current" style="font-family: <?= h($fontFamily($bandFirst['family'])) ?>"><?= h($bandFirst['name']) ?></b><span class="rank" id="band-rank"><?= $bandIndex + 1 ?> / <?= count($bandThemes) ?></span></div>
          <button type="button" class="arrow" data-step="1" aria-label="<?= $t('band.next') ?>">›</button>
        </div>
        <div class="picker-list" id="picker-list">
<?php foreach ($bandThemes as $theme): ?>
          <button type="button" data-id="<?= h($theme['id']) ?>" aria-pressed="<?= $theme === $bandFirst ? 'true' : 'false' ?>" style="font-family: <?= h($fontFamily($theme['family'])) ?>"><?= h($theme['name']) ?></button>
<?php endforeach ?>
        </div>
        <div class="mode-switch" id="mode-switch" role="group" aria-label="<?= $t('band.mode') ?>">
          <button type="button" data-mode="light"><span><?= $t('band.light') ?></span></button>
          <button type="button" data-mode="dark"><span><?= $t('band.dark') ?></span></button>
        </div>
      </div>
    </div>
  </div>
  <script type="application/json" id="band-themes"><?= json_encode(['themes' => $bandThemes, 'first' => $bandIndex, 'standIn' => getSiteText($texts, 'common.stand_in')], JSON_HEX_TAG | JSON_HEX_AMP | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR) ?></script>
</div>

<!-- 2. Three lines on the data -->
<section class="privacy" id="data">
  <div class="wrap">
    <h2><?= $t('privacy.title') ?></h2>
    <ul>
<?php foreach ($layout['privacy'] as $item): ?>
      <li><?= pill($icons, $item['icon'], $item['color']) ?><div><b><?= $t("privacy.{$item['id']}.title") ?></b><span><?= $t("privacy.{$item['id']}.text") ?></span></div></li>
<?php endforeach ?>
    </ul>
  </div>
</section>

<!-- 3. The Italian zone, tool by tool -->
<section data-explore id="zone">
  <div class="wrap">
    <h2><?= $t('zone.title') ?></h2>
    <p class="lead"><?= $t('zone.lead') ?></p>
    <?= explorer('zone', $zoneItems, $zoneStage, $t) ?>
  </div>
</section>

<!-- 4. In action -->
<section data-explore id="action" class="flip">
  <div class="wrap">
    <h2><?= $t('action.title') ?></h2>
    <p class="lead"><?= $t('action.lead') ?></p>
    <?= explorer('action', $actionItems, $actionItems[0]['stage'], $t) ?>
  </div>
</section>

<!-- 5. The tools, then the other pieces -->
<section data-explore id="tools">
  <div class="wrap">
    <h2><?= $t('tools.title') ?></h2>
    <p class="lead"><?= $t('tools.lead') ?></p>
    <?= explorer('tools', $toolItems, $toolItems[0]['stage'], $t) ?>
  </div>
</section>

<section class="pieces" id="pieces">
  <div class="wrap">
    <h2><?= $t('tools.pieces_title') ?></h2>
    <div class="grid">
<?php foreach ($layout['pieces'] as $piece): ?>
      <div class="card"><h3><?= pill($icons, $piece['icon'], $piece['color']) ?><?= $t("pieces.{$piece['id']}.name") ?></h3><p><?= $t("pieces.{$piece['id']}.text") ?></p></div>
<?php endforeach ?>
    </div>
    <div class="carousel pieces-carousel">
      <button type="button" class="arrow" data-step="-1" aria-label="<?= $t('common.previous') ?>">‹</button>
      <div class="current"><span class="rank">1 / <?= count($layout['pieces']) ?></span></div>
      <button type="button" class="arrow" data-step="1" aria-label="<?= $t('common.next') ?>">›</button>
    </div>
  </div>
</section>

<!-- 6. Install, the footer at the bottom of its screen -->
<section id="install" class="install">
  <div class="wrap install-body">
    <div class="signature"><svg width="120" height="120" aria-hidden="true"><use href="#mark"/></svg></div>
    <h2><?= $t('install.title') ?></h2>
    <p class="lead"><?= $t('install.lead') ?></p>
    <ul class="needs">
      <li><?= $t('install.android') ?></li>
      <li><?= $t('install.arm64') ?></li>
    </ul>
    <div class="buttons">
      <a class="btn" href="<?= h($links['fdroid']) ?>"><?= $t('install.fdroid') ?></a>
      <a class="btn ghost" href="<?= h($links['apk']) ?>"><?= $t('install.apk') ?></a>
    </div>
    <p class="young"><?= $t('install.young') ?></p>
  </div>
  <footer>
    <div class="wrap">
      <span><?= $t('footer.licence') ?></span>
      <span><a href="<?= h($links['source']) ?>"><?= $t('footer.source') ?></a> · <a href="<?= h($links['bug']) ?>"><?= $t('footer.bug') ?></a> · <a href="<?= h(LANGUAGE_PATHS[$otherLang]) ?>" hreflang="<?= h($otherLang) ?>" lang="<?= h($otherLang) ?>"><?= $t('meta.other_language') ?></a></span>
    </div>
  </footer>
</section>
</main>

<dialog id="lightbox" aria-label="<?= $t('common.enlarge') ?>">
  <button type="button" class="lb-close" aria-label="<?= $t('common.close') ?>">×</button>
  <div class="lb-body"></div>
</dialog>

<script src="<?= h(asset('site.js')) ?>"></script>
</body>
</html>
