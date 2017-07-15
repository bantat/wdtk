package org.wikidata.wdtk.examples;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedPseudograph;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Created by torebanta on 7/13/17.
 */
public class WikiArtistNetwork {

    static Graph<String, DefaultEdge> graph = new DirectedPseudograph<>(DefaultEdge.class);

    static ArrayList<String> artists = new ArrayList<>();
    static ArrayList<String> genres = new ArrayList<>();
    static ArrayList<String> labels = new ArrayList<>();
    static ArrayList<String> categories = new ArrayList<>();
    static ArrayList<String> vevoArtists = new ArrayList<>();

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
            vevoArtists.add(name);

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

    public static void main(String[] args) throws FileNotFoundException {
        WikiArtistNetwork wiki = new WikiArtistNetwork();

        File file = new File(wiki.getClass().getClassLoader().getResource("wiki-data-final-v1.json").getPath());

        System.out.println("Loading JSON...");

        JSONObject wikiJson = new JSONObject(new Scanner(file).useDelimiter("\\Z").next());

        System.out.println("Building graph...");

        wiki.buildGraph(wikiJson);

        System.out.println("Graph built");

        String artist = "TV on the Radio";

        BreadthFirstInfiniterator<String, DefaultEdge> iterator = new BreadthFirstInfiniterator<>(graph, artist);

        Map<String, Integer> map = new HashMap<>();

        Integer count = 0;

        while (iterator.hasNext() && count < 10000) {
            String item = iterator.next();
            if (wiki.vevoArtists.contains(item) && !item.equals(artist)) {
                if (!map.containsKey(item)) map.put(item, 1);
                else map.put(item, map.get(item) + 1);
            }
            count++;
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

        System.out.println();
        System.out.println("Searching for artists related to " + artist);
        System.out.println();
        System.out.println("Results:");

        for (int i = 0; i < 10; i++) {
            System.out.println(list.get(i));
        }
    }

}
