# kotlin scripts (.kts) and packaged (executable) versions

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
./kprettyjson.app/Contents/MacOS/kprettyjso
```
