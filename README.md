# <img src="src/main/resources/META-INF/pluginIcon.svg" width="40" height="40" align="top" alt=""> Chronicle Services Diagram

An IntelliJ IDEA plugin that adds a **Diagram** tab to Chronicle Services `services.yaml` files, next to the usual **Text** tab at the bottom of the editor. The diagram shows the services and the queues they read and write, and you can edit the YAML from it.

![The Diagram tab: services.yaml on the left, the services and queues drawn top to bottom on the right, with a node's right-click menu open](docs/images/diagram.png)

## Features

- **YAML beside the diagram:** the Diagram tab is split, with the editable YAML on the left and the diagram on the right. Both show the same document, so a change on either side appears in the other right away. Selecting a node scrolls the YAML to its declaration. Drag the divider to resize; the position is remembered.
- **Diagram of services and queues:** services are boxes. Each queue is a labelled arrow from the service that writes it to the services that read it, and its tag shows the roll cycle and master. A queue with no writer or no reader is a small pill. The flow runs *Top to bottom* by default; a toolbar toggle switches it to *Left to right*, and the IDE remembers the choice. The layout keeps arrow crossings to a minimum.
- **Drag to arrange:** drag services, queue pills and queue tags, and their arrows follow. A dragged tag stays attached to the service that writes it, and the arrows run through it to the readers. Moved nodes keep their place through YAML edits and renames, and each layout and focus view keeps its own arrangement. *Reset layout* puts the current view back. Positions are kept until the file is closed.
- **Fills the space:** the diagram fits its area and re-fits when you maximize or resize the window or drag the divider. Panning or zooming yourself switches this off, and the view keeps its centre on resize. *Fit* switches it back on.
- **Rename from the diagram:** double-click a node. Renaming a queue updates its declaration and every service `inputs`, `output` and `snapshotQueue` that refers to it, as one undoable step. Make other changes in the YAML beside the diagram.
- **Search:** type in the search box (Ctrl+F) to highlight every node whose name, properties or builder settings match. Enter jumps to the next match and Shift+Enter to the previous one. Each jump zooms in on the match and shows its YAML. Esc clears the search.
- **Focus on part of a large diagram:** right-click a service or queue.
  - *Show Upstream & Downstream* keeps only what feeds it, all the way back, and what it feeds, all the way forward.
  - *Show Direct Connections* keeps only what is directly in and out. For a service, that's the queues it reads and the queue it writes. For a queue, it's the services that write and read it.
  - The toolbar shows what you're focused on. *Show All*, or Esc, brings everything back.
- **Problems are marked:** a queue that a service uses but the `queues` section doesn't declare is drawn dashed, and so is a `master` that isn't one of the replicate sets.
- **Jump to the YAML:** click a node to scroll the YAML to its declaration, or Ctrl+click to also move the cursor there.

The tab appears for files named `service.yaml` or `services.yaml`, and for any `.yaml`/`.yml` file whose first 4 KB contain `!ChronicleServicesCfg`.

## Expected format

```yaml
!ChronicleServicesCfg {
  queues: {
    orders-in: {
      path: data/orders-in,
      builder: !SingleChronicleQueueBuilder { rollCycle: !RollCycles FAST_HOURLY, replicateSets: [ sg1, sg2 ], master: sg1 }
    },
    trades-out: { path: data/trades-out }
  },
  services: {
    matchingEngine: { inputs: [ orders-in ], output: trades-out, implClass: com.acme.MatchingEngineImpl }
  }
}
```

Block-style YAML works too. A full example is in [src/test/resources/samples/services.yaml](src/test/resources/samples/services.yaml).

## How it works

1. **Reading the file:** the plugin reads the YAML with IntelliJ's bundled YAML support and builds its own model of the queues, services and queue builders, keeping where each name and reference sits in the file.
2. **Drawing:** the model is sent to a single self-contained page (plain JavaScript and SVG, with no libraries and no network access) in the IDE's embedded browser. The page lays out the diagram so that arrows cross as little as possible.
3. **Editing:** an edit from the diagram, such as a rename, replaces exact ranges of the YAML text in the editor, as one undoable step. Comments, formatting and tags such as `!RollCycles` are kept. Typing in the YAML redraws the diagram.

### No Chronicle libraries

The plugin contains no Chronicle Software code and doesn't need any of its proprietary libraries, at build time or at run time. It works only with the text of the configuration file, so you don't need a Chronicle licence to use it. It doesn't run, start or connect to your services, and it works offline.

## Requirements

IntelliJ IDEA 2021.3 or newer, with the bundled YAML plugin and the embedded browser (JCEF). Since 2026.2 the browser is the "Web Browser (JCEF)" plugin.

## Building

Gradle must run on JDK 21 or newer. The plugin itself is compiled to Java 11 bytecode.

```bash
./gradlew buildPlugin    # plugin zip in build/distributions/
./gradlew test
./gradlew runIde         # sandbox IDE with the plugin installed
./gradlew verifyPlugin   # check compatibility with IDEA 2021.3 to 2026.2
```

## License

Free to use under the [MIT License](LICENSE). Copyright (c) 2026 Dushmantha Bandaranayake.

This is an independent project, not affiliated with or endorsed by Chronicle Software. Chronicle is a trademark of its owner.
