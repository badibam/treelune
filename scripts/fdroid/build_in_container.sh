#!/bin/bash
# Runs inside registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie, as root, with the
# fdroiddata clone mounted at /fdroiddata and this folder at /recipe (docs/design/fdroid-compliance.md
# gives the command). Mirrors the "fdroid build" job of fdroiddata's .gitlab-ci.yml for the one app,
# so the verdict is the one their CI would give: the recipe's lint, the scanner, the build from the tag.
# The image carries no fdroidserver: like the job, this clones it at the commit fdroiddata pins.
set -ex
app=app.treelune
cd /fdroiddata
cp /recipe/$app.yml metadata/$app.yml
chown -R "$(whoami)" .
test -d build || mkdir build
apt-get update
test -n "$fdroidserver" || source /etc/profile.d/bsenv.sh
sdkmanager "platform-tools" "build-tools;31.0.0"

# fdroidserver at the commit pinned by fdroiddata's .gitlab-ci.yml (install_fdroid_server)
pin=$(grep -oP 'checkout -B master \K[0-9a-f]{40}' .gitlab-ci.yml | head -1)
rm -rf $fdroidserver
git clone --shallow-since=2026-07-13 https://gitlab.com/fdroid/fdroidserver.git $fdroidserver
git -C $fdroidserver checkout -B master $pin
git -C $fdroidserver pull origin master --ff-only
export serverwebroot=/tmp
git -C $home_vagrant/gradlew-fdroid pull

# the job builds from the vagrant home, the recipe and the source folders linked there
for d in logs tmp unsigned $home_vagrant/.android $home_vagrant/.gradle $home_vagrant/metadata; do
    test -d $d || mkdir $d
    chown -R vagrant $d
done
ln -sfn /fdroiddata/tmp $home_vagrant/tmp
ln -sfn /fdroiddata/srclibs $home_vagrant/srclibs

export GRADLE_USER_HOME=$home_vagrant/.gradle
fdroid="sudo --preserve-env --user vagrant
    env PATH=$fdroidserver:$PATH
    env PYTHONPATH=$fdroidserver:$fdroidserver/examples
    env PYTHONUNBUFFERED=true
    env HOME=$home_vagrant
    fdroid"

apt-get install -y sudo openjdk-21-jdk-headless
update-alternatives --set java /usr/lib/jvm/java-21-openjdk-amd64/bin/java

rm -rf $home_vagrant/build
cp -R /fdroiddata/build $home_vagrant/build
cp metadata/$app.yml $home_vagrant/metadata/
chown -R vagrant $home_vagrant /fdroiddata
(cd /fdroiddata && $fdroid lint $app) || true
cd $home_vagrant
ln -sfn /fdroiddata $home_vagrant/fdroiddata
$fdroid fetchsrclibs $app --verbose
rm $home_vagrant/fdroiddata
(unset CI; $fdroid build --verbose --test --refresh-scanner --on-server --no-tarball $app)
ls -l /fdroiddata/tmp/*.apk /fdroiddata/unsigned/*.apk 2>/dev/null || true
