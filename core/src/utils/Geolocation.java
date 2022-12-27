package utils;

public class Geolocation {
    public double lat;
    public double lng;
    public String name;
    public String address;

    public Geolocation(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public Geolocation(double lat, double lng, String name, String address) {
        this.lat = lat;
        this.lng = lng;
        this.name = name;
        this.address = address;
    }
}
