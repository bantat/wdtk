package org.wikidata.wdtk.wikibaseapi;

/*
 * #%L
 * Wikidata Toolkit Wikibase API
 * %%
 * Copyright (C) 2014 - 2015 Wikidata Toolkit Developers
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.helpers.ItemDocumentBuilder;
import org.wikidata.wdtk.datamodel.helpers.PropertyDocumentBuilder;
import org.wikidata.wdtk.datamodel.interfaces.DatatypeIdValue;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.ItemIdValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.json.jackson.JsonSerializer;
import org.wikidata.wdtk.util.CompressionType;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;
import org.wikidata.wdtk.wikibaseapi.apierrors.TokenErrorException;

public class WikibaseDataEditorTest {

	MockApiConnection con;

	@Before
	public void setUp() throws IOException {
		this.con = new MockApiConnection();
		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "query");
		params.put("meta", "tokens");
		params.put("format", "json");
		this.con.setWebResourceFromPath(params, this.getClass(),
				"/query-csrf-token-loggedin-response.json",
				CompressionType.NONE);
	}

	@Test
	public void testSetMaxLag() throws IOException, MediaWikiApiErrorException {
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);
		wde.setMaxLag(3);
		assertEquals(3, wde.getMaxLag());
	}

	@Test
	public void testSetAverageTimePerEdit() throws IOException,
			MediaWikiApiErrorException {
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);
		wde.setAverageTimePerEdit(5000);
		assertEquals(5000, wde.getAverageTimePerEdit());
	}

	@Test
	public void testSetRemainingEdits() throws IOException,
			MediaWikiApiErrorException {
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);
		wde.setRemainingEdits(1);

		ItemDocument itemDocument = ItemDocumentBuilder.forItemId(
				ItemIdValue.NULL).build();
		ItemDocument expectedResultDocument = ItemDocumentBuilder
				.forItemId(Datamodel.makeWikidataItemIdValue("Q1234"))
				.withRevisionId(1234).build();
		String resultData = JsonSerializer
				.getJsonString(expectedResultDocument);
		String expectedResult = "{\"entity\":" + resultData + ",\"success\":1}";

		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "wbeditentity");
		params.put("summary", "My summary");
		params.put("new", "item");
		params.put("token", "42307b93c79b0cb558d2dfb4c3c92e0955e06041+\\");
		params.put("format", "json");
		params.put("maxlag", "5");
		String data = JsonSerializer.getJsonString(itemDocument);
		params.put("data", data);
		con.setWebResource(params, expectedResult);

		assertEquals(1, wde.getRemainingEdits());
		ItemDocument result = wde
				.createItemDocument(itemDocument, "My summary");
		assertEquals(expectedResultDocument, result);
		assertEquals(0, wde.getRemainingEdits());
		result = wde.createItemDocument(itemDocument, "My summary");
		assertEquals(null, result);
		assertEquals(0, wde.getRemainingEdits());
	}

	@Test
	public void testDisableEditing() throws IOException,
			MediaWikiApiErrorException {
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);
		wde.disableEditing();

		assertEquals(0, wde.getRemainingEdits());

		ItemDocument itemDocument = ItemDocumentBuilder.forItemId(
				ItemIdValue.NULL).build();
		ItemDocument result = wde
				.createItemDocument(itemDocument, "My summary");

		assertEquals(null, result);
		assertEquals(0, wde.getRemainingEdits());
	}

	@Test
	public void testCreateItem() throws IOException, MediaWikiApiErrorException {
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);

		ItemDocument itemDocument = ItemDocumentBuilder.forItemId(
				ItemIdValue.NULL).build();
		ItemDocument expectedResultDocument = ItemDocumentBuilder
				.forItemId(Datamodel.makeWikidataItemIdValue("Q1234"))
				.withRevisionId(1234).build();
		String resultData = JsonSerializer
				.getJsonString(expectedResultDocument);
		String expectedResult = "{\"entity\":" + resultData + ",\"success\":1}";

		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "wbeditentity");
		params.put("summary", "My summary");
		params.put("new", "item");
		params.put("token", "42307b93c79b0cb558d2dfb4c3c92e0955e06041+\\");
		params.put("format", "json");
		params.put("maxlag", "5");
		String data = JsonSerializer.getJsonString(itemDocument);
		params.put("data", data);
		con.setWebResource(params, expectedResult);

		ItemDocument result = wde
				.createItemDocument(itemDocument, "My summary");

		assertEquals(expectedResultDocument, result);
		assertEquals(-1, wde.getRemainingEdits());
	}

	@Test(expected = TokenErrorException.class)
	public void testCreateItemBadToken() throws IOException,
			MediaWikiApiErrorException {
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);

		ItemDocument itemDocument = ItemDocumentBuilder.forItemId(
				ItemIdValue.NULL).build();

		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "wbeditentity");
		params.put("summary", "My summary");
		params.put("new", "item");
		params.put("token", "42307b93c79b0cb558d2dfb4c3c92e0955e06041+\\");
		params.put("format", "json");
		params.put("maxlag", "5");
		String data = JsonSerializer.getJsonString(itemDocument);
		params.put("data", data);
		this.con.setWebResourceFromPath(params, this.getClass(),
				"/error-badtoken.json", CompressionType.NONE);

		wde.createItemDocument(itemDocument, "My summary");
	}

	@Test
	public void testCreateItemCachedToken() throws IOException,
			MediaWikiApiErrorException {
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);

		ItemDocument itemDocument = ItemDocumentBuilder.forItemId(
				ItemIdValue.NULL).build();
		ItemDocument expectedResultDocument = ItemDocumentBuilder
				.forItemId(Datamodel.makeWikidataItemIdValue("Q1234"))
				.withRevisionId(1234).build();
		String resultData = JsonSerializer
				.getJsonString(expectedResultDocument);
		String expectedResult = "{\"entity\":" + resultData + ",\"success\":1}";

		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "wbeditentity");
		params.put("summary", "My summary");
		params.put("new", "item");
		params.put("token", "42307b93c79b0cb558d2dfb4c3c92e0955e06041+\\");
		params.put("format", "json");
		params.put("maxlag", "5");
		String data = JsonSerializer.getJsonString(itemDocument);
		params.put("data", data);
		con.setWebResource(params, expectedResult);

		// Create item twice
		wde.createItemDocument(itemDocument, "My summary");
		ItemDocument result = wde
				.createItemDocument(itemDocument, "My summary");

		assertEquals(expectedResultDocument, result);
	}

	@Test
	public void testCreateItemWikibaseJsonBug() throws IOException,
			MediaWikiApiErrorException {
		// Test what happens if the API returns JSON without an actual entity
		// document and without any respective key
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);

		ItemDocument itemDocument = ItemDocumentBuilder.forItemId(
				ItemIdValue.NULL).build();
		ItemDocument expectedResultDocument = ItemDocumentBuilder
				.forItemId(Datamodel.makeWikidataItemIdValue("Q1234"))
				.withRevisionId(1234).build();
		String expectedResult = "{\"entity\":"
				+ "{\"type\":\"item\",\"aliases\":[],\"labels\":[],\"descriptions\":[],\"lastrevid\":1234,\"sitelinks\":[],\"id\":\"Q1234\",\"claims\":[]}"
				+ ",\"success\":1}";

		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "wbeditentity");
		params.put("summary", "My summary");
		params.put("new", "item");
		params.put("token", "42307b93c79b0cb558d2dfb4c3c92e0955e06041+\\");
		params.put("format", "json");
		params.put("maxlag", "5");
		String data = JsonSerializer.getJsonString(itemDocument);
		params.put("data", data);
		con.setWebResource(params, expectedResult);

		ItemDocument result = wde
				.createItemDocument(itemDocument, "My summary");

		assertEquals(expectedResultDocument, result);
	}

	@Test(expected = IOException.class)
	public void testCreateItemBadEntityDocumentJson() throws IOException,
			MediaWikiApiErrorException {
		// Test what happens if the API returns JSON without an actual entity
		// document, but with a respective key pointing to an empty object
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);

		ItemDocument itemDocument = ItemDocumentBuilder.forItemId(
				ItemIdValue.NULL).build();
		String expectedResult = "{\"entity\":" + "{}" + ",\"success\":1}";

		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "wbeditentity");
		params.put("summary", "My summary");
		params.put("new", "item");
		params.put("token", "42307b93c79b0cb558d2dfb4c3c92e0955e06041+\\");
		params.put("format", "json");
		params.put("maxlag", "5");
		String data = JsonSerializer.getJsonString(itemDocument);
		params.put("data", data);
		con.setWebResource(params, expectedResult);

		wde.createItemDocument(itemDocument, "My summary");
	}

	@Test(expected = IOException.class)
	public void testCreateItemMissingEntityDocumentJson() throws IOException,
			MediaWikiApiErrorException {
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);

		ItemDocument itemDocument = ItemDocumentBuilder.forItemId(
				ItemIdValue.NULL).build();
		String expectedResult = "{\"success\":1}";

		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "wbeditentity");
		params.put("summary", "My summary");
		params.put("new", "item");
		params.put("token", "42307b93c79b0cb558d2dfb4c3c92e0955e06041+\\");
		params.put("format", "json");
		params.put("maxlag", "5");
		String data = JsonSerializer.getJsonString(itemDocument);
		params.put("data", data);
		con.setWebResource(params, expectedResult);

		wde.createItemDocument(itemDocument, "My summary");
	}

	@Test
	public void testCreatePropertyBot() throws IOException,
			MediaWikiApiErrorException {
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);
		wde.setEditAsBot(true);

		PropertyDocument propertyDocument = PropertyDocumentBuilder
				.forPropertyIdAndDatatype(PropertyIdValue.NULL,
						DatatypeIdValue.DT_ITEM).build();
		PropertyDocument expectedResultDocument = PropertyDocumentBuilder
				.forPropertyIdAndDatatype(
						Datamodel.makeWikidataPropertyIdValue("P1234"),
						DatatypeIdValue.DT_ITEM).withRevisionId(1234).build();
		String resultData = JsonSerializer
				.getJsonString(expectedResultDocument);
		String expectedResult = "{\"entity\":" + resultData + ",\"success\":1}";

		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "wbeditentity");
		params.put("new", "property");
		params.put("bot", "");
		params.put("token", "42307b93c79b0cb558d2dfb4c3c92e0955e06041+\\");
		params.put("format", "json");
		params.put("maxlag", "5");
		String data = JsonSerializer.getJsonString(propertyDocument);
		params.put("data", data);
		con.setWebResource(params, expectedResult);

		PropertyDocument result = wde.createPropertyDocument(propertyDocument,
				null);

		assertTrue(wde.editAsBot());
		assertEquals(expectedResultDocument, result);
	}

	@Test
	public void testEditItem() throws IOException, MediaWikiApiErrorException {
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);

		ItemIdValue id = Datamodel.makeWikidataItemIdValue("Q1234");

		ItemDocument itemDocument = ItemDocumentBuilder.forItemId(id)
				.withRevisionId(1234).build();
		ItemDocument expectedResultDocument = ItemDocumentBuilder.forItemId(id)
				.withRevisionId(1235).build();
		String resultData = JsonSerializer
				.getJsonString(expectedResultDocument);
		String expectedResult = "{\"entity\":" + resultData + ",\"success\":1}";

		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "wbeditentity");
		params.put("id", "Q1234");
		params.put("summary", "My summary");
		params.put("token", "42307b93c79b0cb558d2dfb4c3c92e0955e06041+\\");
		params.put("format", "json");
		params.put("baserevid", "1234");
		params.put("maxlag", "5");
		String data = JsonSerializer.getJsonString(itemDocument);
		params.put("data", data);
		con.setWebResource(params, expectedResult);

		ItemDocument result = wde.editItemDocument(itemDocument, false,
				"My summary");

		assertEquals(expectedResultDocument, result);
	}

	@Test
	public void testEditProperty() throws IOException,
			MediaWikiApiErrorException {
		WikibaseDataEditor wde = new WikibaseDataEditor(this.con,
				Datamodel.SITE_WIKIDATA);

		PropertyIdValue id = Datamodel.makeWikidataPropertyIdValue("P1234");

		PropertyDocument itemDocument = PropertyDocumentBuilder
				.forPropertyIdAndDatatype(id, DatatypeIdValue.DT_ITEM)
				.withRevisionId(1234).build();
		PropertyDocument expectedResultDocument = PropertyDocumentBuilder
				.forPropertyIdAndDatatype(id, DatatypeIdValue.DT_ITEM)
				.withRevisionId(1235).build();
		String resultData = JsonSerializer
				.getJsonString(expectedResultDocument);
		String expectedResult = "{\"entity\":" + resultData + ",\"success\":1}";

		Map<String, String> params = new HashMap<String, String>();
		params.put("action", "wbeditentity");
		params.put("id", "P1234");
		params.put("summary", "My summary");
		params.put("token", "42307b93c79b0cb558d2dfb4c3c92e0955e06041+\\");
		params.put("format", "json");
		params.put("clear", "");
		params.put("baserevid", "1234");
		params.put("maxlag", "5");
		String data = JsonSerializer.getJsonString(itemDocument);
		params.put("data", data);
		con.setWebResource(params, expectedResult);

		PropertyDocument result = wde.editPropertyDocument(itemDocument, true,
				"My summary");

		assertEquals(expectedResultDocument, result);
	}

}
