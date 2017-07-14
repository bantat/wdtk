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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.interfaces.DatatypeIdValue;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocument;
import org.wikidata.wdtk.datamodel.interfaces.EntityIdValue;
import org.wikidata.wdtk.datamodel.interfaces.GlobeCoordinatesValue;
import org.wikidata.wdtk.datamodel.interfaces.MonolingualTextValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.QuantityValue;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;
import org.wikidata.wdtk.datamodel.interfaces.StringValue;
import org.wikidata.wdtk.datamodel.interfaces.TimeValue;
import org.wikidata.wdtk.datamodel.interfaces.ValueSnak;
import org.wikidata.wdtk.wikibaseapi.ApiConnection;
import org.wikidata.wdtk.wikibaseapi.WikibaseDataFetcher;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;

/**
 * This class helps to manage information about Properties that has to obtained
 * by a webservice.
 *
 * @author Michael Guenther
 *
 */
public class PropertyRegister {

	static final Logger logger = LoggerFactory
			.getLogger(PropertyRegister.class);

	/**
	 * Object used to fetch data. Kept package private to allow being replaced
	 * by mock object in tests.
	 */
	WikibaseDataFetcher dataFetcher;

	/**
	 * Map that stores the datatype of properties. Properties are identified by
	 * their Pid; dataypes are identified by their datatype IRI.
	 */
	final protected Map<String, String> datatypes = new HashMap<String, String>();

	/**
	 * Map that stores the URI patterns of properties. Properties are identified
	 * by their Pid; patterns are given as strings using $1 as placeholder for
	 * the escaped value.
	 */
	final protected Map<String, String> uriPatterns = new HashMap<String, String>();

	/**
	 * Pid of the property used to store URI patterns, if used, or null if no
	 * such property should be considered.
	 */
	final String uriPatternPropertyId;

	/**
	 * URI prefix to be used on this site.
	 */
	final String siteUri;

	/**
	 * Maximum number of property documents that can be retrieved in one API
	 * call.
	 */
	final int API_MAX_ENTITY_DOCUMENT_NUMBER = 50;

	/**
	 * Smallest property number for which no information has been fetched from
	 * the Web yet in a systematic fashion. Whenever any property data is
	 * fetched, additional properties are also fetched and this number is
	 * incremented accordingly.
	 */
	int smallestUnfetchedPropertyIdNumber = 1;

	static final PropertyRegister WIKIDATA_PROPERTY_REGISTER = new PropertyRegister(
			"P1921", ApiConnection.getWikidataApiConnection(),
			Datamodel.SITE_WIKIDATA);

	/**
	 * Constructs a new property register.
	 *
	 * @param uriPatternPropertyId
	 *            property id used for a URI Pattern property, e.g., P1921 on
	 *            Wikidata; can be null if no such property should be used
	 * @param apiConnection
	 *            API connection object that defines how to connect to the
	 *            online API
	 * @param siteUri
	 *            the URI identifying the site that is accessed (usually the
	 *            prefix of entity URIs), e.g.,
	 *            "http://www.wikidata.org/entity/"
	 */
	public PropertyRegister(String uriPatternPropertyId,
			ApiConnection apiConnection, String siteUri) {
		this.uriPatternPropertyId = uriPatternPropertyId;
		this.siteUri = siteUri;
		dataFetcher = new WikibaseDataFetcher(apiConnection, siteUri);
	}

	/**
	 * Returns a singleton object that serves as a property register for
	 * Wikidata.
	 *
	 * @return property register for Wikidata
	 */
	public static PropertyRegister getWikidataPropertyRegister() {
		return WIKIDATA_PROPERTY_REGISTER;
	}

	/**
	 * Returns the URI prefix that is used on the site considered by this
	 * object. This string also identifies the site globally.
	 *
	 * @return The URI prefix, e.g., "http://www.wikidata.org/entity/"
	 */
	public String getUriPrefix() {
		return this.siteUri;
	}

	/**
	 * Returns the IRI of the primitive type of an {@link PropertyIdValue}.
	 *
	 * @param propertyIdValue
	 *            property whose datatype should be fetched
	 * @return URI of the datatype of this property, or null if the type could
	 *         not be determined
	 */
	public String getPropertyType(PropertyIdValue propertyIdValue) {
		if (!datatypes.containsKey(propertyIdValue.getId())) {
			fetchPropertyInformation(propertyIdValue);
		}
		return datatypes.get(propertyIdValue.getId());
	}

	/**
	 * Sets datatypeIri an IRI of the primitive type of an Property for
	 * {@link PropertyIdValue}.
	 *
	 * @param propertyIdValue
	 * @param datatypeIri
	 */
	public void setPropertyType(PropertyIdValue propertyIdValue,
			String datatypeIri) {
		datatypes.put(propertyIdValue.getId(), datatypeIri);

	}

	/**
	 * Returns the URI Pattern of a {@link PropertyIdValue} that should be used
	 * to create URIs of external resources from statement values for the
	 * property.
	 *
	 * @param propertyIdValue
	 *            property to fetch URI pattern for
	 * @return string pattern using "$1" as a placeholder, or null if no pattern
	 *         was found for the given property
	 */
	public String getPropertyUriPattern(PropertyIdValue propertyIdValue) {
		if (!this.datatypes.containsKey(propertyIdValue.getId())) {
			fetchPropertyInformation(propertyIdValue);
		}
		return this.uriPatterns.get(propertyIdValue.getId());

	}

	/**
	 * Returns the IRI of the primitive Type of an Property for
	 * {@link EntityIdValue} objects.
	 *
	 * @param propertyIdValue
	 * @param value
	 */
	public String setPropertyTypeFromEntityIdValue(
			PropertyIdValue propertyIdValue, EntityIdValue value) {
		switch (value.getId().charAt(0)) {
		case 'Q':
			return DatatypeIdValue.DT_ITEM;
		case 'P':
			return DatatypeIdValue.DT_PROPERTY;
		default:
			logger.warn("Could not determine Type of "
					+ propertyIdValue.getId()
					+ ". It is not a valid EntityDocument Id");
			return null;
		}
	}

	/**
	 * Returns the IRI of the primitive Type of an Property for
	 * {@link GlobeCoordinatesValue} objects.
	 *
	 * @param propertyIdValue
	 * @param value
	 */
	public String setPropertyTypeFromGlobeCoordinatesValue(
			PropertyIdValue propertyIdValue, GlobeCoordinatesValue value) {
		return DatatypeIdValue.DT_GLOBE_COORDINATES;
	}

	/**
	 * Returns the IRI of the primitive Type of an Property for
	 * {@link QuantityValue} objects.
	 *
	 * @param propertyIdValue
	 * @param value
	 */
	public String setPropertyTypeFromQuantityValue(
			PropertyIdValue propertyIdValue, QuantityValue value) {
		return DatatypeIdValue.DT_QUANTITY;
	}

	/**
	 * Returns the IRI of the primitive Type of an Property for
	 * {@link StringValue} objects.
	 *
	 * @param propertyIdValue
	 * @param value
	 */
	public String setPropertyTypeFromStringValue(
			PropertyIdValue propertyIdValue, StringValue value) {
		String datatype = getPropertyType(propertyIdValue);
		if (datatype == null) {
			logger.warn("Could not fetch datatype of "
					+ propertyIdValue.getIri() + ". Assuming type "
					+ DatatypeIdValue.DT_STRING);
			return DatatypeIdValue.DT_STRING; // default type for StringValue
		} else {
			return datatype;
		}
	}

	/**
	 * Returns the IRI of the primitive Type of an Property for
	 * {@link TimeValue} objects.
	 *
	 * @param propertyIdValue
	 * @param value
	 */
	public String setPropertyTypeFromTimeValue(PropertyIdValue propertyIdValue,
			TimeValue value) {
		return DatatypeIdValue.DT_TIME;
	}

	/**
	 * Returns the IRI of the primitive Type of an Property for
	 * {@link MonolingualTextValue} objects.
	 *
	 * @param propertyIdValue
	 * @param value
	 */
	public String setPropertyTypeFromMonolingualTextValue(
			PropertyIdValue propertyIdValue, MonolingualTextValue value) {
		return DatatypeIdValue.DT_MONOLINGUAL_TEXT;
	}

	/**
	 * Fetches the information of the given property from the Web API. Further
	 * properties are fetched in the same request and results cached so as to
	 * limit the total number of Web requests made until all properties are
	 * fetched.
	 *
	 * @param property
	 */
	protected void fetchPropertyInformation(PropertyIdValue property) {
		int propertyIdNumber = new Integer(property.getId().substring(1));
		// Don't do anything if all properties up to this index have already
		// been fetched. In particular, don't try indefinitely to find a
		// certain property type (maybe the property was deleted).
		if (this.smallestUnfetchedPropertyIdNumber > propertyIdNumber) {
			return;
		}

		List<String> propertyIds = new ArrayList<String>(
				API_MAX_ENTITY_DOCUMENT_NUMBER);

		propertyIds.add(property.getId());
		for (int i = 1; i < API_MAX_ENTITY_DOCUMENT_NUMBER; i++) {
			propertyIds.add("P" + this.smallestUnfetchedPropertyIdNumber);
			this.smallestUnfetchedPropertyIdNumber++;
		}

		dataFetcher.getFilter().setLanguageFilter(
				Collections.<String> emptySet());
		dataFetcher.getFilter().setSiteLinkFilter(
				Collections.<String> emptySet());

		Map<String, EntityDocument> properties;
		try {
			properties = dataFetcher.getEntityDocuments(propertyIds);
		} catch (MediaWikiApiErrorException e) {
			logger.error("Error when trying to fetch property data: "
					+ e.toString());
			properties = Collections.emptyMap();
		}

		for (Entry<String, EntityDocument> entry : properties.entrySet()) {
			EntityDocument propertyDocument = entry.getValue();
			if (!(propertyDocument instanceof PropertyDocument)) {
				continue;
			}

			String datatype = ((PropertyDocument) propertyDocument)
					.getDatatype().getIri();
			this.datatypes.put(entry.getKey(), datatype);
			logger.info("Fetched type information for property "
					+ entry.getKey() + " online: " + datatype);

			if (!DatatypeIdValue.DT_STRING.equals(datatype)) {
				continue;
			}

			for (StatementGroup sg : ((PropertyDocument) propertyDocument)
					.getStatementGroups()) {
				if (!sg.getProperty().getId().equals(this.uriPatternPropertyId)) {
					continue;
				}
				for (Statement statement : sg.getStatements()) {
					if (statement.getClaim().getMainSnak() instanceof ValueSnak
							&& ((ValueSnak) statement.getClaim().getMainSnak())
									.getValue() instanceof StringValue) {
						String uriPattern = ((StringValue) ((ValueSnak) statement
								.getClaim().getMainSnak()).getValue())
								.getString();
						if (this.uriPatterns.containsKey(entry.getKey())) {
							logger.info("Found multiple URI patterns for property "
									+ entry.getKey()
									+ " but only one is supported in current code.");
						}
						this.uriPatterns.put(entry.getKey(), uriPattern);
					}
				}
			}
		}

		if (!this.datatypes.containsKey(property.getId())) {
			logger.error("Failed to fetch type information for property "
					+ property.getId() + " online.");
		}
	}
}
