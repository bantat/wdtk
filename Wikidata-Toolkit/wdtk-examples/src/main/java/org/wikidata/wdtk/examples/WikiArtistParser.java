package org.wikidata.wdtk.examples;

import com.mysql.cj.jdbc.MysqlDataSource;
import javafx.util.converter.BigIntegerStringConverter;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.SiteLink;
import org.wikidata.wdtk.wikibaseapi.ApiConnection;
import org.wikidata.wdtk.wikibaseapi.WikibaseDataFetcher;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;

import javax.script.*;
import java.io.*;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by Tore Banta on 7/5/17.
 *
 * This class provides functionality for parsing information from Wikipedia given an artist wiki entity.
 * Functions use the EN wiki page for a given entity, and use the Wikipedia API to query specific information from
 * articles and parse the information into string properties stored in JSON.
 *
 */
public class WikiArtistParser {

    // Wikipedia API for scraping article info
    static ApiConnection wbapi = new ApiConnection("https://en.wikipedia.org/w/api.php");
    static WikibaseDataFetcher wbdf = WikibaseDataFetcher.getWikidataDataFetcher();
    
    // Script engine/invocables for running javascript module 'wtf_wikipedia'
    static Invocable invocable;
    static ScriptEngine engine;

    // Aurora connection for querying additional data
    static Connection conn;
    static MysqlDataSource dataSource;

    /**
     * Extra helper function to add view counts to artist json in order to find unmatched popular artists
     *
     * @param filePath  an absolute file path giving the location of artist data .csv file - generated from aurora
     *                  sql table
     *
     * @return          a list of json objects representing artists in the input table and their given view counts
     *
     * @throws IOException
     */
    public JSONObject parseCSVtoJsonViews(String filePath) throws IOException {
        try (Reader in = new FileReader(filePath);) {
            Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader(
                    "artist_id+1:13", "is_lift", "is_enabled", "total_views", "month_views", "week_views", "day_views",
                    "twitter_id", "facebook_id", "name", "total_videos").parse(in);

            System.out.println("Records built");
            Boolean header = true;
            JSONObject artists = new JSONObject();


            for (CSVRecord record : records) {
                if (header) header = false;

                else {
                    // Add viewcount property to artist json for finding high profile unmatched artists
                    if (record.get("total_views").length() < 4 && !record.get("total_views").equals("")
                            && !record.get("total_videos").equals("")) {
                        try {
                            if (Integer.parseInt(record.get("total_views")) == 0
                                    || Integer.parseInt(record.get("total_videos")) == 0) continue;
                        } catch (java.lang.NumberFormatException e) {
                            continue;
                        }
                    }
                    if (!Pattern.matches("^[a-z-0-9]+$", record.get("artist_id+1:13"))) continue;
                    if (Pattern.matches("^[0-9]+$", record.get("artist_id+1:13"))) continue;
                    String vevoId = record.get("artist_id+1:13");
                    String views = "0";
                    try {
                        views = record.get("total_views");
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println(record.get("total_views"));
                        System.out.println(vevoId);
                    }

                    artists.put(vevoId, views);
                }
            }
            return artists;
        }
    }

    /**
     * Updates an artist JSONObject to contain EN wikipedia article title and labels (shorthand, exact names) for the
     * wiki entity
     *
     * @param artistDocument    wikidata document corresponding to the artist's QID
     *
     * @param artistJson        json representing artist properties, being updated with 'title' and 'label' attributes
     *
     * @return                  updated artist json containing new attributes
     */
    public JSONObject getWikiPropertiesFromDocument(ItemDocument artistDocument, JSONObject artistJson) {
        artistJson.put("title", "");
        artistJson.put("label", "");
        
        // Get EN title and label properties stored in ItemDocument
        try {
            Map<String, SiteLink> links = artistDocument.getSiteLinks();
            SiteLink link = links.get("enwiki");
            artistJson.put("title", link.getPageTitle());
        } catch (NullPointerException e) {
            System.out.println("No enwiki title");
            artistJson.put("title", "");
        }

        try {
            artistJson.put("label", artistDocument.findLabel("en"));
        } catch (NullPointerException e) {
            System.out.println("No enwiki label");
            artistJson.put("label", "");
        }
        
        // Didn't bother querying alias values for further crossreferencing datapoints
//        ArrayList<String> aliasArray = new ArrayList<>();
//
//        Map<String, List<MonolingualTextValue>> aliases = artistDocument.getAliases();
//        List<MonolingualTextValue> englishAliases = aliases.get("en");
//        for (MonolingualTextValue alias : englishAliases) {
//            aliasArray.add(alias.getText());
//        }
//
//        JSONArray aliasesJson = new JSONArray(aliasArray);
//        artistJson.put("aliases", aliasesJson);

        return artistJson;
    }

    /**
     * Returns a list of category name/identifiers for a given wikipedia article title (categories which contain that
     * wikipedia entry)
     *
     * @param wikiTitle title of the wikipedia article categories are being drawn from
     *
     * @return          list of categories (unique strings with category names prefixed by 'Category:')
     *
     * @throws IOException
     */
    public ArrayList<String> getCategoriesFromTitle(String wikiTitle) throws IOException {
        // Wikipedia article categories API query parameters
        Map<String, String> params = new HashMap<>();
        params.put("action", "query");
        params.put("format", "json");
        params.put("prop", "categories");
        params.put("clshow", "!hidden");
        params.put("cllimit", "25");
        params.put("titles", wikiTitle);

        // Use wikibase api to get article categories stored as JSON
        InputStream response = this.wbapi.sendRequest("GET", params);
        StringWriter writer = new StringWriter();
        IOUtils.copy(response, writer);     // "UTF-8" encoding arg?
        JSONObject jsonWrapper = new JSONObject(writer.toString());
        JSONObject results = jsonWrapper.getJSONObject("query").getJSONObject("pages");
        JSONObject item = results.getJSONObject(results.keys().next().toString());
        JSONArray categoriesJson = item.getJSONArray("categories");

        // Parse out string titles from JSON
        ArrayList<String> categories = new ArrayList<>();

        for (int i = 0; i < categoriesJson.length(); i++) {
            JSONObject jsonResult = categoriesJson.getJSONObject(i);
            categories.add(jsonResult.get("title").toString());
        }

        return categories;
    }

    /**
     * Returns 'infobox' text from a wikipedia article. The infobox is located on the top right of most articles and
     * contains the most pertinent links and information for a given entity. The text is returned in wikitext syntax.
     *
     * @param wikiTitle title of the wikipedia article infobox text is being drawn from
     *
     * @return          returns a string containg wikitext formatted infobox content from the source article
     *
     * @throws IOException
     */
    public String getInfoboxFromTitle(String wikiTitle) throws IOException {
        // Wikipedia article infobox string (wikitext) API query parameters
        Map<String, String> params = new HashMap<>();
        params.put("action", "query");
        params.put("format", "json");
        params.put("prop", "revisions");
        params.put("rvprop", "content");
        params.put("rvsection", "0");
        params.put("titles", wikiTitle);

        // Use wikibase API to exectue query, parse text from JSON
        InputStream response = this.wbapi.sendRequest("GET", params);
        StringWriter writer = new StringWriter();
        IOUtils.copy(response, writer);     // "UTF-8" encoding arg?
        JSONObject jsonWrapper = new JSONObject(writer.toString());
        JSONObject results = jsonWrapper.getJSONObject("query").getJSONObject("pages");
        JSONObject item = results.getJSONObject(results.keys().next().toString());
        JSONArray revisionsJson = item.getJSONArray("revisions");
        JSONObject revision = revisionsJson.getJSONObject(0);
        String text = revision.getString("*");
        // Remove newline characters because they screw up infobox parser results
        return text.replace("\n", "");
    }

    /**
     * Returns a json object representing the information contained in an infobox wikitext string. Uses a javascript
     * shell to run parsing function from 'wtf_wikipedia' to generate json from wikitext.
     *
     * @param wikitext  wikitext formatted string representing the infobox of a wikipedia article
     *
     * @return          json containing key value properties described by infobox text
     */
    public JSONObject getInfoboxJsonFromWikitext(String wikitext) {
        // Generic Object type to store results of javascript parser call
        Object result;
        JSONObject json = new JSONObject();

        try {
            result = invocable.invokeMethod(engine.getBindings(ScriptContext.ENGINE_SCOPE).get("wtf_wikipedia"), "parse", wikitext);
            ScriptObjectMirror mirror = (ScriptObjectMirror) result;

            // Check for infobox in parser results, return JSON
            if (mirror.containsKey("infobox")) {
                ScriptObjectMirror infobox = (ScriptObjectMirror) mirror.getMember("infobox");
                json = new JSONObject(infobox);
                return json;
            }
        } catch (ScriptException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return json;
    }

    /**
     * Primary function used to construct json which describes a wikipedia article info relevant to musical qualities -
     * (associated acts, label, genres, categories) - which corresponds to an artist
     *
     * @param infoboxJson   json describing information contained in infobox component of wikipedia article
     *
     * @param categories    list of category names which the wiki article belongs to
     *
     * @return              json containing all available information parsed from wikipedia article
     */
    public JSONObject buildArtistWikiInfoJson(JSONObject infoboxJson, ArrayList<String> categories) {

        // Core attributes generated from wikipedia content:
        ArrayList<String> associated_acts = new ArrayList<>();  // List of artist title strings which are listed as associated acts
        ArrayList<String> labels = new ArrayList<>();           // Recording lables listed for artist
        ArrayList<String> genres = new ArrayList<>();           // Musical genres listed for artists
        ArrayList<String> module = new ArrayList<>();           // 'Module' contains un-categorized infobox content because of parser failure

        // Parsing info from wiki article infobox JSON object
        // All properties parsed to arrays of String titles
        
        if (infoboxJson.has("associated_acts")) {
            try {
                JSONObject links = infoboxJson.getJSONObject("associated_acts").getJSONObject("links");
                for (String artistKey : links.keySet()) {
                    String artist = links.getJSONObject(artistKey).getString("page");
                    associated_acts.add(artist);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception on associated_acts");
                //System.out.println(infoboxJson);
            }
        }

        if (infoboxJson.has("label")) {
            try {
                JSONObject links = infoboxJson.getJSONObject("label").getJSONObject("links");
                for (String artistKey : links.keySet()) {
                    String artist = links.getJSONObject(artistKey).getString("page");
                    labels.add(artist);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception on label");
                //System.out.println(infoboxJson);
            }
        }

        if (infoboxJson.has("genre")) {
            try {
                JSONObject links = infoboxJson.getJSONObject("genre").getJSONObject("links");
                for (String artistKey : links.keySet()) {
                    String artist = links.getJSONObject(artistKey).getString("page");
                    genres.add(artist);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception on genre");
                //System.out.println(infoboxJson);
            }
        }

        if (infoboxJson.has("module")) {
            try {
                JSONObject links = infoboxJson.getJSONObject("module").getJSONObject("links");
                for (String artistKey : links.keySet()) {
                    String artist = links.getJSONObject(artistKey).getString("page");
                    module.add(artist);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception on module");
                //System.out.println(infoboxJson);
            }
        }
        
        // Return parsed info in clean, minimal JSON object artist wiki data

        JSONObject artistJson = new JSONObject();

        if (associated_acts.size() > 0) {
            artistJson.put("associated", new JSONArray(associated_acts));
        }
        if (labels.size() > 0) {
            artistJson.put("labels", new JSONArray(labels));
        }
        if (genres.size() > 0) {
            artistJson.put("genres", new JSONArray(genres));
        }
        if (module.size() > 0) {
            artistJson.put("module", new JSONArray(module));
        }
        if (categories.size() > 0) {
            artistJson.put("categories", new JSONArray(categories));
        }

        return artistJson;
    }

    /**
     * 'Validates' an artist by checking that they actually have accessible videos on their profile
     *
     * @param vevoId    vevo identifier
     *
     * @return          true if artist has videos, false if they do not
     */
    public static boolean validateVevoArtist(String vevoId) {
        // Use aurora artist DB to determine if artist is active, and has videos which are accessible
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
     * Where the magic happens: uncommented code is typically required for setup, then blocks of commented code
     * execute scripts using resource input files, then write results to a top level output file
     *
     * @param args
     * @throws MediaWikiApiErrorException
     * @throws IOException
     * @throws ScriptException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws MediaWikiApiErrorException, IOException, ScriptException, InterruptedException {

        /* SETUP */

        WikiArtistParser wiki = new WikiArtistParser();

        wbdf.getFilter().setSiteLinkFilter(Collections.singleton("enwiki"));

        Set<PropertyIdValue> properties = new HashSet<>();
        wbdf.getFilter().setPropertyFilter(properties);

        // Prep javascript shell for using 'wtf_wikipedia' parser
        FileReader reader = new FileReader(wiki.getClass().getClassLoader().getResource("wtf_wikipedia-master").getPath() + "/builds/wtf_wikipedia.js");
        engine = new ScriptEngineManager().getEngineByName("nashorn");
        ScriptContext scriptCtxt = engine.getContext();
        engine.eval(reader, scriptCtxt);
        invocable = (Invocable) engine;

        dataSource = new MysqlDataSource();

        dataSource.setUser("vevo_aurora");
        dataSource.setPassword("");                 // Server name/PW left out of public repo, insert to validate artists
        dataSource.setServerName("");
        dataSource.setDatabaseName("vevoaurora");

        // This won't work without server name and password...
        try {
            conn = dataSource.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        /* SETUP COMPLETE */

        /* ************** Validate artists that have accessible videos ************** */

//        File file = new File(wiki.getClass().getClassLoader().getResource("artists-ALL-FINAL.json").getPath());
//
//        JSONArray artistArray = new JSONArray(new Scanner(file).useDelimiter("\\Z").next());
//
//        int total = artistArray.length();
//
//        for (int i = 0; i < total; i++) {
//            JSONObject artist = artistArray.getJSONObject(i);
//            System.out.println("Validating " + artist.getString("vevo"));
//            System.out.println(i + " of " + total);
//            if (validateVevoArtist(artist.getString("vevo"))) artist.put("valid", true);
//            else artist.put("valid", false);
//        }
//
//        PrintWriter writer = null;
//
//        try {
//            writer = new PrintWriter("artists-ALL-VALIDATED.json", "UTF-8");
//        } catch (IOException e) {
//            System.out.println("Could not write result file");
//            System.exit(1);
//        }
//
//        writer.print(artistArray);
//        writer.close();



        /* ************** Find Unverified Artists with Most Views ************** */

//        WikiArtistMatcher wiki2 = new WikiArtistMatcher();
//
//        JSONObject artistsJson = wiki.parseCSVtoJsonViews(wiki.getClass().getClassLoader().getResource("artists-simplified.csv").getPath());
//
//        Map<String, BigInteger> map = new HashMap<>();
//
//        for (String artist : artistsJson.keySet()) {
//            String viewString = artistsJson.getString(artist);
//            try {
//                map.put(artist, new BigInteger(viewString));
//            } catch (Exception e) {
//                map.put(artist, new BigInteger("0"));
//            }
//        }
//
//        Set<Map.Entry<String, BigInteger>> set = map.entrySet();
//        List<Map.Entry<String, BigInteger>> list = new ArrayList<>(set);
//        Collections.sort(list, new Comparator<Map.Entry<String, BigInteger>>()
//        {
//            public int compare( Map.Entry<String, BigInteger> o1, Map.Entry<String, BigInteger> o2 )
//            {
//                return (o2.getValue()).compareTo( o1.getValue() );
//            }
//        });
//
//        JSONObject verifiedArtists = new JSONObject();
//
//        for (int i = 0; i < artistArray.length(); i++) {
//            JSONObject artist = artistArray.getJSONObject(i);
//            if (artist.getBoolean("verified") && !artist.getString("title").equals("")) verifiedArtists.put(artist.getString("vevo"), artist.getString("title"));
//        }
//
//        System.out.println(verifiedArtists.length());
//
//        for (int i = 0; i < 5000; i++) {
//            String artist = list.get(i).getKey();
//            if (!verifiedArtists.has(artist)) System.out.println(artist);
//        }

//        int count = 0;
//
//        for (int i = 0; i < artistArray.length(); i++) {
//            JSONObject artist = artistArray.getJSONObject(i);
//            if (artist.getBoolean("verified") && wikiJson.has(artist.getString("title"))) count++;
//        }
//
//        System.out.println(count);

//        File file = new File(wiki.getClass().getClassLoader().getResource("artists-verified.json").getPath());
//
//        JSONArray array = new JSONArray(new Scanner(file).useDelimiter("\\Z").next());
//
//        System.out.println(array.toList().size());
//
//        for (int i = 0; i < array.length(); i++) {
//            JSONObject artist = array.getJSONObject(i);
//            if (!artist.has("wiki")) artist.put("wiki", "");
//            if (!artist.has("title")) artist.put("title", "");
//            if (!artist.has("label")) artist.put("label", "");
//        }
//
//        for (int i = 0; i < array.length(); i++) {
//            JSONObject artist = array.getJSONObject(i);
//            if (artist.getString("name").equals(artist.getString("title")) || artist.getString("name").equals(artist.getString("label"))) {
//                artist.put("verified", true);
//            }
//        }
//
//        PrintWriter writer = null;
//
//        try {
//            writer = new PrintWriter("artists-verified-string.json", "UTF-8");
//        } catch (IOException e) {
//            System.out.println("Could not write result file");
//            System.exit(1);
//        }
//
//        writer.print(array);
//        writer.close();

        /* ************** Filter Categories ************** */
        // Filter out categories which contain irrelevant info/noise

//        File file = new File(wiki.getClass().getClassLoader().getResource("wiki-data-module-fixed.json").getPath());
//
//        JSONObject wikiJson = new JSONObject(new Scanner(file).useDelimiter("\\Z").next());
//
//        ArrayList<String> categories = new ArrayList<>();
//        ArrayList<String> sharedCategories = new ArrayList<>();
//        Map<String, Integer> map = new HashMap<>();
//
//        for (String key : wikiJson.keySet()) {
//            JSONObject artist = wikiJson.getJSONObject(key);
//            if (artist.has("categories")) {
//                JSONArray categoriesArray = artist.getJSONArray("categories");
//                for (int i = 0; i < categoriesArray.length(); i++) {
//                    String item = categoriesArray.getString(i);
//                    if (!categories.contains(item)) {
//                        categories.add(item);
//                    } else if (!sharedCategories.contains(item)) {
//                        sharedCategories.add(item);
//                        map.put(item, 1);
//                    } else {
//                        map.put(item, map.get(item) + 1);
//                    }
//                }
//            }
//        }
//
////        Set<Map.Entry<String, Integer>> set = map.entrySet();
////        List<Map.Entry<String, Integer>> list = new ArrayList<>(set);
////        Collections.sort( list, new Comparator<Map.Entry<String, Integer>>()
////        {
////            public int compare( Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2 )
////            {
////                return (o2.getValue()).compareTo( o1.getValue() );
////            }
////        });
////
////        System.out.println(list);
//
////        System.out.println(categories.size() + " unique categories");
////        System.out.println(sharedCategories.size() + " shared categories");
//
//        ArrayList<String> toFilter = new ArrayList<>();
//        toFilter.add("Category:Living people");           // Massive, irrelevant category
//        toFilter.add("Category:Commandeurs of the Ordre des Arts et des Lettres");
//        toFilter.add("Officers Crosses of the Order of Merit of the Federal Republic of Germany");
//
//        // These categories were somewhat arbitrarily filtered out, can be replaced with low weighting/special treatment
//        for (String category : categories) {
//            String simplified = category.toLowerCase();
//            if (simplified.contains("births")) toFilter.add(category);
//            if (simplified.contains("deaths")) toFilter.add(category);
//            if (simplified.contains("alumni")) toFilter.add(category);
//            if (simplified.contains("winner")) toFilter.add(category);
//            if (simplified.contains("actor")) toFilter.add(category);
//            if (simplified.contains("actress")) toFilter.add(category);
////            if (simplified.contains("christian")) toFilter.add(category);   // There were a bunch of semetic categories, realized is useful for christian music
////            if (simplified.contains("jew")) toFilter.add(category);
//            if (simplified.contains("business")) toFilter.add(category);
//            if (simplified.contains("model")) toFilter.add(category);
//            if (simplified.contains("designer")) toFilter.add(category);
//        }
//
////        ArrayList<String> filtered = new ArrayList<>();
////
////        for (String category : categories) {
////            if (!toFilter.contains(category)) {
////                filtered.add(category);
////                //if (map.containsKey(category)) System.out.println(category + ": " + map.get(category));
////            }
////        }
//
//        //System.out.println(toFilter);
//        //System.out.println(toFilter.size() + " categories to be fitered out of " + categories.size());
//        //System.out.println(filtered);
//
//        for (String key : wikiJson.keySet()) {
//            JSONObject artist = wikiJson.getJSONObject(key);
//            if (artist.has("categories")) {
//                JSONArray categoriesArray = artist.getJSONArray("categories");
//                for (int i = 0; i < categoriesArray.length(); i++) {
//                    String item = categoriesArray.getString(i);
//                    if (toFilter.contains(item)) categoriesArray.remove(i);
//                }
//            }
//        }
//
//        PrintWriter writer = null;
//
//        try {
//            writer = new PrintWriter("wiki-data-categories-filtered.json", "UTF-8");
//        } catch (IOException e) {
//            System.out.println("Could not write result file");
//            System.exit(1);
//        }
//
//        writer.print(wikiJson);
//        writer.close();

        /* ************** Parse module entities ************** */
        // When wiki article has multiple sections to infobox (i.e. Donald Glover has a music and film career)
        // wikitext parser lists all links as 'module'. This code block finds what attribute a link in the module
        // belongs to, and updates artist json appropriately

//        for (String key : wikiJson.keySet()) {
//            JSONObject artist = wikiJson.getJSONObject(key);
//            if (artist.has("module")) {
//                System.out.println("Updating " + key);
//                JSONArray moduleArray = artist.getJSONArray("module");
//                ArrayList<String> artistAssociated = new ArrayList<>();
//                ArrayList<String> artistGenres = new ArrayList<>();
//                ArrayList<String> artistLabels = new ArrayList<>();
//                for (int i = 0; i < moduleArray.length(); i++) {
//                    String item = moduleArray.getString(i);
//                    if (titles.contains(item)) artistAssociated.add(item);
//                    if (genres.contains(item)) artistGenres.add(item);
//                    if (labels.contains(item)) artistLabels.add(item);
//                }
//                if (artistAssociated.size() > 0) artist.put("associated", new JSONArray(artistAssociated));
//                if (artistGenres.size() > 0) artist.put("genres", new JSONArray(artistGenres));
//                if (artistLabels.size() > 0) artist.put("labels", new JSONArray(artistLabels));
//                System.out.println(artist);
//            }
//        }
//
//        PrintWriter writer = null;
//
//        try {
//            writer = new PrintWriter("wiki-data-fix.json", "UTF-8");
//        } catch (IOException e) {
//            System.out.println("Could not write result file");
//            System.exit(1);
//        }
//
//        writer.print(wikiJson);
//        writer.close();


        /* ************** Generate wiki data ************** */
        // Parse categories, infobox content from wiki article titles
//
//        File file = new File(wiki.getClass().getClassLoader().getResource("artists-updated.json").getPath());
//
//        JSONArray array = new JSONArray(new Scanner(file).useDelimiter("\\Z").next());
//
//        ArrayList<String> wikiTitles = new ArrayList<>();
//
//        for (int i = 0; i < array.length(); i++) {
//            JSONObject artist = array.getJSONObject(i);
//            if (artist.has("title")) {
//                if (!wikiTitles.contains(artist.getString("title"))) {
//                    wikiTitles.add(artist.getString("title"));
//                }
//            }
//        }
//
//        System.out.println(wikiTitles);
//
//        PrintWriter writer = null;
//
//        try {
//            writer = new PrintWriter("wiki-data.json", "UTF-8");
//            writer.println("{");
//        } catch (IOException e) {
//            System.out.println("Could not write result file");
//            System.exit(1);
//        }
//
//        JSONObject results = new JSONObject();
//
//        int total = 1;
//        int exceptions = 0;
//        int numArtists = wikiTitles.size();
//        for (String wikiTitle : wikiTitles) {
//            if (total % 5000 == 0) {
//                System.out.println("Sleeping 2 minutes");
//                Thread.sleep(2 * 60000);
//            } else if (total % 1000 == 0) {
//                System.out.println("Sleeping 1 minute");
//                Thread.sleep(60000);
//            }
//            try {
//                String wikiText = wiki.getInfoboxFromTitle(wikiTitle);
//                JSONObject infobox = wiki.getInfoboxJsonFromWikitext(wikiText);
//                ArrayList<String> categories = wiki.getCategoriesFromTitle(wikiTitle);
//                JSONObject result = wiki.buildArtistWikiInfoJson(infobox, categories);
//                if (result.length() > 0) {
//                    System.out.println(result);
//                    results.put(wikiTitle, result);
//                    JSONObject wrapper = new JSONObject();
//                    wrapper.put(wikiTitle, result);
//                    writer.println(wrapper.toString() + ",");
//                }
//            } catch (Exception e) {
//                exceptions++;
//                e.printStackTrace();
//                System.out.println("Could not build artist json!");
//            }
//            if (total % 10 == 0) System.out.println(total + "/" + numArtists);
//            total++;
//            Thread.sleep(250);
//        }
//
//        writer.println("}");
//        writer.close();
//
//        System.out.println(exceptions + " exceptions during run");
//        System.out.println(results.length() + " results of " + numArtists);

        /* ************** Add Wiki title/label to Json ************** */
        // Use QID to get wiki title and label for data collection and artist match verification

//        JSONObject results = new JSONObject();
//        for (String artist : new ArrayList<String>()) {
//            results.put(artist, wiki.buildArtistWikiInfoJson(wiki.getInfoboxJsonFromWikitext(wiki.getInfoboxFromTitle(artist)), wiki.getCategoriesFromTitle(artist)));
//        }

//        File file = new File(wiki.getClass().getClassLoader().getResource("artists-matched.json").getPath());
//
//        JSONArray array = new JSONArray(new Scanner(file).useDelimiter("\\Z").next());
//
//        ArrayList<JSONObject> artistsJson = new ArrayList<>();
//
//        for (int i = 0; i < array.length(); i++) {
//            artistsJson.add(array.getJSONObject(i));
//        }
//
//        PrintWriter writer = null;
//
//        try {
//            writer = new PrintWriter("artists-updated.json", "UTF-8");
//            writer.println("[");
//        } catch (IOException e) {
//            System.out.println("Could not write result file");
//            System.exit(1);
//        }
//        int total = 1;
//        int titles = 0;
//        int count = artistsJson.size();
//        int exceptions = 0;
//        for (JSONObject artistJson : artistsJson) {
//            try {
//                if (total % 10000 == 0) {
//                    System.out.println("Sleeping 2 minutes");
//                    Thread.sleep(2 * 60000);
//                } else if (total % 5000 == 0) {
//                    System.out.println("Sleeping 1 minute");
//                    Thread.sleep(60000);
//                } else if (total % 1000 == 0) {
//                    System.out.println("Sleeping 30 seconds");
//                    Thread.sleep(30000);
//                }
//                if (!artistJson.getString("wiki").equals("")) {
//                    total++;
//                    JSONObject updatedArtist = wiki.getWikiPropertiesFromDocument(
//                            (ItemDocument) wbdf.getEntityDocument(artistJson.getString("wiki")), artistJson);
//                    System.out.println(updatedArtist);
//                    System.out.println(total + " of " + count);
//                    if (!updatedArtist.getString("title").equals("")) titles++;
//                    writer.println(updatedArtist.toString() + ",");
//                }
//            } catch (Exception e) {
//                System.out.println("Exception!");
//                exceptions++;
//                System.out.println("artist: " + artistJson.get("name"));
//                e.printStackTrace();
//            }
//            try {
//                Thread.sleep(250);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        writer.println("{}]");
//        writer.close();
//
//        System.out.println(exceptions + " exceptions");
//        System.out.println(titles + " titles matched of " + total);
//        JSONArray final_json = new JSONArray(artistsJson);
//        System.out.println(final_json);

//        JSONObject artistJson = new JSONObject("{\"twitter\":\"kendricklamar\",\"facebook\":\"KendrickLamarmusic\",\"wiki\":\"Q130798\",\"name\":\"Kendrick Lamar\",\"vevo\":\"kendrick-lamar\"}");
//        wiki.getWikiPropertiesFromDocument((ItemDocument) wbdf.getEntityDocument(artistJson.getString("wiki")), artistJson);
//
//        System.out.println(artistJson);
    }
}
