# src/main/kotlin/README.md

## ***BEWARE!!!***

kscript's `@file:Import("from/any/subdir/Scriptname.kt")` just includes the utf-8 content of that file at ***that*** place.

INCLUDING any package that might be in it.

So you might end up having multiple package declarations in the resulting script that kscript tries to compile.


<big>***THEREFORE IT IS MORE OR LESS RECOMMENDED NOT(!!!) TO USE package's within decomposed parts of your script***</big>

see [kscript issue 334](https://github.com/kscripting/kscript/issues/334)
