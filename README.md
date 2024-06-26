# kotlin script goodies for the terminal shell

using [https://github.com/kscripting/kscript](https://github.com/kscripting/kscript) to execute kotlin `.kt` or `.kts` files/scripts

used libraries also include:
- multiplatform file handling: [https://github.com/square/okio](https://github.com/square/okio)
- clikt cmd line parsing: [https://github.com/ajalt/clikt](https://github.com/ajalt/clikt)


## known limitations

for repeatable opt groups, the corresponding subcommand has to come BEFORE the actual subcommand that does the action.

e.g.: see `tutorial_DoWithUsers.kt`   `user --login hoffi user --login admin` have to come BEFORE `doWithUsers`!

## implement your own MainApp Subcommands

Any ctx objects shared between subcommands should go into root's context obj

and root's context obj should be a `MutableSet<AContextValue>`

see `helpers_ScriptHelpers.kt`

## bash helper functions

define an environment variable where to find all your kscripts:
```bash
export KTSCRIPTSBASE="$HOME/gitRepos/kscripts/src/main/kotlin"
```

For calling a script inside src/main/kotlin/... via `Main.kt` use one of these:<br>
(just call `kt` to see available configured subcommands.)
```bash
# default suppress all error messages to just get STDOUT
function kt() {
  if [[ -z "$KTSCRIPTSBASE" ]]; then echo "\$KTSCRIPTSBASE not set. Set with e.g.: export KTSCRIPTSBASE=\"\$HOME/gitRepos/kscripts/src/main/kotlin\"" >&2 ; fi
  if [[ ! -s "$KTSCRIPTSBASE/Main.kt" ]]; then echo "did not find '\$KTSCRIPTSBASE/Main.kt'" ; return 1 ; fi
  echo "kscript \$KTSCRIPTSBASE/Main.kt "$*" 2> >(grep -v '^\[kscript\]') # only redirect stderr to grep" >&2
  kscript "$KTSCRIPTSBASE/Main.kt" "$@" 2> >(grep -v '^\[kscript\]') # only redirect stderr to grep
}
# echo all output of kscript
function ktraw() {
  if [[ -z "$KTSCRIPTSBASE" ]]; then echo "\$KTSCRIPTSBASE not set. Set with e.g.: export KTSCRIPTSBASE=\"\$HOME/gitRepos/kscripts/src/main/kotlin\"" >&2 ; fi
  if [[ ! -s "$KTSCRIPTSBASE/Main.kt" ]]; then echo "did not find '\$KTSCRIPTSBASE/Main.kt'" ; return 1 ; fi
  echo "kscript \$KTSCRIPTSBASE/Main.kt "$*" 2> >(awk '{gsub(\"\\\\\\[nl\\\\\\]\",\"\\n\")};1') # only redirect stderr" >&2
  kscript "$KTSCRIPTSBASE/Main.kt" "$@" 2> >(awk '{gsub("\\\[nl\\\]","\n")};1') # only redirect stderr to awk
}
```

For calling a specific script directly without going via Main.kt use:
```bash
function ktscript() {
  if [[ -z "$KTSCRIPTSBASE" ]]; then echo "\$KTSCRIPTSBASE not set. Set with e.g.: export KTSCRIPTSBASE=\"\$HOME/gitRepos/kscripts/src/main/kotlin\"" >&2 ; fi
  if [[ ! -s "$KTSCRIPTSBASE/$1" ]]; then echo "did not find '\$KTSCRIPTSBASE/$1' script does not exist/zero" >&2 ; return 1 ; fi
  local ktscr="$1" ; shift
  echo "kscript \$KTSCRIPTSBASE/$ktscr $* 2> >(grep -v '^\[kscript\]') # only redirect stderr to grep" >&2
  kscript "$KTSCRIPTSBASE/$ktscr" "$@" 2> >(grep -v '^\[kscript\]') # only redirect stderr to grep
}
# echo all output of kscript
function ktscriptraw() {
  if [[ -z "$KTSCRIPTSBASE" ]]; then echo "\$KTSCRIPTSBASE not set. Set with e.g.: export KTSCRIPTSBASE=\"\$HOME/gitRepos/kscripts/src/main/kotlin\"" >&2 ; fi
  if [[ ! -s "$KTSCRIPTSBASE/$1" ]]; then echo "did not find '\$KTSCRIPTSBASE/$1' script does not exist/zero" >&2 ; return 1 ; fi
  local ktscr="$1" ; shift
  echo "kscript \$KTSCRIPTSBASE/$ktscr $* 2> >(awk '{gsub(\"\\\\\\[nl\\\\\\]\",\"\\n\")};1') # only redirect stderr" >&2
  kscript "$KTSCRIPTSBASE/$ktscr" "$@" 2> >(awk '{gsub("\\\[nl\\\]","\n")};1') # only redirect stderr to awk
}
```

create bash completions for above functions:
```bash
## create bash completions for kt and ktraw
function ktcompletions() {
  if [[ -z "$KTSCRIPTSBASE" ]]; then echo "\$KTSCRIPTSBASE not set. Set with e.g.: export KTSCRIPTSBASE=\"\$HOME/gitRepos/kscripts/src/main/kotlin\"" >&2 ; fi
  if [[ ! -s "$KTSCRIPTSBASE/Main.kt" ]]; then echo "did not find '\$KTSCRIPTSBASE/Main.kt'" ; return 1 ; fi
    local subcommands=$(kt "completions")
    complete -W "${subcommands[*]}" "kt"
    complete -W "${subcommands[*]}" "ktraw"
    subcommands=$(kt "completions" "scriptfiles")
    complete -W "${subcommands[*]}" "ktscript"
    complete -W "${subcommands[*]}" "ktscriptraw"
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

## ***BEWARE!!!***

kscript's `@file:Import("from/any/subdir/Scriptname.kt")` just includes the utf-8 content of that file at ***that*** place.

INCLUDING any package that might be in it.

So you might end up having multiple package declarations in the resulting script that kscript tries to compile.


<big>***THEREFORE IT IS MORE OR LESS RECOMMENDED NOT(!!!) TO USE package's within decomposed parts of your script***</big>

see [kscript issue 334](https://github.com/kscripting/kscript/issues/334)
