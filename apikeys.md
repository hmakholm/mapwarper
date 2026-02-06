# API keys for Mapwarper tilesets

Some tilesets require an API key for tile download to work.

If you procure your own keys, you can put them in `config/mapwarper.xml`
or `config/mapwarper-local.xml` or `~/.config/mapwarper/config.xml`,
with the syntax

    <apiKey name="(tileset name goes here)" value="(key goes here)"/>

(and you need to wrap that in a dummy XML top-level element too).
See the example `apiKey` element in `config/mapwarper.xml`).

I recommend not putting keys in `config/mapwarper.xml`, even though
the program will recognize them there. That way you don't risk
checking them into Git.

## OpenStreetMap "Transport Map"

The `transport` tileset needs an API key. The online renderer at
`https://www.openstreetmap.org` has one, which it is not difficult to
extract using your browser's inspector panel. I don't like just
putting it up for download, though.

This tileset used to be one's best option for clearly showing railways
on large-scale maps, but nowadays the default style has been updated
to use darker colors for railways, so it's not as needed these days.
I'm keeping it _mostly_ just as a way to test the API key
infrastructure.

## GeoDanmark orthophotos

There are two tileset definitions for this high-quality publicly
funded orthophoto collection covering Denmark, corresponding to
different APIs for accessing the data.

### `geodanmark` tiles

The _older_ API behaves like a usual tiled web map, compatible with
the coordinate system of OpenStreetMap and Google Map, which
conveniently is also the Mapwarper's internal coordinate system. It's
nice and fast; unfortunately it is slated to be sunset at some time in
2026.

This API needs a username/password combinantion, but I found a remark
on the officical distribution website to the effect of "yeah, your
users will be able to get at the key if they inspect your website's
source code, but that is not the end of the world, since the data it
gives acces to are public and free anyway". So I've felt free to
distribute _my own_ username and password in the standard
`config/mapwarper.xml`.

### `geodk` tiles

This _newer_ API will apparently be the only way to access the tiles
after 2026. It servers 1×1 kilometer tiles in full resolution, so
there will be rather longer download waits involved, but what can you
do if the old one goes away?

This tileset needa an API key; and I'm not entirely sure that the "not
the end of the world" comment applies to that, so out of an abundance
of caution I'm not distributing that. Making one for yourself is free
though. Here's what I did:

1. Log on to portal.datafordeler.dk with MitID credentials.  Create an
   account and verify your email address.

   - If you're not a Danish resident, you won't have MitID. Supposedly
     it's also possible to create an account with just a username/password
     combinantion, but I haven't tried that route myself.

2. Using the Datafordeler dashboard, create an "IT system". This seems
   to be a kind of sub-account that mostly has a reason to exist for
   corporate accounts. But you need to have one all the same.

3. Click on your new IT system in the table, and create an API key in its
   administration page.

   - There's an UI for managing an IP address whitelist. You can ignore that;
     it is not used for the orthophoto service.

   - Apparently you don't need to specify anywhere what the API key will be
     _used_ for -- so it cannot be restricted to map downloads. (A contributing
     factor in why I'm not distributing the one I made).

4. The fairly long base64 string will be shown only when _creating_ the API key.
   Immediately copy it to an element in `config/mapwarper-local.xml`.
