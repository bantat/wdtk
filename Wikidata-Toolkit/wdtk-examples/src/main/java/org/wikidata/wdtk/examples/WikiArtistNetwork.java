package org.wikidata.wdtk.examples;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedPseudograph;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by torebanta on 7/13/17.
 */
public class WikiArtistNetwork {

    static DirectedGraph<String, DefaultEdge> graph = new DirectedPseudograph<>(DefaultEdge.class);

    static Connection conn;
    static MysqlDataSource dataSource;

    static ArrayList<String> artists = new ArrayList<>();
    static ArrayList<String> genres = new ArrayList<>();
    static ArrayList<String> labels = new ArrayList<>();
    static ArrayList<String> categories = new ArrayList<>();
    static ArrayList<String> verifiedArtists = new ArrayList<>();

    static Map<String, String> wikiToVevo = new HashMap<>();
    static Map<String, String> vevoToWiki = new HashMap<>();

    public static void buildComponentFromGenres(JSONArray genreArray, String artist) {
        for (int i = 0; i < genreArray.length(); i++) {
            String genre = genreArray.getString(i);
            if (!genres.contains(genre)) {
                genres.add(genre);
                if (!graph.containsVertex(genre)) graph.addVertex(genre);
            }
            graph.addEdge(artist, genre);
            graph.addEdge(genre, artist);
        }
    }

    public static void buildComponentFromLabels(JSONArray labelArray, String artist) {
        for (int i = 0; i < labelArray.length(); i++) {
            String label = labelArray.getString(i);
            if (!labels.contains(label)) {
                labels.add(label);
                if (!graph.containsVertex(label)) graph.addVertex(label);
            }
            graph.addEdge(artist, label);
            graph.addEdge(label, artist);
        }
    }

    public static void buildComponentFromCategories(JSONArray categoryArray, String artist) {
        for (int i = 0; i < categoryArray.length(); i++) {
            String category = categoryArray.getString(i);
            if (!categories.contains(category)) {
                categories.add(category);
                if (!graph.containsVertex(category)) graph.addVertex(category);
            }
            graph.addEdge(artist, category);
            graph.addEdge(category, artist);
        }
    }

    public static void buildComponentFromAssociated(JSONArray associatedArray, String artist) {
        for (int i = 0; i < associatedArray.length(); i++) {
            String associatedArtist = associatedArray.getString(i);
            if (!artists.contains(associatedArtist)) {
                artists.add(associatedArtist);
                if (!graph.containsVertex(associatedArtist)) graph.addVertex(associatedArtist);
            }
            //if (!graph.containsEdge(artist, associatedArtist)) graph.addEdge(artist, associatedArtist);
            graph.addEdge(artist, associatedArtist);
        }
    }

    public static void buildGraph(JSONObject wikiData) {
        for (String name : wikiData.keySet()) {
            JSONObject artist = wikiData.getJSONObject(name);

            if (!artists.contains(name)) {
                artists.add(name);
                if (!graph.containsVertex(name)) graph.addVertex(name);
            }

            if (artist.has("genres")) buildComponentFromGenres(artist.getJSONArray("genres"), name);

            if (artist.has("labels")) buildComponentFromLabels(artist.getJSONArray("labels"), name);

            if (artist.has("categories")) buildComponentFromCategories(artist.getJSONArray("categories"), name);

            if (artist.has("associated")) buildComponentFromAssociated(artist.getJSONArray("associated"), name);
        }
    }

    public static void updateNodeScore(String source, String node, int weight, Map map) {
        if (!labels.contains(source) && !categories.contains(source)) {
            if (!map.containsKey(node)) map.put(node, weight);
            else map.put(node, (int) map.get(node) + weight);
        } else if (labels.contains(source)) {
            if (weight == 1) return;
            else {
                if (!map.containsKey(node)) map.put(node, weight - 1);
                else map.put(node, (int) map.get(node) + weight - 1);
            }
        } else if (source.toLowerCase().contains("from")) {
            if (weight == 1) return;
            else {
                if (!map.containsKey(node)) map.put(node, weight - 1);
                else map.put(node, (int) map.get(node) + weight - 1);
            }
        } else {
            if (!map.containsKey(node)) map.put(node, weight);
            else map.put(node, (int) map.get(node) + weight);
        }
    }

    public static void updateNodeFirstDegree(Map map) {
        if (!map.containsKey("source")) map.put("source", 1);
        else map.put("source", (int) map.get("source") + 1);
    }

    public static void updateNodeSecondDegree(String source, String node, Map map) {
        if (genres.contains(source)) {
            if (!map.containsKey("genre")) map.put("genre", 1);
            else map.put("genre", (int) map.get("genre") + 1);
        } else if (labels.contains(source)) {
            if (!map.containsKey("label")) map.put("label", 1);
            else map.put("label", (int) map.get("label") + 1);
        } else if (categories.contains(source)) {
            if (!map.containsKey("category")) map.put("category", 1);
            else map.put("category", (int) map.get("category") + 1);
        } else if (artists.contains(source)) {
            if (!map.containsKey("artist")) map.put("artist", 1);
            else map.put("artist", (int) map.get("artist") + 1);
        } else if (node.equals("SOURCE_ARTIST")) {
            if (!map.containsKey("return")) map.put("return", 1);
            else map.put("return", (int) map.get("return") + 1);
        }
    }

    public static void updateNodeThirdDegree(String source, String node, Map map) {
        if (genres.contains(source)) {
            if (!map.containsKey("genre-ext")) map.put("genre-ext", 1);
            else map.put("genre-ext", (int) map.get("genre-ext") + 1);
        } else if (labels.contains(source)) {
            if (!map.containsKey("label-ext")) map.put("label-ext", 1);
            else map.put("label-ext", (int) map.get("label-ext") + 1);
        } else if (categories.contains(source)) {
            if (!map.containsKey("category-ext")) map.put("category-ext", 1);
            else map.put("category-ext", (int) map.get("category-ext") + 1);
        } else if (artists.contains(source)) {
            if (!map.containsKey("artist-ext")) map.put("artist-ext", 1);
            else map.put("artist-ext", (int) map.get("artist-ext") + 1);
        } else if (node.equals("SOURCE_ARTIST")) {
            if (!map.containsKey("return-ext")) map.put("return-ext", 1);
            else map.put("return-ext", (int) map.get("return-ext") + 1);
        }
    }

    public static void newgetRecommendedArtists(String artist) {
        Map<String, Map<String, Integer>> artistsMap = new HashMap<>();

        List<String> neighbors = Graphs.neighborListOf(graph, artist);

        for (String neighbor : neighbors) {
            if (!artistsMap.containsKey(neighbor)) {
                Map<String, Integer> artistMap = new HashMap<>();
                updateNodeFirstDegree(artistMap);
                artistsMap.put(neighbor, artistMap);
            } else updateNodeFirstDegree(artistsMap.get(neighbor));
            List<String> secondNeighbors = Graphs.neighborListOf(graph, neighbor);
            for (String secondNeighbor : secondNeighbors) {
                if (!secondNeighbor.equals(artist)) {
                    if (!artistsMap.containsKey(secondNeighbor)) {
                        Map<String, Integer> artistMap = new HashMap<>();
                        updateNodeSecondDegree(neighbor, secondNeighbor, artistMap);
                        artistsMap.put(secondNeighbor, artistMap);
                    } else updateNodeSecondDegree(neighbor, secondNeighbor, artistsMap.get(secondNeighbor));
                } else {
                    updateNodeSecondDegree(neighbor, "SOURCE_ARTIST", artistsMap.get(neighbor));
                }
                List<String> thirdNeighbors = Graphs.neighborListOf(graph, secondNeighbor);
                for (String thirdNeighbor : thirdNeighbors) {
                    if (!secondNeighbor.equals(artist)) {
                        if (neighbors.contains(thirdNeighbor)) {
                            if (!artistsMap.containsKey(thirdNeighbor)) {
                                Map<String, Integer> artistMap = new HashMap<>();
                                updateNodeThirdDegree(secondNeighbor, thirdNeighbor, artistMap);
                                artistsMap.put(thirdNeighbor, artistMap);
                            } else updateNodeThirdDegree(neighbor, secondNeighbor, artistsMap.get(secondNeighbor));
                        } else if (thirdNeighbor.equals(artist)) {
                            updateNodeThirdDegree(secondNeighbor, "SOURCE_ARTIST", artistsMap.get(secondNeighbor));
                        }
                    }
                }
            }
        }

        Map<String, Double> map = new HashMap<>();

        for (String key : artistsMap.keySet()) {
            Map<String, Integer> artistMap = artistsMap.get(key);
            Double total = 0.0;
            for (String key2 : artistMap.keySet()) {
                if (key2.equals("artist")) {
                    total += 20.0 * artistMap.get(key2);
                } else if (key2.equals("genre")) {
                    total += 5.0 * artistMap.get(key2);
                } else if (key2.equals("category")) {
                    total += 8.0 * artistMap.get(key2);
                } else if (key2.equals("source")) {
                    total += 40.0 * artistMap.get(key2);
                } else if (key2.equals("label")) {
                    total += 0.5 * artistMap.get(key2);
                } else if (key2.equals("artist-ext")) {
                    total += 1.0 * artistMap.get(key2);
                } else if (key2.equals("genre-ext")) {
                    total += 0.1 * artistMap.get(key2);
                } else if (key2.equals("category-ext")) {
                    total += 0.05 * artistMap.get(key2);
                }
            }
            if (!artistMap.containsKey("genre") && !artistMap.containsKey("genre-ext")) total *= 0.5;
            map.put(key, total);
        }

        Set<Map.Entry<String, Double>> set = map.entrySet();
        List<Map.Entry<String, Double>> list = new ArrayList<>(set);
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>()
        {
            public int compare( Map.Entry<String, Double> o1, Map.Entry<String, Double> o2 )
            {
                return (o2.getValue()).compareTo( o1.getValue() );
            }
        });
        for (Map.Entry<String, Double> entry : list) {
            if (verifiedArtists.contains(entry.getKey())) {
                System.out.println(entry.getKey() + ": " + entry.getValue() + " - " + artistsMap.get(entry.getKey()));
            }
        }
    }

    public static List<Map.Entry<String, Integer>> getRecommendedArtists(String artist) {
        Map<String, Integer> map = new HashMap<>();

        List<String> neighbors = Graphs.neighborListOf(graph, artist);

        for (String neighbor : neighbors) {
            if (!neighbor.equals(artist)) {
                updateNodeScore(artist, neighbor, 1, map);
            }
            List<String> secondNeighbors = Graphs.neighborListOf(graph, neighbor);
            for (String secondNeighbor : secondNeighbors) {
                if (!secondNeighbor.equals(artist)) {
                    updateNodeScore(neighbor, secondNeighbor, 1, map);
                }
                List<String> thirdNeighbors = Graphs.neighborListOf(graph, secondNeighbor);
                for (String thirdNeighbor : thirdNeighbors) {
                    if (neighbors.contains(thirdNeighbor) && !secondNeighbor.equals(artist)) {
                        updateNodeScore(neighbor, secondNeighbor, 1, map);
                    } else if (thirdNeighbor.equals(artist) && !secondNeighbor.equals(artist)) {
                        updateNodeScore(neighbor, secondNeighbor, 1, map);
                    }
                }
            }
        }

        Set<Map.Entry<String, Integer>> set = map.entrySet();
        List<Map.Entry<String, Integer>> list = new ArrayList<>(set);
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>()
        {
            public int compare( Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2 )
            {
                return (o2.getValue()).compareTo( o1.getValue() );
            }
        });
        List<Map.Entry<String, Integer>> results = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : list) {
            if (verifiedArtists.contains(entry.getKey())) {
                results.add(entry);
            }
        }
        return results;
    }

    public static boolean validateVevoArtist(String vevoId) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT is_enabled, total_videos FROM artist_data WHERE artist_id = '" + vevoId + "'");

            if (rs.next()) {
                if ((rs.getInt("is_enabled") == 1) && (rs.getInt("total_videos") > 0)) {
                    ResultSet rs2 = stmt.executeQuery("SELECT COUNT(*) FROM video_data WHERE artist_id = '" + vevoId + "' AND is_active AND NOT is_live;");
                    if (rs2.next()) {
                        if (rs2.getInt("COUNT(*)") > 0) {
                            rs.close();
                            rs2.close();
                            stmt.close();
                            return true;
                        }
                    }
                }
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("EXCEPTION ON ARTIST: " + vevoId);
        }

        return false;
    }

    public static void main(String[] args) throws FileNotFoundException {
        WikiArtistNetwork wiki = new WikiArtistNetwork();

        File file = new File(wiki.getClass().getClassLoader().getResource("wiki-data-ALL-FINAL.json").getPath());
        File file2 = new File(wiki.getClass().getClassLoader().getResource("artists-ALL-FINAL.json").getPath());

        System.out.println("Loading JSON...");

        JSONObject wikiJson = new JSONObject(new Scanner(file).useDelimiter("\\Z").next());
        JSONArray artistJson = new JSONArray(new Scanner(file2).useDelimiter("\\Z").next());

        System.out.println("Building graph...");

        wiki.buildGraph(wikiJson);

        System.out.println("Graph built");

        System.out.println("Num genres: " + genres.size());
        System.out.println("Num categories: " + categories.size());
        System.out.println("Num labels: " + labels.size());
        System.out.println("Num aritsts: " + artists.size());
        System.out.println("Num vertices: " + graph.vertexSet().size());

        for (int i = 0; i < artistJson.length(); i++) {
            JSONObject artist = artistJson.getJSONObject(i);
            if (artist.getBoolean("verified") && !artist.getString("title").equals("")) {
                verifiedArtists.add(artist.getString("title"));
                vevoToWiki.put(artist.getString("vevo"), artist.getString("title"));
                wikiToVevo.put(artist.getString("title"), artist.getString("vevo"));
            }
        }

        dataSource = new MysqlDataSource();

        dataSource.setUser("vevo_aurora");
        dataSource.setPassword("foB-E6r-UsN-3y5");
        dataSource.setServerName("analytics-staging.cluster-c87qoqg6lbh2.us-east-1.rds.amazonaws.com");
        dataSource.setDatabaseName("vevoaurora");


        try {
            conn = dataSource.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String artist = "";

        newgetRecommendedArtists(artist);

//        List<Map.Entry<String, Integer>> results = getRecommendedArtists(artist);
//
//        System.out.println(results.size());
//
//        Float max = results.get(0).getValue().floatValue();
//        System.out.println(max);
//        Map<String, Float> finalResults = new HashMap<>();
//
//        int count = 0;
//
//        for (Map.Entry<String, Integer> entry : results) {
//            if (count > 50) break;
//            String wikiTitle = entry.getKey();
//            String vevoId = wikiToVevo.get(wikiTitle);
//            System.out.println("Validating " + wikiTitle);
//            try {
//                if (validateVevoArtist(vevoId)) {
//                    finalResults.put(vevoId, entry.getValue().floatValue()/max);
//                    count++;
//                }
//            } catch (Exception e) {
//                continue;
//            }
//        }
//
//        for (Map.Entry<String, Integer> entry : results) {
//            if (finalResults.containsKey(wikiToVevo.get(entry.getKey()))) System.out.println(wikiToVevo.get(entry.getKey()) + ": " + finalResults.get(wikiToVevo.get(entry.getKey())));
//        }
//
//        try {
//            conn.close();
//        } catch (SQLException e) {
//            e.printStackTrace();
//            System.exit(1);
//        }

//        ArrayList<String> artistGenres = getArtistGenres(artist, wikiJson);
//
//        Map<String, Integer> map = new HashMap<>();
//
//        List<String> neighbors = Graphs.neighborListOf(graph, artist);
//
//        for (String neighbor : neighbors) {
//            if (!neighbor.equals(artist)) {
//                if (!map.containsKey(neighbor)) map.put(neighbor, 1);
//                else map.put(neighbor, map.get(neighbor) + 1);
//            }
//            List<String> nodes = Graphs.neighborListOf(graph, neighbor);
//            for (String secondNeighbor : nodes) {
//                if (!secondNeighbor.equals(artist)) {
//                    if (!map.containsKey(secondNeighbor)) map.put(secondNeighbor, 1);
//                    else map.put(secondNeighbor, map.get(secondNeighbor) + 1);
//                }
//                List<String> nodeNeighbors = Graphs.neighborListOf(graph, secondNeighbor);
//                for (String nodeNeighbor : nodeNeighbors) {
//                    if (neighbors.contains(nodeNeighbor)) {
//                        if (!map.containsKey(secondNeighbor)) map.put(secondNeighbor, 1);
//                        else map.put(secondNeighbor, map.get(secondNeighbor) + 1);
//                    }
//                }
//            }
//        }
//
//        Set<Map.Entry<String, Integer>> set = map.entrySet();
//        List<Map.Entry<String, Integer>> list = new ArrayList<>(set);
//        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>()
//        {
//            public int compare( Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2 )
//            {
//                return (o2.getValue()).compareTo( o1.getValue() );
//            }
//        });
//
//        for (Map.Entry<String, Integer> entry : list) {
//            if (verifiedArtists.contains(entry.getKey())) {
//                System.out.print(entry);
//                for (String genre : getArtistGenres(entry.getKey(), wikiJson)) {
//                    if (artistGenres.contains(genre)) System.out.print(" *");
//                }
//                System.out.println();
//            }
//        }
    }

}
