# cat

All maps offline with a track writer.

[download](https://github.com/aqoleg/cat/releases/download/5.0.0/cat.apk)

## add maps

[mende](https://cat.aqoleg.com/app?newMap=mende&newUrl=http%3A%2F%2Fcat.aqoleg.com%2Fmaps%2Fmende%2F%253%24d%2F%252%24d%2F%251%24d.jpeg)

[osm](https://cat.aqoleg.com/app?newMap=osm&newUrl=http%3A%2F%2Fa.tile.openstreetmap.org%2F%253%24d%2F%251%24d%2F%252%24d.png)

[otm](https://cat.aqoleg.com/app?newMap=otm&newUrl=https%3A%2F%2Fa.tile.opentopomap.org%2F%253%24d%2F%251%24d%2F%252%24d.png)

[topo](https://cat.aqoleg.com/app?newMap=topo&newUrl=https%3A%2F%2Fmaps.marshruty.ru%2Fml.ashx%3Fal%3D1%26x%3D%251%24d%26y%3D%252%24d%26z%3D%253%24d)

[gsat](https://cat.aqoleg.com/app?newMap=gsat&newUrl=https%3A%2F%2Fkhms0.googleapis.com%2Fkh%3Fv%3D937%26hl%3Den%26x%3D%251%24d%26y%3D%252%24d%26z%3D%253%24d)

[gmap](https://cat.aqoleg.com/app?newMap=gmap&newUrl=http%3A%2F%2Fmt0.google.com%2Fvt%2Flyrs%3Dm%26hl%3Den%26x%3D%251%24d%26y%3D%252%24d%26z%3D%253%24d)

[yasat (ellipsoid)](https://cat.aqoleg.com/app?newMap=yasat&newUrl=https%3A%2F%2Fsat01.maps.yandex.net%2Ftiles%3Fl%3Dsat%26x%3D%251%24d%26y%3D%252%24d%26z%3D%253%24d%26g%3DGagarin&newProjection=ellipsoid)

[arcsat](https://cat.aqoleg.com/app?newMap=arcsat&newUrl=https%3A%2F%2Fservices.arcgisonline.com%2FArcGIS%2Frest%2Fservices%2FWorld_Imagery%2FMapServer%2Ftile%2F%253%24d%2F%252%24d%2F%251%24d)

[arctopo](https://cat.aqoleg.com/app?newMap=arctopo&newUrl=https%3A%2F%2Fservices.arcgisonline.com%2FArcGIS%2Frest%2Fservices%2FWorld_Topo_Map%2FMapServer%2Ftile%2F%253%24d%2F%252%24d%2F%251%24d)

## external api

http(s)://cat.aqoleg.com/app?

### add map

newMap=mapName&newUrl=urlToDownload&newProjection=projection&

where

- newMap - the name of the map to add
- newUrl - url to download tiles, with %1$d for x, %2$d for y an %3$d for z, for example https://tileserver.com/x=%1$d/y=%2$d/z=%3$d.png
- newProjection - optional, use 'ellipsoid' for ellipsoid prjection

### open map

map=mapName&

### setZoom

z=zoom&

### open point

longitude=lon&latitude=lat&  or
lon=lon&lat=lat&

### open track

track=track&

where track is decoded track "xLonyLatxNextLonyNextLat", for example
track=x-34y-51x-34.5y-51.5x-34.1y-51.2



[deprecated store](https://play.google.com/store/apps/details?id=space.aqoleg.cat)
