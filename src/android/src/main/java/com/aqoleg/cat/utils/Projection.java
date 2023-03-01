/*
converts tiles coordinates (x, y) to longitude-latitude coordinates and back
normalizes longitude and latitude
x, longitude - horizontal axis; y, latitude - vertical axis

projections:
 spherical (epsg3857, epsg4326, web mercator, default, "not ellipsoid")
 ellipsoid (epsg3395, true mercator)

https://pubs.usgs.gov/pp/1395/report.pdf (pages 12, 41, 44-45)
 */
package com.aqoleg.cat.utils;

import static java.lang.Math.PI;

public class Projection {
    private static final double pi2; // 2*pi
    private static final double pi1_2; // pi/2
    private static final double pi1_4; // pi/4
    private static final double e; // ellipsoid eccentricity
    private static final double e1_2; // e/2

    static {
        pi2 = PI * 2.0;
        pi1_2 = PI / 2.0;
        pi1_4 = PI / 4.0;
        // [1 - (polarRadius**2 / equatorialRadius**2)]**0.5
        e = Math.sqrt(1.0 - (Math.pow(6356752.3142, 2) / Math.pow(6378137, 2)));
        e1_2 = e / 2.0;
    }

    private Projection() {
    }

    // returns x, from 0 (west, longitude = -180) to 1 (east, longitude = 180)
    // the same for both projections
    public static double getX(double longitude) {
        // formula (7 - 1a)
        // x = pi * R * (lambdaDeg - lambda0Deg) / 180
        // tilesX = x = pi / 2pi * (lon - -180) / 180 = (lon + 180) / 360
        return (longitude + 180.0) / 360.0;
    }

    // returns decimal degrees, from -180 (x = 0) to 180 (x = 1)
    // the same for both projections
    public static double getLongitude(double x) {
        // formula (7 - 5)
        // lambda = x/R + lambda0
        // lon = lambda * 180 / pi = tilesX * 2pi * 180 / pi + lambda0Deg = tilesX * 360 - 180
        return x * 360.0 - 180.0;
    }

    // returns -180 < longitude < 180
    public static double normalizeLongitude(double longitude) {
        if (longitude < -180.0 || longitude > 180.0) {
            longitude = ((longitude % 360.0) + 360.0) % 360.0;
            if (longitude > 180.0) {
                longitude -= 360.0;
            }
        }
        return longitude;
    }

    // returns y, from 0 (north, latitude 85) to 1 (south, latitude -85)
    public static double getY(double latitude, boolean ellipsoid) {
        if (ellipsoid) {
            // formula (7 - 7)
            // y = a * ln( tan(pi/4 + phi/2) * ( (1 - e*sin(phi)) / (1 + e*sin(phi)) )**(e/2) )
            // phi = lat / 180 * pi
            // temp = ( (1 - e*sin(phi)) / (1 + e*sin(phi)) )**(e/2)
            // tilesY = 0.5 - y = 0.5 - ln( tan(pi/4 + phi/2) * temp) / 2pi
            double phi = latitude / 180.0 * PI;
            double temp = e * Math.sin(phi);
            temp = Math.pow((1.0 - temp) / (1.0 + temp), e1_2);
            return 0.5 - Math.log(Math.tan(pi1_4 + phi / 2.0) * temp) / pi2;
        } else {
            // formula (7 - 2)
            // y = R * ln(tan(pi/4 + phi/2))
            // tilesY = 0.5 - y = 0.5 - ln(tan(pi/4 + lat / 360 * pi)) / 2pi
            return 0.5 - Math.log(Math.tan(pi1_4 + latitude / 360.0 * PI)) / pi2;
        }
    }

    // returns decimal degrees, from -85 (y = 1) to 85 (y = 0)
    public static double getLatitude(double y, boolean ellipsoid) {
        if (ellipsoid) {
            // formula (7 - 9)
            // phi = pi/2 - 2 * arctan( t * ( (1 - e*sin(phi)) / (1 + e*sin(phi)) )**(e/2) )
            // formula (7 - 10)
            // t = e**(-y/a)
            // t = e**( -(0.5 - tilesY) * 2pi ) = e**( tilesY * 2pi - pi )
            // formula (7 - 11)
            // trialPhi = pi/2 - 2 * arctan(t)
            double t = Math.exp((pi2 * y) - PI);
            double trialPhi = pi1_2 - 2.0 * Math.atan(t);
            double phi = 0;
            // usually 1 - 2 iterations
            for (int i = 0; i < 5; i++) {
                phi = e * Math.sin(trialPhi);
                phi = pi1_2 - 2.0 * Math.atan(t * Math.pow((1.0 - phi) / (1.0 + phi), e1_2));
                if (Math.abs(trialPhi - phi) < 0.000000001) { // 1 cm max
                    break;
                }
                trialPhi = phi;
            }
            return phi * 180.0 / PI;
        } else {
            // formula (7 - 4a)
            // phi = arctan(sinh(y / R))
            // lat = phi * 180 / pi = arctan(sinh( (0.5 - tilesY) * 2pi )) * 180 / pi =
            // = arctan(sinh( pi - 2pi * tilesY )) * 180 / pi
            return Math.atan(Math.sinh(PI - (pi2 * y))) * 180.0 / PI;
        }
    }

    // returns -85 < latitude < 85
    public static double normalizeLatitude(double latitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            latitude = ((latitude % 360.0) + 360.0) % 360.0;
            if (latitude > 270.0) {
                latitude -= 360.0;
            } else if (latitude > 90.0) {
                latitude = 180.0 - latitude;
            }
        }
        if (latitude < -85.0) {
            return -85.0;
        } else if (latitude > 85.0) {
            return 85.0;
        }
        return latitude;
    }
}