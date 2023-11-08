#!/usr/bin/env bash
function finish() { set +x ; }
trap finish EXIT

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
if [[ $(basename "$SCRIPTDIR") != "buildLogic" ]]; then echo "sync.sh is not inside a 'buildLogic/' folder" >&2 ; exit 4 ; fi
if [[ ! -f "$SCRIPTDIR/../settings.gradle.kts" ]]; then echo "you seem to be in the 'reference' buildLogic/ folder; Calling sync.sh there makes no sense!" ; exit 3 ; fi
SYNCDIR_base=${GRADLE_BUILDLOGIC_DIR:-$SCRIPTDIR/../../buildLogic}
if [[ ! -d $SYNCDIR_base ]]; then echo "SYNCDIR_base dir '$SYNCDIR_base' does not exist! (consider setting env var GRADLE_BUILDLOGIC_DIR)" >&2 ; exit 1 ; fi
SYNCDIR_base=$(realpath "$SYNCDIR_base")
echo "SYNCDIR_base=$SYNCDIR_base" >&2

OPT_SYNC_DIRECTION="DIFF"
if [[ -n $1 && $1 == "diffback" || $1 == "back" ]]; then OPT_SYNC_DIRECTION="DIFFBACK" ; fi
if [[ -n $1 && $1 == "overwrite" ]]; then                OPT_SYNC_DIRECTION="OVERWRITE" ; fi
if [[ -n $1 && $1 == "overwriteback" ]]; then            OPT_SYNC_DIRECTION="OVERWRITEBACK" ; fi

if [[ "OVERWRITE" == "$OPT_SYNC_DIRECTION" ]]; then
    SYNC_SOURCE="$SYNCDIR_base"
    SYNC_TARGET="$SCRIPTDIR"
    cp -f "$SYNC_SOURCE/libs.versions.toml"  "$SYNC_TARGET/libs.versions.toml"
elif [[ "OVERWRITEBACK" == "$OPT_SYNC_DIRECTION" ]]; then
    SYNC_SOURCE="$SCRIPTDIR"
    SYNC_TARGET="$SYNCDIR_base"
    cp -f "$SYNC_SOURCE/libs.versions.toml" "$SYNC_TARGET/libs.versions.toml"
fi

if [[ "OVERWRITE" == "$OPT_SYNC_DIRECTION" || "OVERWRITEBACK" == "$OPT_SYNC_DIRECTION" ]]; then
    cp -rf \
      "$SYNC_SOURCE/binaryPlugins" \
      "$SYNC_SOURCE/src" \
      "$SYNC_SOURCE/build.gradle.kts" \
      "$SYNC_SOURCE/settings.gradle.kts" \
      "$SYNC_SOURCE/sync.sh" \
      \
      "$SYNC_TARGET/"

      exit $? # for SYNC OVERWRITE EXITS HERE!
fi

ERROR_MESSAGES=()
#if [[   -f "$SCRIPTDIR/libs.versions.toml" ]]; then           ERROR_MESSAGES+=( "libs.versions.toml should be inside 'ROOT/gradle/' folder!" ) ; fi
if [[   -f "$SCRIPTDIR/../gradle/libs.versions.toml" ]]; then ERROR_MESSAGES+=( "libs.versions.toml file should be directly in topmost rootProject (and not inside ROOT/gradle/ folder!)" ) ; fi
if [[ ! -f "$SYNCDIR_base/libs.versions.toml" ]]; then        ERROR_MESSAGES+=( "no libs.versions.toml file in SYNC/libs.versions.toml" ) ; fi
if [[   -f "$SYNCDIR_base/gradle/libs.versions.toml" ]]; then ERROR_MESSAGES+=( "there shouldn't be a libs.versions.toml found in SYNC/gradle/libs.versions.toml" ) ; fi
if [[ ${#ERROR_MESSAGES[@]} -gt 0 ]]; then printf '%s\n' "${ERROR_MESSAGES[@]}" >&2 ; exit 2 ; fi

echo
echo "checking existance of binaryPlugins/..."
DIFF_MESSAGES=()

readarray -t SOURCE_BINARYPLUGINS < <(ls -1 "$SYNCDIR_base/binaryPlugins")
readarray -t TARGET_BINARYPLUGINS < <(ls -1 "$SCRIPTDIR/binaryPlugins")
# find the diffs of both arrays
readarray -t MISSING_BINARYPLUGINS < <(printf '%s\n' "${SOURCE_BINARYPLUGINS[@]}" "${TARGET_BINARYPLUGINS[@]}" | sort | uniq -u)
for MISSING in "${MISSING_BINARYPLUGINS[@]}"; do
    m="\033[0;31mbinaryPlugins/$MISSING does not exist\033[0m "
    if [[ -d "$SYNCDIR_base/binaryPlugins/$MISSING" ]]; then m+="here" ; else m+="in SYNC/buildLogic/" ; fi
    DIFF_MESSAGES+=( "$m" )
done
if [[ ${#DIFF_MESSAGES[@]} -gt 0 ]]; then printf "%s\n" "${DIFF_MESSAGES[@]}" ; else echo -e "\033[0;32mok\033[0m" ; fi

# $1 = projectFile, $2 = syncdirFile, $3 = displayFilename
function doTheDiff() {
    if [[ $# -ne 3 ]]; then echo -e "wrong number of arguments to function doTheDiff $(printf "'%s' " "$@")" >&2 ; exit 5 ; fi
    local err="false"
    if [[ ! -f "$1" ]]; then err="true" ; echo -e "$3 \033[0;31mdoes not exist here\033[0m" >&2 ; fi
    if [[ ! -f "$2" ]]; then err="true" ; echo -e "$3 \033[0;31mdoes not exist in sync dir\033[0m" >&2 ; fi
    if [[ $err == "true" ]]; then return ; fi
    if cmp --silent "$1" "$2" ; then
        echo -e "\033[0;32mequal $3\033[0m" # green
    else
        echo -e "\033[0;31mdiff  $3\033[0m" # red
        if [[ "DIFF" == "$OPT_SYNC_DIRECTION" ]]; then
            set -x
            /usr/bin/opendiff "$1" "$2" -merge "$1"
            set +x
        elif [[ "DIFFBACK" == "$OPT_SYNC_DIRECTION" ]]; then
            set -x
            /usr/bin/opendiff "$2" "$1" -merge "$2"
            set +x
        fi
    fi
}

FILES_TO_CHECK=( "build.gradle.kts" "settings.gradle.kts" "sync.sh" )
DIRS_TO_CHECK=( "src/main/kotlin" )
# intersect/same elements of both arrays
readarray -t BINARYPLUGINS_TO_CHECK < <(printf '%s\n' "${SOURCE_BINARYPLUGINS[@]}" "${TARGET_BINARYPLUGINS[@]}" | sort | uniq -d)

echo "diffing non binaryPlugins files ..."
for FILE in "${FILES_TO_CHECK[@]}"; do
    doTheDiff "$SCRIPTDIR/$FILE" "$SYNCDIR_base/$FILE" "$FILE"
done

echo "diffing non binaryPlugins dirs ..."
for DIR in "${DIRS_TO_CHECK[@]}"; do
    pushd "$SCRIPTDIR" >/dev/null 2>&1 || exit 42
    readarray -t FILES_IN_SCRIPTDIR < <(find "$DIR" -type f)
    popd >/dev/null 2>&1 || exit 42
    pushd "$SYNCDIR_base" >/dev/null 2>&1 || exit 42
    readarray -t FILES_IN_SYNCDIR   < <(find "$DIR" -type f)
    popd >/dev/null 2>&1 || exit 42
    readarray -t FILES_IN_DIRS < <(printf '%s\n' "${FILES_IN_SCRIPTDIR[@]}" "${FILES_IN_SYNCDIR[@]}" | sort | uniq)
    for FILE in "${FILES_IN_DIRS[@]}"; do
        doTheDiff "$SCRIPTDIR/$FILE" "${SYNCDIR_base}/$FILE" "$FILE"
    done
done

PLUGIN_FILES_TO_CHECK=( "build.gradle.kts" "settings.gradle.kts" )
PLUGIN_DIRS_TO_CHECK=( "src" )
echo "diffing binaryPlugins content ..."
for PLUGIN in "${BINARYPLUGINS_TO_CHECK[@]}"; do
    echo "diffing binaryPlugins/$PLUGIN files ..."
    for FILE in "${PLUGIN_FILES_TO_CHECK[@]}"; do
        doTheDiff "$SCRIPTDIR/binaryPlugins/$PLUGIN/$FILE" "$SYNCDIR_base/binaryPlugins/$PLUGIN/$FILE" "binaryPlugins/$PLUGIN/$FILE"
    done
    echo "diffing binaryPlugins/$PLUGIN dirs ..."
    for DIR in "${PLUGIN_DIRS_TO_CHECK[@]}"; do
        pushd "$SCRIPTDIR/binaryPlugins/$PLUGIN" >/dev/null 2>&1 || exit 42
        readarray -t FILES_IN_SCRIPTDIR < <(find "$DIR" -type f)
        popd >/dev/null 2>&1 || exit 42
        pushd "$SYNCDIR_base/binaryPlugins/$PLUGIN" >/dev/null 2>&1 || exit 42
        readarray -t FILES_IN_SYNCDIR   < <(find "$DIR" -type f)
        popd >/dev/null 2>&1 || exit 42
        readarray -t FILES_IN_DIRS < <(printf '%s\n' "${FILES_IN_SCRIPTDIR[@]}" "${FILES_IN_SYNCDIR[@]}" | sort | uniq)
        for FILE in "${FILES_IN_DIRS[@]}"; do
            doTheDiff "$SCRIPTDIR/binaryPlugins/$PLUGIN/$FILE" "$SYNCDIR_base/binaryPlugins/$PLUGIN/$FILE" "binaryPlugins/$PLUGIN/$FILE"
        done
    done
done

# finally deal with libs.versions.toml
doTheDiff "$SCRIPTDIR/libs.versions.toml" "$SYNCDIR_base/libs.versions.toml" "libs.versions.toml"

exit 0
