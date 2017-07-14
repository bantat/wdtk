package org.wikidata.wdtk.examples;


import org.apache.commons.io.IOUtils;
import org.apache.commons.csv.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wikidata.wdtk.datamodel.interfaces.*;
import org.wikidata.wdtk.wikibaseapi.WikibaseDataFetcher;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;
import org.wikidata.wdtk.wikibaseapi.ApiConnection;


import java.io.*;
import java.util.*;
import java.util.regex.Pattern;


/**
 * Created by torebanta on 6/26/17.
 */
public class WikiArtistMatcher {

    static ApiConnection wbapi = ApiConnection.getWikidataApiConnection();
    static WikibaseDataFetcher wbdf = WikibaseDataFetcher.getWikidataDataFetcher();

    public ArrayList<JSONObject> parseCSVtoJson(String filePath) throws IOException {
        try (Reader in = new FileReader(filePath);) {
            Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader(
                    "artist_id+1:13", "is_lift", "is_enabled", "total_views", "month_views", "week_views", "day_views",
                    "twitter_id", "facebook_id", "name", "total_videos").parse(in);

            System.out.println("Records built");
            Boolean header = true;
            ArrayList<JSONObject> artists = new ArrayList<>();


            for (CSVRecord record : records) {
                if (header) header = false;

                else {
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
                    String name = record.get("name");
                    String twitter = record.get("twitter_id");
                    String facebook = record.get("facebook_id");

                    JSONObject artist = new JSONObject();
                    artist.put("name", name);
                    artist.put("vevo", vevoId);
                    artist.put("twitter", twitter);
                    artist.put("facebook", facebook);

                    artists.add(artist);
                }
            }
            return artists;
        }
    }

    public ArrayList<String> getWikiIdsFromArtistKeyword(String searchKeyword, String language) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("action", "wbsearchentities");
        params.put("type", "item");
        params.put("language", language);
        params.put("format", "json");
        params.put("search", searchKeyword);

        ArrayList<String> wikiIds = new ArrayList();

        InputStream response = wbapi.sendRequest("GET", params);
        StringWriter writer = new StringWriter();
        IOUtils.copy(response, writer);     // "UTF-8" encoding arg?
        JSONObject jsonWrapper = new JSONObject(writer.toString());
        JSONArray results = jsonWrapper.getJSONArray("search");

        for (int i = 0; i < results.length(); i++) {
            JSONObject jsonResult = results.getJSONObject(i);
            wikiIds.add(jsonResult.get("id").toString());
        }

        return wikiIds;
    }

    public ArrayList<String> getMoreWikiIdsFromArtistKeyword(String searchKeyword) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("action", "query");
        params.put("format", "json");
        params.put("list", "search");
        params.put("srsearch", searchKeyword);

        ArrayList<String> wikiIds = new ArrayList();

        InputStream response = wbapi.sendRequest("GET", params);
        StringWriter writer = new StringWriter();
        IOUtils.copy(response, writer);     // "UTF-8" encoding arg?
        JSONObject jsonWrapper = new JSONObject(writer.toString());
        JSONObject queryWrapper = jsonWrapper.getJSONObject("query");
        JSONArray results = queryWrapper.getJSONArray("search");

        for (int i = 0; i < results.length(); i++) {
            JSONObject jsonResult = results.getJSONObject(i);
            wikiIds.add(jsonResult.get("title").toString());
        }

        return wikiIds;
    }

    public String findWikiIdFromList(ArrayList<String> wikiIds, JSONObject artistJson) {
        String artistWikiId = "";
        Map<String, EntityDocument> entities = Collections.emptyMap();

        if (wikiIds.size() > 0) {
            try {
                entities = wbdf.getEntityDocuments(wikiIds);
            } catch (MediaWikiApiErrorException e) {
                return artistWikiId;
            }
        }

        Map<String, Integer> map = new HashMap<>();

        for (EntityDocument entity : entities.values()) {
            if (entity instanceof ItemDocument) {
                ItemDocument itemDocument = ((ItemDocument) entity);
                Integer matchCount = propertyMatchCountFromDocument(itemDocument, artistJson);
                if (matchCount > 0) map.put(itemDocument.getItemId().getId(), matchCount);
            }
        }

        Map.Entry<String, Integer> maxEntry = null;

        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0)
            {
                maxEntry = entry;
            }
        }
        if (maxEntry != null) artistWikiId = maxEntry.getKey();

        return artistWikiId;
    }

    public Integer propertyMatchCountFromDocument(ItemDocument artistDocument, JSONObject artistJson) {
        int matchCount = 0;
        for (StatementGroup sg : artistDocument.getStatementGroups()) {
            // "P31" is "instance of" on Wikidata
            if ("P31".equals(sg.getProperty().getId())) {
                for (Statement s : sg.getStatements()) {
                    if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                        Value v = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                        if (v instanceof ItemIdValue) {
                            if ("Q215380".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a band");
                                matchCount++;
                            } else if ("Q5741069".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a rock band");
                                matchCount++;
                            } else if ("Q19184926".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a duo");
                                matchCount++;
                            } else if ("Q641066".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a girl group");
                                matchCount++;
                            } else if ("Q2088357".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a musical ensemble");
                                matchCount++;
                            } else if ("Q216337".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a boy band");
                                matchCount++;
                            } else if ("Q7623897".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is all female band");
                                matchCount++;
                            }
                        }
                    }
                }
            } else if ("P106".equals(sg.getProperty().getId())) {       // Occupation
                for (Statement s : sg.getStatements()) {
                    if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                        Value v = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                        if (v instanceof ItemIdValue) {
                            if ("Q639669".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a musician");
                                matchCount++;
                            } else if ("Q177220".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a singer");
                                matchCount++;
                            } else if ("Q2252262".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a rapper");
                                matchCount++;
                            } else if ("Q130857".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a dj");
                                matchCount++;
                            } else if ("Q183945".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a record producer");
                                matchCount++;
                            } else if ("Q488205".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a singer-songwriter");
                                matchCount++;
                            } else if ("Q753110".equals(((ItemIdValue) v).getId())) {
                                System.out.println("artist is a songwriter");
                                matchCount++;
                            }
                        }
                    }
                }
            } else if ("P2013".equals(sg.getProperty().getId())) {      // Facebook Profile ID
                for (Statement s : sg.getStatements()) {
                    if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                        Value v = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                        if (v instanceof StringValue) {
                            if (((StringValue) v).getString().equals(artistJson.get("facebook"))) {
                                System.out.println("artist fb matched");
                                matchCount++;
                            }
                        }
                    }
                }
            } else if ("P2002".equals(sg.getProperty().getId())) {      // Twitter Username
                for (Statement s : sg.getStatements()) {
                    if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                        Value v = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                        if (v instanceof StringValue) {
                            if (((StringValue) v).getString().equals(artistJson.get("twitter"))) {
                                System.out.println("artist twitter matched");
                                matchCount++;
                            }
                        }
                    }
                }
            }
        }
        return matchCount;
    }

    public String findWikiIdFromArtistJson(JSONObject artistJson) throws IOException {
        ArrayList<String> ids = getWikiIdsFromArtistKeyword(artistJson.get("name").toString(), "en");
        System.out.println(ids);
        String wikiId = findWikiIdFromList(ids, artistJson);
        System.out.println(wikiId);
        if (ids.size() == 0 || wikiId.equals("")) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
            String searchKeyword = artistJson.get("vevo").toString();
            if (artistJson.get("vevo").toString().contains("-1")) {
                searchKeyword = searchKeyword.replace("-1", "");
                searchKeyword = searchKeyword.replace('-', ' ');
            } else searchKeyword = searchKeyword.replace('-', ' ');
            ids = getWikiIdsFromArtistKeyword(searchKeyword, "en");
            System.out.println(ids);
            wikiId = findWikiIdFromList(ids, artistJson);
            System.out.println(wikiId);
            if (ids.size() == 0 || wikiId.equals("")) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
                ids = getWikiIdsFromArtistKeyword(artistJson.get("name").toString(), "es");
                System.out.println(ids);
                wikiId = findWikiIdFromList(ids, artistJson);
                System.out.println(wikiId);
                if (ids.size() == 0 || wikiId.equals("")) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        e.printStackTrace();
                    }
                    ids = getWikiIdsFromArtistKeyword(searchKeyword, "es");
                    System.out.println(ids);
                    wikiId = findWikiIdFromList(ids, artistJson);
                    System.out.println(wikiId);
                }
            }
        }
        if (wikiId.equals("")) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
            ids = getMoreWikiIdsFromArtistKeyword(artistJson.get("name").toString());
            System.out.println(ids);
            wikiId = findWikiIdFromList(ids, artistJson);
            System.out.println(wikiId);
            if (ids.size() == 0 || wikiId.equals("")) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
                String searchKeyword = artistJson.get("vevo").toString();
                if (artistJson.get("vevo").toString().contains("-1")) {
                    searchKeyword = searchKeyword.replace("-1", "");
                    searchKeyword = searchKeyword.replace('-', ' ');
                } else searchKeyword = searchKeyword.replace('-', ' ');
                ids = getMoreWikiIdsFromArtistKeyword(searchKeyword);
                System.out.println(ids);
                wikiId = findWikiIdFromList(ids, artistJson);
            }
        }
        return wikiId;
    }

    public static void main(String[] args) throws MediaWikiApiErrorException, IOException {
        ExampleHelpers.configureLogging();

        WikiArtistMatcher wiki = new WikiArtistMatcher();

        wiki.wbdf.getFilter().setLanguageFilter(Collections.singleton("en"));
        wiki.wbdf.getFilter().setSiteLinkFilter(Collections.singleton("enwiki"));

        EntityDocument entityP31 = wiki.wbdf.getEntityDocument("P31");      // Instance of
        EntityDocument entityP106 = wiki.wbdf.getEntityDocument("P106");    // Occupation
        EntityDocument entityP2013 = wiki.wbdf.getEntityDocument("P2013");  // FB ID
        EntityDocument entityP2002 = wiki.wbdf.getEntityDocument("P2002");  // Twitter



        Set<PropertyIdValue> properties = new HashSet<>();

        properties.add(((PropertyDocument) entityP31).getPropertyId());
        properties.add(((PropertyDocument) entityP106).getPropertyId());
        properties.add(((PropertyDocument) entityP2013).getPropertyId());
        properties.add(((PropertyDocument) entityP2002).getPropertyId());

        wiki.wbdf.getFilter().setPropertyFilter(properties);


//        JSONObject artistJson = new JSONObject("{\"twitter\": \"thalia\",\"facebook\": \"Thalia\",\"wiki\": \"\",\"name\": \"ThalÃ\u00ADa\",\"vevo\": \"thalia-1\"}");
//        System.out.println(wiki.findWikiIdFromArtistJson(artistJson));

//        ArrayList<JSONObject> artistsJson = wiki.parseCSVtoJson(wiki.getClass().getClassLoader()
//                .getResource("artists-simplified.csv").getPath());
//        System.out.println("records: " + artistsJson.size());
//        int matches = 0;
//        int total = 0;
//        PrintWriter writer = null;
//        try {
//            writer = new PrintWriter("results.json", "UTF-8");
//            writer.println("[");
//        } catch (IOException e) {
//            System.out.println("Could not write result file");
//            System.exit(1);
//        }
//        for (JSONObject artistJson : artistsJson) {
//            try {
//                System.out.println(artistJson);
//                String wikiId = wiki.findWikiIdFromArtistJson(artistJson);
//                artistJson.put("wiki", wikiId);
//                total++;
//                try {
//                    Thread.sleep(150);
//                    if (total % 10000 == 0) {
//                        System.out.println("Sleeping 5 minutes");
//                        Thread.sleep(5 * 60000);
//                    } else if (total % 1000 == 0) {
//                        System.out.println("Sleeping 2 minutes");
//                        Thread.sleep(2 * 60000);
//                    } else if (total % 500 == 0) {
//                        System.out.println("Sleeping 1 minute");
//                        Thread.sleep(60000);
//                    } else if (total % 100 == 0) {
//                        System.out.println("Sleeping 30 seconds");
//                        Thread.sleep(30000);
//                    }
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    e.printStackTrace();
//                }
//                if (artistJson.get("wiki") != "") {
//                    matches++;
//                    System.out.println(matches + " out of " + total + " matched");
//                } else {
//                    System.out.println(artistJson.get("name") + ", " + artistJson.get("vevo") + " did not match");
//                    System.out.println(matches + " out of " + total + " matched");
//                }
//                writer.println(artistJson.toString() + ",");
//            } catch (Exception e) {
//                System.out.println("***** CAUGHT EXCEPTION *****");
//                e.printStackTrace();
//                total++;
//            }
//        }
//        writer.println("{}]");
//        writer.close();
//        System.out.println(matches + " matches of " + artistsJson.size());
//
//        JSONArray final_json = new JSONArray(artistsJson);
//        System.out.println(final_json);
    }
}
