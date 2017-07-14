package org.wikidata.wdtk.rdf;

/*
 * #%L
 * Wikidata Toolkit RDF
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.implementation.DataObjectFactoryImpl;
import org.wikidata.wdtk.datamodel.interfaces.DataObjectFactory;
import org.wikidata.wdtk.datamodel.interfaces.DatatypeIdValue;
import org.wikidata.wdtk.datamodel.interfaces.DocumentDataFilter;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocument;
import org.wikidata.wdtk.datamodel.interfaces.MonolingualTextValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Reference;
import org.wikidata.wdtk.datamodel.interfaces.SnakGroup;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;
import org.wikidata.wdtk.datamodel.interfaces.StatementRank;
import org.wikidata.wdtk.wikibaseapi.ApiConnection;
import org.wikidata.wdtk.wikibaseapi.WikibaseDataFetcher;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;

public class PropertyRegisterTest {

	PropertyRegister propertyRegister;

	final String siteIri = "http://www.example.org/entities/";

	final TestObjectFactory objectFactory = new TestObjectFactory();
	final DataObjectFactory dataObjectFactory = new DataObjectFactoryImpl();

	@Before
	public void setUp() throws MediaWikiApiErrorException {
		Map<String, EntityDocument> mockResult = new HashMap<String, EntityDocument>();
		List<StatementGroup> mockStatementGroups = new ArrayList<StatementGroup>();

		PropertyIdValue pid434 = dataObjectFactory.getPropertyIdValue("P434",
				this.siteIri);
		PropertyIdValue pid23 = dataObjectFactory.getPropertyIdValue("P23",
				this.siteIri);
		PropertyIdValue pid1921 = dataObjectFactory.getPropertyIdValue("P1921",
				this.siteIri);

		Statement p23Statement = dataObjectFactory.getStatement(
				dataObjectFactory.getClaim(pid434, dataObjectFactory
						.getValueSnak(pid23, dataObjectFactory.getItemIdValue(
								"Q42", this.siteIri)), Collections
						.<SnakGroup> emptyList()), Collections
						.<Reference> emptyList(), StatementRank.NORMAL, "000");

		Statement p1921Statement = dataObjectFactory
				.getStatement(
						dataObjectFactory.getClaim(
								pid434,
								dataObjectFactory
										.getValueSnak(
												pid1921,
												dataObjectFactory
														.getStringValue("http://musicbrainz.org/$1/artist")),
								Collections.<SnakGroup> emptyList()),
						Collections.<Reference> emptyList(),
						StatementRank.NORMAL, "000");

		mockStatementGroups.add(dataObjectFactory.getStatementGroup(Collections
				.singletonList(p23Statement)));
		mockStatementGroups.add(dataObjectFactory.getStatementGroup(Collections
				.singletonList(p1921Statement)));

		mockResult.put("P434",
				dataObjectFactory.getPropertyDocument(pid434, Collections
						.<MonolingualTextValue> emptyList(), Collections
						.<MonolingualTextValue> emptyList(), Collections
						.<MonolingualTextValue> emptyList(),
						mockStatementGroups, dataObjectFactory
								.getDatatypeIdValue(DatatypeIdValue.DT_STRING),
						0));
		mockResult.put("P23", dataObjectFactory.getPropertyDocument(pid23,
				Collections.<MonolingualTextValue> emptyList(),
				Collections.<MonolingualTextValue> emptyList(),
				Collections.<MonolingualTextValue> emptyList(),
				Collections.<StatementGroup> emptyList(),
				dataObjectFactory.getDatatypeIdValue(DatatypeIdValue.DT_ITEM),
				0));

		this.propertyRegister = new PropertyRegister("P1921",
				new ApiConnection("http://localhost/"), this.siteIri);

		WikibaseDataFetcher dataFetcher = Mockito
				.mock(WikibaseDataFetcher.class);

		List<String> propertyIds = new ArrayList<String>();
		propertyIds.add("P434");
		for (int i = 1; i < 50; i++) {
			propertyIds.add("P" + i);
		}
		Mockito.when(dataFetcher.getEntityDocuments(propertyIds)).thenReturn(
				mockResult);
		Mockito.when(dataFetcher.getFilter()).thenReturn(
				new DocumentDataFilter());
		this.propertyRegister.dataFetcher = dataFetcher;
	}

	@Test
	public void testGetWikidataPropertyRegister() {
		assertEquals("P1921", this.propertyRegister.uriPatternPropertyId);
	}

	@Test
	public void testFetchPropertyUriPattern() {
		PropertyIdValue pid = this.dataObjectFactory.getPropertyIdValue("P434",
				this.siteIri);
		assertEquals("http://musicbrainz.org/$1/artist",
				this.propertyRegister.getPropertyUriPattern(pid));
		// Check twice to test that the cached retrieval works too
		assertEquals("http://musicbrainz.org/$1/artist",
				this.propertyRegister.getPropertyUriPattern(pid));
		assertEquals(50,
				this.propertyRegister.smallestUnfetchedPropertyIdNumber);
		assertTrue(this.propertyRegister.datatypes.keySet().contains("P434"));
	}

	@Test
	public void testGetPropertyType() {
		assertEquals(DatatypeIdValue.DT_STRING,
				this.propertyRegister.getPropertyType(dataObjectFactory
						.getPropertyIdValue("P434", this.siteIri)));
		// Check twice to test that the cached retrieval works too
		assertEquals(DatatypeIdValue.DT_STRING,
				this.propertyRegister.getPropertyType(dataObjectFactory
						.getPropertyIdValue("P434", this.siteIri)));
		assertEquals(50,
				this.propertyRegister.smallestUnfetchedPropertyIdNumber);
		assertTrue(this.propertyRegister.datatypes.keySet().contains("P434"));
	}

	@Test
	public void testGetMissingPropertyType() {
		assertNull(this.propertyRegister.getPropertyType(dataObjectFactory
				.getPropertyIdValue("P10", this.siteIri)));
		// Check twice to test fast failing on retry
		assertNull(this.propertyRegister.getPropertyType(dataObjectFactory
				.getPropertyIdValue("P10", this.siteIri)));
	}

	@Test
	public void testSetPropertyTypeFromEntityIdValue() {
		assertEquals(this.propertyRegister.setPropertyTypeFromEntityIdValue(
				this.dataObjectFactory
						.getPropertyIdValue("P1001", this.siteIri),
				this.dataObjectFactory.getItemIdValue("Q20", this.siteIri)),
				DatatypeIdValue.DT_ITEM);
	}

	@Test
	public void testSetPropertyTypeFromStringValue() {
		assertEquals(this.propertyRegister.setPropertyTypeFromStringValue(
				dataObjectFactory.getPropertyIdValue("P434", this.siteIri),
				dataObjectFactory
						.getStringValue("http://musicbrainz.org/$1/artist")),
				"http://www.wikidata.org/ontology#propertyTypeString");
	}

	@Test
	public void testSetMissingPropertyTypeFromStringValue() {
		assertEquals(this.propertyRegister.setPropertyTypeFromStringValue(
				dataObjectFactory.getPropertyIdValue("P10", this.siteIri),
				dataObjectFactory
						.getStringValue("http://musicbrainz.org/$1/artist")),
				"http://www.wikidata.org/ontology#propertyTypeString");
	}

	@Test
	public void testWikidataPropertyRegister() {
		PropertyRegister pr = PropertyRegister.getWikidataPropertyRegister();
		assertEquals(Datamodel.SITE_WIKIDATA, pr.getUriPrefix());
		assertEquals("P1921", pr.uriPatternPropertyId);
	}

}
