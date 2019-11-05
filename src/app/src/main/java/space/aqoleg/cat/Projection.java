/*
math for projections
converts tiles coordinates to longitude-latitude coordinates and back
spherical (epsg3857, epsg4326, web mercator, default) and ellipsoid (epsg3395)
https://pubs.usgs.gov/pp/1395/report.pdf
 */
package space.aqoleg.cat;

class Projection {
    private static final double pi2; // 2*pi
    private static final double pi1_2; // pi/2
    private static final double pi1_4; // pi/4
    private static final double e; // ellipsoid eccentricity
    private static final double e1_2; // e/2

    static {
        pi2 = Math.PI * 2;
        pi1_2 = Math.PI / 2;
        pi1_4 = Math.PI / 4;
        // [1 - (polarRadius**2 / equatorialRadius**2)]**0.5
        e = Math.sqrt(1 - (Math.pow(6356752.3142, 2) / Math.pow(6378137, 2)));
        e1_2 = e / 2;
    }

    /*
    longitude in decimal degrees from -180 (west) to 180 (east)
    x from 0 (longitude -180) to 1 (longitude 180)
    use double values, float has not enough precious
    this is the same for both projections
    xRadian = longitudeRadian
    x = (xRadian - x0Radian) / 2pi = (longitude + 180) / 360
     */
    static double getX(double longitude) {
        return (longitude + 180) / 360;
    }

    static double getLongitude(double x) {
        return x * 360 - 180;
    }

    /*
    latitude in decimal degrees from -85 (south) to 85 (north)
    y from 0 (north) to 1 (south), reversed direction
    use double values, float has not enough precious
    y = (y0Radian - yRadian) / 2pi = (pi - yRadian) / 2pi = 0.5 - yRadian / 2pi
    yRadian = y0Radian - y * 2pi = pi - y * 2pi
     */
    static double getY(double latitude, boolean ellipsoid) {
        // yRadian = ln[ tan(pi/4 + latitudeRadian/2) * k ]
        if (!ellipsoid) {
            // k = 1
            return 0.5 - Math.log(Math.tan(pi1_4 + Math.toRadians(latitude) / 2)) / pi2;
        } else {
            // k = [ (1 - e*sin(latitudeRadian)) / (1 + e*sin(latitudeRadian)) ]**(e/2)
            double latitudeRadian = Math.toRadians(latitude);
            double k = e * Math.sin(latitudeRadian);
            k = Math.pow((1 - k) / (1 + k), e1_2);
            return 0.5 - Math.log(Math.tan(pi1_4 + latitudeRadian / 2) * k) / pi2;
        }
    }

    static double getLatitude(double y, boolean ellipsoid) {
        if (!ellipsoid) {
            // latitudeRadian = arctan[ sinh(yRadian) ]
            return Math.toDegrees(Math.atan(Math.sinh(Math.PI - (pi2 * y))));
        } else {
            // latitudeRadian =
            // pi/2 - 2*arctan[ t * ( (1 - e * sin(latitudeRadian) / (1 + e * sin(latitudeRadian)) )**(e/2) ]
            // t = e ** (-yRadian)
            double t = Math.exp((pi2 * y) - Math.PI);
            // first trial latitude = pi/2 - 2*arctan(t)
            double trialLatitudeRadian = pi1_2 - 2 * Math.atan(t);
            double latitudeRadian = 0;
            // usually 1 - 2 iterations
            for (int i = 0; i < 5; i++) {
                latitudeRadian = e * Math.sin(trialLatitudeRadian);
                latitudeRadian = pi1_2 - 2 * Math.atan(t * Math.pow((1 - latitudeRadian) / (1 + latitudeRadian), e1_2));
                if (Math.abs(trialLatitudeRadian - latitudeRadian) < 0.000000001) { // 1 cm max
                    break;
                }
                trialLatitudeRadian = latitudeRadian;
            }
            return Math.toDegrees(latitudeRadian);
        }
    }
}