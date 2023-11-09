# kotlin script goodies for the terminal shell

using [https://github.com/kscripting/kscript](https://github.com/kscripting/kscript) to execute kotlin `.kt` or `.kts` files/scripts

used libraries also include:
- multiplatform file handling: [https://github.com/square/okio](https://github.com/square/okio)
- clikt cmd line parsing: [https://github.com/ajalt/clikt](https://github.com/ajalt/clikt)


## known limitations

clikt Main and subcommands cannot both have a `fun main(...)`

## bash helper functions

```bash
export KTSCRIPTSBASE="$HOME/gitRepos/kscripts/src/main/kotlin"
function kt() {
  echo "kscript \$KTSCRIPTSBASE/Main.kt $* 2> >(grep -v '^\[kscript\]') # only redirect stderr to grep" >&2
  kscript "$KTSCRIPTSBASE/Main.kt" "$@" 2> >(grep -v '^\[kscript\]') # only redirect stderr to grep
}
function ktraw() {
  echo "kscript \$KTSCRIPTSBASE/Main.kt $* 2> >(awk '{gsub(\"\\\\\\[nl\\\\\\]\",\"\\n\")};1') # only redirect stderr" >&2
  kscript "$KTSCRIPTSBASE/Main.kt" "$@" 2> >(awk '{gsub("\\\[nl\\\]","\n")};1') # only redirect stderr to awk
}
function ktscript() {
  if [[ -z "$KTSCRIPTSBASE" ]]; then echo "\$KTSCRIPTSBASE not set. Set with e.g.: export KTSCRIPTSBASE=\"\$HOME/gitRepos/kscripts/src/main/kotlin\"" >&2 ; fi
  if [[ ! -s "$KTSCRIPTSBASE/$1" ]]; then echo "'$KTSCRIPTSBASE/$1' script does not exist/zero" >&2 ; return 1 ; fi
  local ktscr="$1" ; shift
  echo "kscript \$KTSCRIPTSBASE/$ktscr $* 2> >(grep -v '^\[kscript\]') # only redirect stderr to grep" >&2
  kscript "$KTSCRIPTSBASE/$ktscr" "$@" 2> >(grep -v '^\[kscript\]') # only redirect stderr to grep
}
function ktscriptraw() {
  if [[ -z "$KTSCRIPTSBASE" ]]; then echo "\$KTSCRIPTSBASE not set. Set with e.g.: export KTSCRIPTSBASE=\"\$HOME/gitRepos/kscripts/src/main/kotlin\"" >&2 ; fi
  if [[ ! -s "$KTSCRIPTSBASE/$1" ]]; then echo "'$KTSCRIPTSBASE/$1' script does not exist/zero" >&2 ; return 1 ; fi
  local ktscr="$1" ; shift
  echo "kscript \$KTSCRIPTSBASE/$ktscr $* 2> >(awk '{gsub(\"\\\\\\[nl\\\\\\]\",\"\\n\")};1') # only redirect stderr" >&2
  kscript "$KTSCRIPTSBASE/$ktscr" "$@" 2> >(awk '{gsub("\\\[nl\\\]","\n")};1') # only redirect stderr to awk
}
```

## kotlin scripts (.kts) and packaged (executable) versions

get executable version of a .kts by

```shell
kscript --package some_script.kts
```

or use java 14+ jpackage tool

``````
jpackage --verbose --name kprettyjson \
    --input build/libs --main-jar kscripts-1.0.0-all.jar \
    --main-class MainKt \
    --type app-image
``````

and then

```
./kprettyjson.app/Contents/MacOS/kprettyjson
```
