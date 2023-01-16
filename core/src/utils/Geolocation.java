package utils;

public class Geolocation {
    public double lat;
    public double lng;
    public String name;
    public String address;
    public int numOfPeople;

    public Geolocation(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public Geolocation(double lat, double lng, String name, String address, int numOfPeople) {
        this.lat = lat;
        this.lng = lng;
        this.name = name;
        this.numOfPeople = numOfPeople;
        this.address = address;
    }
}
