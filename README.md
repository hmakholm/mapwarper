# Mapwarper (v3)

This is a desktop application for displaying Google Maps (and Bing)
aerophotos with specially tailored distortions to make railway track
layouts more clearly visible.

I don't particularly expect anyone but myself will really want to use
it, but just in case someone I tell about it is curious, here are some
notes:

## Attributions

### Maps

For gross navigation, the application displays map tiles it has
downloaded from one of various sources.

 * OpenStreetMap --
   © [OpenStreetMap Foundation](https://openstreetmap.org/copyright) and
   its contributors, map data licensed under [Open Data Commons Open
   Database License](https://opendatacommons.org/licenses/odbl/).
   Tiles rendered by OpenStreetMap itself.

 * OpenStreetMap "Humanitarian" --
   Map data from [OpenStreetMap](https://osm.org/copyright), rendered by
   [OpenStreetMap France](https://www.openstreetmap.fr/fonds-de-carte/).

 * OpenTopoMap --
   Map data from [OpenStreetMap](https://openstreetmap.org/copyright).  
   _Kartendarstellung: © [OpenTopoMap](http://opentopomap.org/OpenTopoMap)
   ([CC-BY-SA](https://creativecommons.org/licenses/by-sa/3.0/))_

The program can also show a map overlay from OpenRailwayMap, mainly
useful because it contains track numbers in many locations

 * OpenRailwayMap --
   Data [© OpenStreetMap contributors](https://www.openstreetmap.org/copyright),
   Style: [CC-BY-SA 2.0](http://creativecommons.org/licenses/by-sa/2.0/)
   [OpenRailwayMap](http://www.openrailwaymap.org/).

### Aerophotos

The aerophoto views use orthophoto tiles from
[Google Maps](https://www.google.com/maps)
or alternatively [Microsoft Bing](https://www.bing.com/maps).
For each of these, I use URL formats I've reverse engineered from
their public map websites. Since they're serving tiles from them
without as much as requiring a faked `Referer` header, I suppose they
can't be _too_ opposed in practice to consuming them in a small-scale
hobby project.

Google and Bing both have official APIs I probably _should_ be using
instead (which would also provide specific attributions to their photo
subproviders, which vary from place to place) -- but those require API
keys, and even though they have free plans available, it's unclear at
best whether it makes sense to bundle an API key with source code I
put up on GitHub.

If either provider actively complains to me, I suppose I can rewrite
the code to load an API key from a local configuration file and
require the user to acquire one themself, but I so far I'm not
bothering.

## Installation

Installation, what's that?

If you're on Linux or MacOS and have a Java Development Kit installed
(so there's a `javac` command on your path), you should be able to
just check out the contents of this Git repository and run the
`mapwarper` script.  It will compile the source code into a
`mapwarper.jar` the first time it runs. If you pull new code changes
from Github, delete the JAR file to provoke a recompilation.

Or, if you're super ambitious, load the source tree into Eclipse or
another IDE that will populate a `bin/` directory automatically. The
`mapwarper` script will use that preferentially over a
`mapwarper.jar`.

On Windows you'll be more on your own with compiling the code. I don't
have any opportunity to test a launcher script for Windows. If you
know how to run a Java compiler at all, it should be straightforward
-- there are _no_ dependencies beyond what comes with Java out of the
box!

From time to time (if people bug me for it) I might release a
pre-built JAR for the benefit of people who only have a Java Runtime
Environment installed. You'll still want to check out the source tree
from Git in order to get my example warp definitions.

Strictly speaking I don't _know_ the program will work on MacOS or
Windows, but Swing claims to be cross-platform, so there's at least a
fairly good chance that it should. If you try it, let me know how it
goes!

### Minor caveats

The program starts by creating a cache directory for downloaded map
tiles in an appropriate location. The default location can be
overridden by giving a `--tilecache` argument. The program never
itself deletes anything from this cache itself, so be sure to clear it
out once you're done playing.

With a 4K display you need to add a `-Dsun.java2d.uiScale=2` argument
to the `java` command line, at least on Linux. (I haven't found a
robust way to autodetect the need for this yet).

## Getting started

Navigate to a `*.vect` file in the `cases/` subdirectory, single click
on it to load, and then press `W` to switch to warped view and locate
the tracks.  To get back to the graphical track picker the program
starts up in, press `R` to get back to an ordinary unwarped map view,
then `O`.

Drag holding the middle mouse button to move the map. Or, if you don't
have a working middle button, press `.` to select a tool where the
first button will do it.

Beyond that, play around. Most of the command keys are documented in
the right-click menu in the map view.  For many tools, holding Shift
down previews what the result of clicking would be. In the drawing
tools, Ctrl-click (or Ctrl-drag) deletes nodes.

If you can read Danish, my original design notes in `gui.txt` may give
you an idea how the UI is supposed to work. Otherwise you're on your
own.  (In any case, I don't have a written-down explanation in _any_
language about the underlying model of how a chain of vector segments
defines a warped map projection ...)

## Example data

The `cases/` subdirectory contains my own warp definitions, currently
mostly for large German stations.

Many of them were created with earlier versions of the program, so
they shouldn't be taken as canonical examples of how best to use the
_current_ feature set.

## Contact

I can be reached at henning.makholm@gmail.com -- though I don't have a
good track record of answering emails promptly ...
