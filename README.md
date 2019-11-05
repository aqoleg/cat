# cat

All maps offline in one app and track writer.

[download](https://github.com/aqoleg/cat/releases/download/4.0.0/cat.apk)

Put tiles in the /cat/maps/mapName/z/y/x.png or /x.jpeg 
and/or specifiy parameters in /cat/maps/mapName/properties.txt as json file:
- "name" - optional, name of the map
- "url" - optional, url to download as java formatted string "https://example/x=%1$d/y=%2$d/z=%3$d"
- "size" - optional, size of the tile
- "projection": "ellipsoid" - optional, for ellipsoid projection
