#!/bin/bash
# Runs inside registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie, as root, with the
# fdroiddata clone mounted at /fdroiddata and this folder at /recipe (docs/design/fdroid-compliance.md
# gives the command). Mirrors the build job of fdroiddata's .gitlab-ci.yml for the one app, so the
# verdict is the one their CI would give: the recipe's lint, the scanner, the build from the tag.
set -ex
app=app.treelune
cd /fdroiddata
cp /recipe/$app.yml metadata/$app.yml
chown -R "$(whoami)" .
test -d build || mkdir build
apt-get update
test -n "$fdroidserver" || source /etc/profile.d/bsenv.sh
sdkmanager "platform-tools" "build-tools;31.0.0"
export GRADLE_USER_HOME=$home_vagrant/.gradle
fdroid="sudo --preserve-env --user vagrant env PATH=$fdroidserver:$PATH fdroid"
apt-get install -y sudo openjdk-21-jdk-headless
update-alternatives --set java /usr/lib/jvm/java-21-openjdk-amd64/bin/java
chown -R vagrant /fdroiddata
$fdroid lint $app || true
$fdroid fetchsrclibs $app --verbose
(unset CI; $fdroid build --verbose --test --refresh-scanner --on-server --no-tarball $app)
ls -l /fdroiddata/tmp/*.apk /fdroiddata/unsigned/*.apk 2>/dev/null || true
