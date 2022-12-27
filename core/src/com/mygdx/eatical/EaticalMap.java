package com.mygdx.eatical;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapRenderer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.connection.ClusterDescription;
import com.mygdx.eatical.assets.AssetDescriptors;

import org.bson.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import jdk.internal.org.jline.utils.Log;
import utils.Geolocation;
import utils.Keys;
import utils.MapRasterTiles;
import utils.PixelPosition;
import utils.ZoomXY;

public class EaticalMap extends ApplicationAdapter implements GestureDetector.GestureListener {

    private final int NUM_TILES = 3;
    private final int ZOOM = 14;
    private final Geolocation CENTER_GEOLOCATION = new Geolocation(46.556452, 15.646115);
    private final ArrayList<Geolocation> markers = new ArrayList<>();
    private final int WIDTH = MapRasterTiles.TILE_SIZE * NUM_TILES;
    private final int HEIGHT = MapRasterTiles.TILE_SIZE * NUM_TILES;
    private Vector3 touchPosition;
    private TiledMap tiledMap;
    private TiledMapRenderer tiledMapRenderer;
    private OrthographicCamera camera;
    private Texture[] mapTiles;
    private ZoomXY beginTile;   // top left tile
    private Geolocation selectedMarker;
    private MongoClient client;
    private MongoDatabase database;

    private SpriteBatch batch;
    private Texture pin;
    private Texture bluePin;
    private AssetManager assetManager;
    private Skin skin;
    private Stage stage;
    private Viewport viewport;

    //State
    InterfaceState state = InterfaceState.INITIAL;

    //Interface
    TextButton addButton;
    TextButton findButton;
    Window locationWindow;
    Window restaurantWindow;
    Window restaurantInfoWindow;
    Label labelLatitudeValue;
    Label labelLongitudeValue;
    Label labelLatitudeInfoValue;
    Label labelLongitudeInfoValue;
    Label labelNameValue;
    Label labelAddressValue;

    @Override
    public void create() {
        databaseConnect();
        getRestaurants();

        viewport = new FitViewport(WIDTH/2f, HEIGHT/2f);

        assetManager = new AssetManager();
        assetManager.load(AssetDescriptors.UI_FONT);
        assetManager.load(AssetDescriptors.UI_SKIN);
        assetManager.finishLoading();

        skin = assetManager.get(AssetDescriptors.UI_SKIN);

        //initialize UI globals
        addButton = new TextButton("Add Restaurant", skin);
        findButton = new TextButton("Find Restaurant", skin);
        locationWindow = new Window("Choose location", skin);
        restaurantWindow = new Window("Fill in restaurant information", skin);
        restaurantInfoWindow = new Window("Restaurant info", skin);
        labelLatitudeValue = new Label("", skin);
        labelLongitudeValue = new Label("", skin);
        labelLatitudeInfoValue = new Label("", skin);
        labelLongitudeInfoValue = new Label("", skin);
        labelNameValue = new Label("", skin);
        labelAddressValue = new Label("", skin);

        batch = new SpriteBatch();
        stage = new Stage(viewport, batch);

        pin = new Texture(Gdx.files.internal("pin.png"));
        bluePin = new Texture(Gdx.files.internal("blue_pin.png"));

        camera = new OrthographicCamera();
        camera.setToOrtho(false, WIDTH, HEIGHT);
        camera.position.set(WIDTH / 2f, HEIGHT / 2f, 0);
        camera.viewportWidth = WIDTH / 2f;
        camera.viewportHeight = HEIGHT / 2f;
        camera.zoom = 2f;
        camera.update();

        touchPosition = new Vector3();
        Gdx.input.setInputProcessor(new GestureDetector(this));

        try {
            //in most cases, geolocation won't be in the center of the tile because tile borders are predetermined (geolocation can be at
            // the corner of a tile)
            ZoomXY centerTile = MapRasterTiles.getTileNumber(CENTER_GEOLOCATION.lat, CENTER_GEOLOCATION.lng, ZOOM);
            mapTiles = MapRasterTiles.getRasterTileZone(centerTile, NUM_TILES);
            //you need the beginning tile (tile on the top left corner) to convert geolocation to a location in pixels.
            beginTile = new ZoomXY(ZOOM, centerTile.x - ((NUM_TILES - 1) / 2), centerTile.y - ((NUM_TILES - 1) / 2));
        } catch (IOException e) {
            e.printStackTrace();
        }

        tiledMap = new TiledMap();
        MapLayers layers = tiledMap.getLayers();

        TiledMapTileLayer layer = new TiledMapTileLayer(NUM_TILES, NUM_TILES, MapRasterTiles.TILE_SIZE, MapRasterTiles.TILE_SIZE);
        int index = 0;
        for (int j = NUM_TILES - 1; j >= 0; j--) {
            for (int i = 0; i < NUM_TILES; i++) {
                TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
                cell.setTile(
                    new StaticTiledMapTile(new TextureRegion(mapTiles[index], MapRasterTiles.TILE_SIZE, MapRasterTiles.TILE_SIZE)));
                layer.setCell(i, j, cell);
                index++;
            }
        }
        layers.add(layer);

        tiledMapRenderer = new OrthogonalTiledMapRenderer(tiledMap);
        stage.addActor(Menu());
        stage.addActor(locationInfo());
        stage.addActor(restaurantForm());
        stage.addActor(restaurantInfo());
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0, 0, 0, 1);

        handleInput();

        camera.update();

        tiledMapRenderer.setView(camera);
        tiledMapRenderer.render();

        drawMarkers();
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void dispose() {
        assetManager.dispose();
        bluePin.dispose();
        pin.dispose();
        stage.dispose();
    }

    @Override
    public boolean touchDown(float x, float y, int pointer, int button) {
        touchPosition.set(x, y, 0);
        camera.unproject(touchPosition);
        return false;
    }

    @Override
    public boolean tap(float x, float y, int count, int button) {
        return false;
    }

    @Override
    public boolean longPress(float x, float y) {
        return false;
    }

    @Override
    public boolean fling(float velocityX, float velocityY, int button) {
        return false;
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        camera.translate(-deltaX, deltaY);
        return false;
    }

    @Override
    public boolean panStop(float x, float y, int pointer, int button) {
        return false;
    }

    @Override
    public boolean zoom(float initialDistance, float distance) {
        if (initialDistance >= distance) {
            camera.zoom += 0.02;
        } else {
            camera.zoom -= 0.02;
        }
        return false;
    }

    @Override
    public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2) {
        return false;
    }

    @Override
    public void pinchStop() {

    }

    private void handleInput() {
        if (state != InterfaceState.FILL_RESTAURANT_INFO) {
            if (Gdx.input.isKeyPressed(Input.Keys.A)) {
                camera.zoom += 0.02;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
                camera.zoom -= 0.02;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
                camera.translate(-3, 0, 0);
            }
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
                camera.translate(3, 0, 0);
            }
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
                camera.translate(0, -3, 0);
            }
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
                camera.translate(0, 3, 0);
            }

            if (Gdx.input.isTouched()) {
                Vector3 touchPos = new Vector3();
                touchPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                camera.unproject(touchPos);

                // TODO: Implement screen position:: to coordinates
                Geolocation locationTouched = new Geolocation(46.556452, 15.646115);

                if (state == InterfaceState.CHOOSE_LOCATION) {
                    chooseLocation(locationTouched);
                } else if (state == InterfaceState.FIND_RESTAURANT) {
                    findClosestRestaurant(locationTouched);
                }
            }
        }

        camera.zoom = MathUtils.clamp(camera.zoom, 0.5f, 2f);

        float effectiveViewportWidth = camera.viewportWidth * camera.zoom;
        float effectiveViewportHeight = camera.viewportHeight * camera.zoom;

        camera.position.x = MathUtils.clamp(camera.position.x, effectiveViewportWidth / 2f, WIDTH - effectiveViewportWidth / 3f);
        camera.position.y = MathUtils.clamp(camera.position.y, effectiveViewportHeight / 2f, HEIGHT - effectiveViewportHeight / 3f);
    }

    private void findClosestRestaurant(Geolocation locationTouched) {
        selectedMarker = findClosestPoint(markers, locationTouched);
        if(selectedMarker != null){
            setState(InterfaceState.RESTAURANT_INFO);
            displayRestaurantInfo(selectedMarker);
        }
    }

    private void chooseLocation(Geolocation locationTouched) {
        selectedMarker = locationTouched;
        labelLatitudeValue.setText("" + locationTouched.lat);
        labelLongitudeValue.setText("" + locationTouched.lng);
    }

    private void displayRestaurantInfo(Geolocation closestRestaurant) {
        restaurantInfoWindow.setVisible(true);
        labelNameValue.setText(closestRestaurant.name);
        labelAddressValue.setText(closestRestaurant.address);
        labelLongitudeInfoValue.setText(""+closestRestaurant.lng);
        labelLatitudeInfoValue.setText(""+closestRestaurant.lat);
    }

    private Actor restaurantInfo() {
        Table table = new Table();

        Table windowTable = new Table();
        windowTable.align(Align.left);
        windowTable.setFillParent(true);
        windowTable.defaults().left().padTop(5).fill();

        Label labelName = new Label("Name:", skin);
        windowTable.add(labelName);
        windowTable.add(labelNameValue).row();

        Label labelAddress = new Label("Address:", skin);
        windowTable.add(labelAddress).fill();
        windowTable.add(labelAddressValue).row();

        Label labelLongitude = new Label("Longitude:", skin);
        windowTable.add(labelLongitude);
        windowTable.add(labelLongitudeInfoValue).row();

        Label labelLatitude = new Label("Latitude:", skin);
        windowTable.add(labelLatitude);
        windowTable.add(labelLatitudeInfoValue).row();

        final TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setState(InterfaceState.INITIAL);
            }
        });
        windowTable.add(closeButton).padTop(5).center().colspan(2).fill().row();

        restaurantInfoWindow.add(windowTable).left().fill();
        restaurantInfoWindow.setVisible(false);
        table.add(restaurantInfoWindow).padBottom(5).padRight(5).fill().row();
        table.right().bottom();
        table.setFillParent(true);

        return table;
    }

    private Actor restaurantForm() {
        Table table = new Table();

        Table windowTable = new Table();
        windowTable.align(Align.left);
        windowTable.setFillParent(true);
        windowTable.defaults().left().padTop(5).fill();

        Label labelName = new Label("Name:", skin);
        windowTable.add(labelName);
        final TextField textFieldName = new TextField("", skin);
        windowTable.add(textFieldName).row();

        Label labelAddress = new Label("Address:", skin);
        windowTable.add(labelAddress);
        final TextField textFieldAddress = new TextField("", skin);
        windowTable.add(textFieldAddress).row();

        final TextButton publishButton = new TextButton("Publish", skin);
        publishButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (selectedMarker != null && !Objects.equals(textFieldName.getText(), "") && !Objects.equals(textFieldAddress.getText(), "")) {
                    publishRestaurant(textFieldName.getText(), (textFieldAddress.getText()));
                    textFieldName.setText("");
                    textFieldAddress.setText("");
                    setState(InterfaceState.INITIAL);

                }
            }
        });
        windowTable.add(publishButton).padTop(5).center().colspan(2).fill().row();

        restaurantWindow.add(windowTable).left().fill();
        restaurantWindow.setVisible(false);
        table.add(restaurantWindow).padBottom(5).padRight(5).fill().row();
        table.right().bottom();
        table.setFillParent(true);

        return table;
    }

    private Actor locationInfo() {
        Table table = new Table();

        Table windowTable = new Table();
        windowTable.align(Align.left);
        windowTable.setFillParent(true);
        windowTable.defaults().left().padTop(5).fill();

        Label labelLongitude = new Label("Longitude:", skin);
        windowTable.add(labelLongitude);
        windowTable.add(labelLongitudeValue).row();

        Label labelLatitude = new Label("Latitude:", skin);
        windowTable.add(labelLatitude);
        windowTable.add(labelLatitudeValue).row();

        TextButton nextButton = new TextButton("Next", skin);
        nextButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setState(InterfaceState.FILL_RESTAURANT_INFO);
            }
        });
        windowTable.add(nextButton).padTop(5).center().colspan(2).fill().row();

        locationWindow.add(windowTable).left().fill();
        locationWindow.setVisible(false);
        table.add(locationWindow).padBottom(5).padRight(5).fill().row();
        table.right().bottom();
        table.setFillParent(true);

        return table;
    }

    private Actor Menu() {
        Table table = new Table();

        table.add(addButton).padBottom(5).padRight(5).fill();
        addButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setState(InterfaceState.CHOOSE_LOCATION);
            }
        });

        table.add(findButton).padBottom(5).padRight(5).fill().row();
        findButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setState(InterfaceState.FIND_RESTAURANT);
            }
        });

        table.bottom().right();
        table.setFillParent(true);

        return table;
    }

    private void databaseConnect() {
        try {
            client = MongoClients.create(Keys.MONGO_CONNECTION_STRING);
            database = client.getDatabase("maribor-digital-twin-db");
            System.out.println("Connected successfully to server.");
        } catch (MongoException exception) {
            System.err.println("An error occurred while attempting to run a command: " + exception);
        }
    }

    private boolean checkConnection() {
        ClusterDescription clusterDescription = client.getClusterDescription();
        if (clusterDescription.getConnectionMode() == ClusterConnectionMode.SINGLE) {
            System.out.println("Connect directly to a server, regardless of the type of cluster it is a part of..");
            return true;
        } else if (clusterDescription.getConnectionMode() == ClusterConnectionMode.MULTIPLE) {
            System.out.println("Connect to multiple servers in a cluster (either a replica set or multiple mongos servers).");
            return true;
        } else {
            System.out.println("MongoClient is not connected to a server.");
            return false;
        }
    }

    private void getRestaurants() {
        if (checkConnection()) {
            MongoCollection<Document> collection = database.getCollection("restaurants");
            FindIterable<Document> iterDoc = collection.find();
            for (Document document : iterDoc) {
                String name = (String) document.get("name");
                String address = (String) document.get("address");
                Document location = (Document) document.get("location");
                ArrayList coordinates = (ArrayList) location.get("coordinates");
                markers.add(new Geolocation((double) coordinates.get(0), (double) coordinates.get(1), name, address));
            }
        } else {
            Log.error("Not connected to database");
        }
    }

    private void drawMarkers() {
        batch.begin();
        {
            for (Geolocation marker : markers) {
                PixelPosition pixelPosition =
                    MapRasterTiles.getPixelPosition(marker.lat, marker.lng, MapRasterTiles.TILE_SIZE, ZOOM, beginTile.x, beginTile.y,
                        HEIGHT);
                batch.draw(pin, pixelPosition.x, pixelPosition.y);
                batch.setProjectionMatrix(camera.combined);
            }

            if (selectedMarker != null) {
                PixelPosition pixelPosition =
                    MapRasterTiles.getPixelPosition(selectedMarker.lat, selectedMarker.lng, MapRasterTiles.TILE_SIZE, ZOOM, beginTile.x, beginTile.y,
                        HEIGHT);
                batch.draw(bluePin, pixelPosition.x, pixelPosition.y);
                batch.setProjectionMatrix(camera.combined);
            }
        }
        batch.end();
    }

    private void publishRestaurant(String name, String address) {
        if (checkConnection()) {
            MongoCollection<Document> collection = database.getCollection("restaurants");

            Document restaurant = new Document();
            restaurant.append("name", name);
            restaurant.append("address", address);

            Document location = new Document();
            location.append("type", "Point");
            location.append("coordinates", Arrays.asList(selectedMarker.lng, selectedMarker.lat));
            restaurant.append("location", location);

            collection.insertOne(restaurant);
        } else {
            Log.error("No connection");
        }
    }

    private static double calculateDistance(Geolocation location1, Geolocation location2) {
        double xDiff = location1.lat - location2.lat;
        double yDiff = location1.lng - location2.lng;
        return Math.sqrt(xDiff * xDiff + yDiff * yDiff);
    }

    public static Geolocation findClosestPoint(ArrayList<Geolocation> coordinates, Geolocation point) {
        double minDistance = Double.MAX_VALUE;
        Geolocation closestPoint = null;

        for (Geolocation coord : coordinates) {
            double distance = calculateDistance(coord, point);
            if (distance < minDistance && distance <= 3E-4) {
                minDistance = distance;
                closestPoint = coord;
            }
        }

        return closestPoint;
    }

    private void setState(InterfaceState newState) {
        state = newState;
        switch (newState) {
            case INITIAL:
                addButton.setVisible(true);
                findButton.setVisible(true);
                locationWindow.setVisible(false);
                restaurantWindow.setVisible(false);
                restaurantInfoWindow.setVisible(false);
                labelLatitudeValue.setText("");
                labelLongitudeValue.setText("");
                labelLatitudeInfoValue.setText("");
                labelLongitudeInfoValue.setText("");
                labelNameValue.setText("");
                labelAddressValue.setText("");
                selectedMarker = null;
                break;
            case CHOOSE_LOCATION:
                addButton.setVisible(false);
                findButton.setVisible(false);
                locationWindow.setVisible(true);
                restaurantWindow.setVisible(false);
                restaurantInfoWindow.setVisible(false);
                break;
            case FILL_RESTAURANT_INFO:
                addButton.setVisible(false);
                findButton.setVisible(false);
                locationWindow.setVisible(false);
                restaurantWindow.setVisible(true);
                restaurantInfoWindow.setVisible(false);
                break;
            case FIND_RESTAURANT:
                addButton.setVisible(false);
                findButton.setVisible(false);
                locationWindow.setVisible(false);
                restaurantWindow.setVisible(false);
                restaurantInfoWindow.setVisible(false);
                break;
            case RESTAURANT_INFO:
                addButton.setVisible(false);
                findButton.setVisible(false);
                locationWindow.setVisible(false);
                restaurantWindow.setVisible(false);
                restaurantInfoWindow.setVisible(true);
                break;
        }
    }
}
