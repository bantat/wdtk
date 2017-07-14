package org.wikidata.wdtk.examples;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
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
import java.util.*;

/**
 * Created by torebanta on 7/5/17.
 */
public class WikiArtistParser {

    static ApiConnection wbapi = new ApiConnection("https://en.wikipedia.org/w/api.php");
    static WikibaseDataFetcher wbdf = WikibaseDataFetcher.getWikidataDataFetcher();
    static Invocable invocable;
    static ScriptEngine engine;

    public JSONObject getWikiPropertiesFromDocument(ItemDocument artistDocument, JSONObject artistJson) {
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

    public ArrayList<String> getCategoriesFromTitle(String wikiTitle) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("action", "query");
        params.put("format", "json");
        params.put("prop", "categories");
        params.put("clshow", "!hidden");
        params.put("cllimit", "25");
        params.put("titles", wikiTitle);

        InputStream response = this.wbapi.sendRequest("GET", params);
        StringWriter writer = new StringWriter();
        IOUtils.copy(response, writer);     // "UTF-8" encoding arg?
        JSONObject jsonWrapper = new JSONObject(writer.toString());
        JSONObject results = jsonWrapper.getJSONObject("query").getJSONObject("pages");
        JSONObject item = results.getJSONObject(results.keys().next().toString());
        JSONArray categoriesJson = item.getJSONArray("categories");

        ArrayList<String> categories = new ArrayList<>();

        for (int i = 0; i < categoriesJson.length(); i++) {
            JSONObject jsonResult = categoriesJson.getJSONObject(i);
            categories.add(jsonResult.get("title").toString());
        }

        return categories;
    }

    public String getInfoboxFromTitle(String wikiTitle) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("action", "query");
        params.put("format", "json");
        params.put("prop", "revisions");
        params.put("rvprop", "content");
        params.put("rvsection", "0");
        params.put("titles", wikiTitle);

        InputStream response = this.wbapi.sendRequest("GET", params);
        StringWriter writer = new StringWriter();
        IOUtils.copy(response, writer);     // "UTF-8" encoding arg?
        JSONObject jsonWrapper = new JSONObject(writer.toString());
        JSONObject results = jsonWrapper.getJSONObject("query").getJSONObject("pages");
        JSONObject item = results.getJSONObject(results.keys().next().toString());
        JSONArray revisionsJson = item.getJSONArray("revisions");
        JSONObject revision = revisionsJson.getJSONObject(0);
        String text = revision.getString("*");
        return text.replace("\n", "");
    }

    public JSONObject getInfoboxJsonFromWikitext(String wikitext) {
        Object result;
        JSONObject json = new JSONObject();

        try {
            result = invocable.invokeMethod(engine.getBindings(ScriptContext.ENGINE_SCOPE).get("wtf_wikipedia"), "parse", wikitext);
            ScriptObjectMirror mirror = (ScriptObjectMirror) result;

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

    public JSONObject buildArtistWikiInfoJson(JSONObject infoboxJson, ArrayList<String> categories) {

        ArrayList<String> associated_acts = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> genres = new ArrayList<>();
        ArrayList<String> module = new ArrayList<>();

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

    public static void main(String[] args) throws MediaWikiApiErrorException, IOException, ScriptException, InterruptedException {

        /* SETUP */

        WikiArtistParser wiki = new WikiArtistParser();

        wbdf.getFilter().setSiteLinkFilter(Collections.singleton("enwiki"));

        Set<PropertyIdValue> properties = new HashSet<>();
        wbdf.getFilter().setPropertyFilter(properties);

        FileReader reader = new FileReader(wiki.getClass().getClassLoader().getResource("wtf_wikipedia-master").getPath() + "/builds/wtf_wikipedia.js");
        engine = new ScriptEngineManager().getEngineByName("nashorn");
        ScriptContext scriptCtxt = engine.getContext();
        engine.eval(reader, scriptCtxt);
        invocable = (Invocable) engine;

        /* SETUP COMPLETE */

        /* ************** Filter Categories ************** */

        File file = new File(wiki.getClass().getClassLoader().getResource("wiki-data-module-fixed.json").getPath());

        JSONObject wikiJson = new JSONObject(new Scanner(file).useDelimiter("\\Z").next());

        ArrayList<String> categories = new ArrayList<>();
        ArrayList<String> sharedCategories = new ArrayList<>();
        Map<String, Integer> map = new HashMap<>();

        for (String key : wikiJson.keySet()) {
            JSONObject artist = wikiJson.getJSONObject(key);
            if (artist.has("categories")) {
                JSONArray categoriesArray = artist.getJSONArray("categories");
                for (int i = 0; i < categoriesArray.length(); i++) {
                    String item = categoriesArray.getString(i);
                    if (!categories.contains(item)) {
                        categories.add(item);
                    } else if (!sharedCategories.contains(item)) {
                        sharedCategories.add(item);
                        map.put(item, 1);
                    } else {
                        map.put(item, map.get(item) + 1);
                    }
                }
            }
        }

//        Set<Map.Entry<String, Integer>> set = map.entrySet();
//        List<Map.Entry<String, Integer>> list = new ArrayList<>(set);
//        Collections.sort( list, new Comparator<Map.Entry<String, Integer>>()
//        {
//            public int compare( Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2 )
//            {
//                return (o2.getValue()).compareTo( o1.getValue() );
//            }
//        });
//
//        System.out.println(list);

//        System.out.println(categories.size() + " unique categories");
//        System.out.println(sharedCategories.size() + " shared categories");

        ArrayList<String> toFilter = new ArrayList<>();
        toFilter.add("Category:Living people");
        toFilter.add("Category:Commandeurs of the Ordre des Arts et des Lettres");
        toFilter.add("Officers Crosses of the Order of Merit of the Federal Republic of Germany");

        for (String category : categories) {
            String simplified = category.toLowerCase();
            if (simplified.contains("births")) toFilter.add(category);
            if (simplified.contains("deaths")) toFilter.add(category);
            if (simplified.contains("alumni")) toFilter.add(category);
            if (simplified.contains("winner")) toFilter.add(category);
            if (simplified.contains("actor")) toFilter.add(category);
            if (simplified.contains("actress")) toFilter.add(category);
//            if (simplified.contains("christian")) toFilter.add(category);
//            if (simplified.contains("jew")) toFilter.add(category);
            if (simplified.contains("business")) toFilter.add(category);
            if (simplified.contains("model")) toFilter.add(category);
            if (simplified.contains("designer")) toFilter.add(category);
        }

//        ArrayList<String> filtered = new ArrayList<>();
//
//        for (String category : categories) {
//            if (!toFilter.contains(category)) {
//                filtered.add(category);
//                //if (map.containsKey(category)) System.out.println(category + ": " + map.get(category));
//            }
//        }

        //System.out.println(toFilter);
        //System.out.println(toFilter.size() + " categories to be fitered out of " + categories.size());
        //System.out.println(filtered);

        for (String key : wikiJson.keySet()) {
            JSONObject artist = wikiJson.getJSONObject(key);
            if (artist.has("categories")) {
                JSONArray categoriesArray = artist.getJSONArray("categories");
                for (int i = 0; i < categoriesArray.length(); i++) {
                    String item = categoriesArray.getString(i);
                    if (toFilter.contains(item)) categoriesArray.remove(i);
                }
            }
        }

        PrintWriter writer = null;

        try {
            writer = new PrintWriter("wiki-data-categories-filtered.json", "UTF-8");
        } catch (IOException e) {
            System.out.println("Could not write result file");
            System.exit(1);
        }

        writer.print(wikiJson);
        writer.close();

        /* ************** Parse module entities ************** */

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


        /* ************** Add Wiki title/label to Json ************** */
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
