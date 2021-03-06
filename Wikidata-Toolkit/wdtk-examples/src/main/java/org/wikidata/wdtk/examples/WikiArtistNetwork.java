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
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Tore Banta on 7/13/17.
 *
 * Using information drawn from wikipedia, this class constructs a graph representation of artist, genre, category and
 * music label relationships with JGraphT. With the graph constructed, this class produces an artist recommendation
 * table based on a graph traversal algorithm starting with a given artist in the dataset.
 *
 */
public class WikiArtistNetwork {

    static DirectedGraph<String, DefaultEdge> graph = new DirectedPseudograph<>(DefaultEdge.class);

    static Connection conn;
    static MysqlDataSource dataSource;

    // Arrays for determining the type of a node given the string representation
    static ArrayList<String> artists = new ArrayList<>();
    static ArrayList<String> genres = new ArrayList<>();
    static ArrayList<String> labels = new ArrayList<>();
    static ArrayList<String> categories = new ArrayList<>();
    static ArrayList<String> verifiedArtists = new ArrayList<>();
    static ArrayList<String> validArtists = new ArrayList<>();

    // Conversion maps for wiki QID <-> Vevo artist ID
    static Map<String, String> wikiToVevo = new HashMap<>();
    static Map<String, String> vevoToWiki = new HashMap<>();

    /**
     * Constructs JGraphT graph component from a list of genres, linking the artist which is a part of those genres to
     * the newly added (and existing) genre nodes
     *
     * @param genreArray    list of genres
     *
     * @param artist        artist from which genre array originated
     */
    public static void buildComponentFromGenres(JSONArray genreArray, String artist) {
        // Noticed that artists with > 7 categories skew results
        if (genreArray.length() > 7) System.out.println(artist + " has " + genreArray.length());
        // Add genres as nodes, link to existing graph component
        for (int i = 0; i < genreArray.length(); i++) {
            String genre = genreArray.getString(i);
            if (!genres.contains(genre)) {
                genres.add(genre);
                if (!graph.containsVertex(genre)) graph.addVertex(genre);
            }
            // Genres connections are undirected
            graph.addEdge(artist, genre);
            graph.addEdge(genre, artist);
        }
    }

    /**
     * Constructs JGraphT graph component from a list of labels, linking the artist which is a part of those labels to
     * the newly added (and existing) label nodes
     *
     * @param labelArray    list of record labels
     *
     * @param artist        artist from which label array originated
     */
    public static void buildComponentFromLabels(JSONArray labelArray, String artist) {
        // Add labels as nodes, link to existing graph component
        for (int i = 0; i < labelArray.length(); i++) {
            String label = labelArray.getString(i);
            if (!labels.contains(label)) {
                labels.add(label);
                if (!graph.containsVertex(label)) graph.addVertex(label);
            }
            // Record label connections are undirected
            graph.addEdge(artist, label);
            graph.addEdge(label, artist);
        }
    }

    /**
     * Constructs JGraphT graph component from a list of categories, linking the artist which is a part of those
     * categories to the newly added (and existing) category nodes
     *
     * @param categoryArray list of categories
     *
     * @param artist        artist from which category array originated
     */
    public static void buildComponentFromCategories(JSONArray categoryArray, String artist) {
        // Add categories as nodes, link to existing graph component
        for (int i = 0; i < categoryArray.length(); i++) {
            String category = categoryArray.getString(i);
            if (!categories.contains(category)) {
                categories.add(category);
                if (!graph.containsVertex(category)) graph.addVertex(category);
            }
            // Category connections are undirected
            graph.addEdge(artist, category);
            graph.addEdge(category, artist);
        }
    }

    /**
     * Constructs JGraphT graph component from a list of associated acts, linking the artist to their associated acts
     * (directed), creating the associated act's artist node if it does not yet exist
     *
     * @param associatedArray   list of associated artists
     *
     * @param artist            artist from which associated acts originated
     */
    public static void buildComponentFromAssociated(JSONArray associatedArray, String artist) {
        for (int i = 0; i < associatedArray.length(); i++) {
            // Add asscoiated artists as nodes, link to existing graph component
            String associatedArtist = associatedArray.getString(i);
            if (!artists.contains(associatedArtist)) {
                artists.add(associatedArtist);
                if (!graph.containsVertex(associatedArtist)) graph.addVertex(associatedArtist);
            }
            // Associated act connections are directed! smaller artist may link to bigger artist
            //if (!graph.containsEdge(artist, associatedArtist)) graph.addEdge(artist, associatedArtist);
            graph.addEdge(artist, associatedArtist);
        }
    }

    /**
     * Constructs the complete artist graph using the json wiki data - each element of the json dataset corresponds to
     * a node in the graph, with artists linking to each other, their genres, categories, and music labels. Each node
     * is identified by the unique string EN wikipedia title they correspond to
     *
     * @param wikiData  json containing wiki data dump collected by WikiArtistParser
     */
    public static void buildGraph(JSONObject wikiData) {
        // Iterate through wikidata JSON objects, generating/updating subgraphs for each article node
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

//    public static void updateNodeScore(String source, String node, int weight, Map map) {
//        if (!labels.contains(source) && !categories.contains(source)) {
//            if (!map.containsKey(node)) map.put(node, weight);
//            else map.put(node, (int) map.get(node) + weight);
//        } else if (labels.contains(source)) {
//            if (weight == 1) return;
//            else {
//                if (!map.containsKey(node)) map.put(node, weight - 1);
//                else map.put(node, (int) map.get(node) + weight - 1);
//            }
//        } else if (source.toLowerCase().contains("from")) {
//            if (weight == 1) return;
//            else {
//                if (!map.containsKey(node)) map.put(node, weight - 1);
//                else map.put(node, (int) map.get(node) + weight - 1);
//            }
//        } else {
//            if (!map.containsKey(node)) map.put(node, weight);
//            else map.put(node, (int) map.get(node) + weight);
//        }
//    }

    /**
     * Updates node scoring map for a node one degree of separation (level) away from source artist node
     *
     * @param map   map tracking number of times a given node is encountered during traversal, and the source node from
     *              which a given node is traversed to
     */
    public static void updateNodeFirstDegree(Map map) {
        if (!map.containsKey("source")) map.put("source", 1);
        else map.put("source", (int) map.get("source") + 1);
    }

    /**
     * Updates node scoring map for a node two degrees of separation (levels) away from source artist node
     *
     * @param source    source artist node of graph traversal
     *
     * @param node      node which was encountered during traversal, in order to check if node is source
     *
     * @param map       map tracking number of times a given node is encountered during traversal, and the source node from
     *                  which a given node is traversed to
     */
    public static void updateNodeSecondDegree(String source, String node, Map map) {
        // Check what 'type' of source node was traversed from, updates map to indicate traversal
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

    /**
     * Updates node scoring map for a node three degrees of separation (levels) away from source artist node
     *
     * @param source    source artist node of graph traversal
     *
     * @param node      node which was encountered during traversal, in order to check if node is source
     *
     * @param map       map tracking number of times a given node is encountered during traversal, and the source node
     *                  from which a given node is traversed to
     */
    public static void updateNodeThirdDegree(String source, String node, Map map) {
        // -ext traversal type indicates traversal occured at 2nd/3rd level of graph centered at source artist
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

    /**
     * Primary function used to calculate sorted recommendations for a given source artist, with each artist's
     * recommendation metric corresponding to a weighted sum of the number of times the artist was encountered during
     * the traversal from source artist
     *
     * @param artist    source artist node for which recommendations will be generated
     *
     * @param max       maximum number of recommended artists to return
     *
     * @return          sorted list of recommended artists and their 'score' being a ratio of the max value in the
     *                  results (i.e. the top recommendation will always be 1.0, all subsequent results <1.0 and >0.0
     */
    public static List<Map.Entry<String, Float>> getRecommendedArtists(String artist, Integer max) {
        // Map of artists, and their associated maps for traversal encounters
        Map<String, Map<String, Integer>> artistsMap = new HashMap<>();

        List<String> neighbors = Graphs.neighborListOf(graph, artist);

        // Iterate through source artist's neighbors (1st level)
        for (String neighbor : neighbors) {
            if (!artistsMap.containsKey(neighbor)) {
                // Add map for node encountered
                Map<String, Integer> artistMap = new HashMap<>();
                updateNodeFirstDegree(artistMap);
                artistsMap.put(neighbor, artistMap);
            } else updateNodeFirstDegree(artistsMap.get(neighbor)); // Update map for node encountered
            
            // Iterate through second degree neighbors (2nd level)
            List<String> secondNeighbors = Graphs.neighborListOf(graph, neighbor);
            for (String secondNeighbor : secondNeighbors) {
                if (!secondNeighbor.equals(artist)) {
                    if (!artistsMap.containsKey(secondNeighbor)) {
                        // Add map for node encountered
                        Map<String, Integer> artistMap = new HashMap<>();
                        updateNodeSecondDegree(neighbor, secondNeighbor, artistMap);
                        artistsMap.put(secondNeighbor, artistMap);
                    } else updateNodeSecondDegree(neighbor, secondNeighbor, artistsMap.get(secondNeighbor));    // Update map for node encountered
                } else {
                    updateNodeSecondDegree(neighbor, "SOURCE_ARTIST", artistsMap.get(neighbor));
                }
                
                // Iterate through third degree neighbors (3rd level)
                List<String> thirdNeighbors = Graphs.neighborListOf(graph, secondNeighbor);
                for (String thirdNeighbor : thirdNeighbors) {
                    if (!secondNeighbor.equals(artist)) {
                        if (neighbors.contains(thirdNeighbor)) {
                            if (!artistsMap.containsKey(thirdNeighbor)) {
                                // Add map for node encountered
                                Map<String, Integer> artistMap = new HashMap<>();
                                updateNodeThirdDegree(secondNeighbor, thirdNeighbor, artistMap);
                                artistsMap.put(thirdNeighbor, artistMap);
                            } else updateNodeThirdDegree(neighbor, secondNeighbor, artistsMap.get(secondNeighbor)); // Update map for node encountered
                        } else if (thirdNeighbor.equals(artist)) {
                            updateNodeThirdDegree(secondNeighbor, "SOURCE_ARTIST", artistsMap.get(secondNeighbor));
                        }
                    }
                }
            }
        }

        // Map for storing weighted sum of node encounters in traversal
        Map<String, Double> map = new HashMap<>();

        for (String key : artistsMap.keySet()) {
            Map<String, Integer> artistMap = artistsMap.get(key);
            Double total = 0.0;
            // Traversal weights
            for (String key2 : artistMap.keySet()) {
                if (key2.equals("artist")) {
                    total += 20.0 * artistMap.get(key2);
                } else if (key2.equals("genre")) {
                    total += 8.0 * artistMap.get(key2);
                } else if (key2.equals("category")) {
                    total += 5.0 * artistMap.get(key2);
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
            // Catch edge case where artists not linked at all by musical genre
            if (!artistMap.containsKey("genre") && !artistMap.containsKey("genre-ext")) total *= 0.5;
            map.put(key, total);
        }
        
        // Sort weighted sums and determine maximum value

        Set<Map.Entry<String, Double>> set = map.entrySet();
        List<Map.Entry<String, Double>> list = new ArrayList<>(set);
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>()
        {
            public int compare( Map.Entry<String, Double> o1, Map.Entry<String, Double> o2 )
            {
                return (o2.getValue()).compareTo( o1.getValue() );
            }
        });
        Double maxEntry = null;
        int count = 0;
        List<Map.Entry<String, Float>> recommendations = new ArrayList<>();
        for (Map.Entry<String, Double> entry : list) {
            if (verifiedArtists.contains(entry.getKey()) && validArtists.contains(entry.getKey())) {
                if (count < 10) System.out.println(entry.getKey() + ": " + entry.getValue() + " - " + artistsMap.get(entry.getKey()));
                if (maxEntry == null) {
                    maxEntry = entry.getValue();
                }
                // Scoring as ratio of weighted sum to maximum weighted sum in results (float 1.0 > x > 0.0)
                Double score = entry.getValue() / maxEntry;
                recommendations.add(new AbstractMap.SimpleEntry<>(wikiToVevo.get(entry.getKey()), score.floatValue()));
                count++;
            }
            if (recommendations.size() == max) break;
        }
        return recommendations;
    }

//    public static List<Map.Entry<String, Integer>> getRecommendedArtists(String artist) {
//        Map<String, Integer> map = new HashMap<>();
//
//        List<String> neighbors = Graphs.neighborListOf(graph, artist);
//
//        for (String neighbor : neighbors) {
//            if (!neighbor.equals(artist)) {
//                updateNodeScore(artist, neighbor, 1, map);
//            }
//            List<String> secondNeighbors = Graphs.neighborListOf(graph, neighbor);
//            for (String secondNeighbor : secondNeighbors) {
//                if (!secondNeighbor.equals(artist)) {
//                    updateNodeScore(neighbor, secondNeighbor, 1, map);
//                }
//                List<String> thirdNeighbors = Graphs.neighborListOf(graph, secondNeighbor);
//                for (String thirdNeighbor : thirdNeighbors) {
//                    if (neighbors.contains(thirdNeighbor) && !secondNeighbor.equals(artist)) {
//                        updateNodeScore(neighbor, secondNeighbor, 1, map);
//                    } else if (thirdNeighbor.equals(artist) && !secondNeighbor.equals(artist)) {
//                        updateNodeScore(neighbor, secondNeighbor, 1, map);
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
//        List<Map.Entry<String, Integer>> results = new ArrayList<>();
//        for (Map.Entry<String, Integer> entry : list) {
//            if (verifiedArtists.contains(entry.getKey())) {
//                results.add(entry);
//            }
//        }
//        return results;
//    }

    // idk why this function is in this file too...
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

    /**
     * Writes headers of csv table of recommendation results using print writer
     *
     * @param writer    writer for the table
     *
     * @param max       maximum number of artist/score pairs per row (max number of recomendations)
     */
    public static void writeTableHeaders(PrintWriter writer, Integer max) {
        // Variable number of columns, 2*max (Artist/Score) + 1 (Source)
        StringBuilder header = new StringBuilder();
        header.append("source");
        header.append(',');
        for (Integer i = 1; i < max; i++) {
            header.append("A" + i.toString());
            header.append(',');
            header.append("S" + i.toString());
            header.append(',');
        }
        header.append("A" + max.toString());
        header.append(',');
        header.append("S" + max.toString());
        writer.println(header.toString());
    }

    /**
     * Writes artist recommendations to row of csv table
     *
     * @param writer            writer for the table
     *
     * @param sourceVevo        source artist's vevo id
     *
     * @param recommendations   list of artist recommendations
     *
     * @param max               maximum number of artist recommendations per row
     */
    public static void writeArtistRecommendations(PrintWriter writer, String sourceVevo, List<Map.Entry<String, Float>> recommendations, Integer max) {
        StringBuilder row = new StringBuilder();
        row.append(sourceVevo);
        row.append(',');
        for (Map.Entry<String, Float> entry : recommendations) {
            row.append(entry.getKey());
            row.append(',');
            row.append(entry.getValue().toString());
            row.append(',');
        }
        if (recommendations.size() < max) {
            int diff = max - recommendations.size();
            for (int i = 0; i < diff; i++) {
                row.append(',');
                row.append(',');
            }
        }
        row.deleteCharAt(row.length() - 1);
        writer.println(row.toString());
    }

    /**
     * Where the magic happens: uncommented code is typically required for setup, then blocks of commented code
     * execute scripts using resource input files, then write results to a top level output file
     *
     * @param args
     * @throws FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException {
        WikiArtistNetwork wiki = new WikiArtistNetwork();

        File file = new File(wiki.getClass().getClassLoader().getResource("wiki-data-ALL-FINAL.json").getPath());
        File file2 = new File(wiki.getClass().getClassLoader().getResource("artists-ALL-VALIDATED.json").getPath());

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

        // Populate list of 'verified' (confident match) vevo artists to limit nodes which are scored by graph traversal
        for (int i = 0; i < artistJson.length(); i++) {
            JSONObject artist = artistJson.getJSONObject(i);
            if (artist.getBoolean("verified") && !artist.getString("title").equals("")) {
                verifiedArtists.add(artist.getString("title"));
                vevoToWiki.put(artist.getString("vevo"), artist.getString("title"));
                wikiToVevo.put(artist.getString("title"), artist.getString("vevo"));
            }
            if (artist.getBoolean("valid") && !artist.getString("title").equals("")) {
                validArtists.add(artist.getString("title"));
            }
        }

//        String artist = "God Is an Astronaut";
//
//        List<Map.Entry<String, Float>> recommendations1 = getRecommendedArtists(artist, 200);
//
//        String artist2 = "Anderson Paak";
//
//        List<Map.Entry<String, Float>> recommendations2 = getRecommendedArtists(artist2, 200);
        
        /* Generate artist recommendation table! Takes about 4 hours to run on 15k artists */

        Integer MAX_ARTISTS = 200;
        Integer numVerified = verifiedArtists.size();

        PrintWriter writer = null;

        try {
            writer = new PrintWriter("artist-recommendation.csv", "UTF-8");
        } catch (IOException e) {
            System.out.println("Could not write result file");
            System.exit(1);
        }

        writeTableHeaders(writer, MAX_ARTISTS);

        for (int i = 0; i < artistJson.length(); i++) {
            JSONObject artist = artistJson.getJSONObject(i);
            try {
                if (artist.getBoolean("verified") && !artist.getString("title").equals("")) {
                    String artistTitle = artist.getString("title");
                    String artistVevo = artist.getString("vevo");
                    System.out.println("*********** " + artistTitle + ": " + artistVevo + " ***********");
                    System.out.println("########### " + i + " of " + numVerified + " ###########");
                    List<Map.Entry<String, Float>> recommendations = getRecommendedArtists(artistTitle, MAX_ARTISTS);
                    if (recommendations.size() > 0) writeArtistRecommendations(writer, artistVevo, recommendations, MAX_ARTISTS);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("EXCEPTION ON ARTIST: " + artist.toString());
            }
        }

        writer.close();

        /* ************** Everything below was just testing code, i think ************** */

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
