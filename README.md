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

## Installation and getting started

Installation, what's that?

Myself, I let Eclipse compile the Java code, populating a `bin/`
directory; then the `mapwarper` script will launch the program in a
JVM appropriately.

With a 4K display you need to add a `-Dsun.java2d.uiScale=2` argument
to the `java` command line, at least on Linux. (I haven't found a
robust way to autodeted the need for this yet).

To get started, navigate to a `*.vect` file in the `cases/`
subdirectory, single click on it to load, and then press `W` followed
by `U` to switch to warped view and locate the tracks.

Beyond that, play around. Most of the command keys are documented in
the right-click menu in the map view.  For many tools, holding Shift
down previews what the result of clicking would be. In the drawing
tools, Ctrl-click deletes nodes or line segments.

If you can read Danish, my original design notes in `gui.txt` may give
you an idea how the UI is supposed to work. Otherwise you're on your
own.  (In any case, I don't have a written-down explanation in _any_
language about the underlying model of how a chain of vector segments
defined a warped map projection ...)

## Example data

The `cases/` subdirectory contains my own warp definitions, currently
mostly for large German stations.

Many of them were created with earlier versions of the program, so
they shouldn't be taken as canonical examples of how best to use the
_current_ feature set.

## Contact

I can be reached at henning.makholm@gmail.com -- though I don't have a
good track record of answering emails promptly ...
