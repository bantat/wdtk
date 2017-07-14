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

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.ItemIdValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementDocument;
import org.wikidata.wdtk.datamodel.json.jackson.JsonSerializer;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;

/**
 * Class that provides high-level editing functionality for Wikibase data.
 *
 * @author Markus Kroetzsch
 *
 */
public class WikibaseDataEditor {

	static final Logger logger = LoggerFactory
			.getLogger(WikibaseDataEditor.class);

	/**
	 * API Action to edit data.
	 */
	final WbEditEntityAction wbEditEntityAction;

	/**
	 * Helper class to read data. Used for checking the state of the online data
	 * before editing.
	 */
	final WikibaseDataFetcher wikibaseDataFetcher;

	/**
	 * The IRI that identifies the site that the data is from.
	 */
	final String siteIri;

	/**
	 * If true, the bot flag will be set for all edits. This will only have
	 * effect when logged in with a user account that is in the bot group.
	 */
	boolean editAsBot = false;

	/**
	 * Creates an object to edit data via the Web API of the given
	 * {@link ApiConnection} object. The site URI is necessary to create data
	 * objects from API responses, since it is not contained in the data
	 * retrieved from the URI.
	 *
	 * @param connection
	 *            ApiConnection
	 * @param siteUri
	 *            the URI identifying the site that is accessed (usually the
	 *            prefix of entity URIs), e.g.,
	 *            "http://www.wikidata.org/entity/"
	 */
	public WikibaseDataEditor(ApiConnection connection, String siteUri) {
		this.wbEditEntityAction = new WbEditEntityAction(connection, siteUri);
		this.wikibaseDataFetcher = new WikibaseDataFetcher(connection, siteUri);
		this.siteIri = siteUri;
	}

	/**
	 * Returns true if edits should be flagged as bot edits. See
	 * {@link #setEditAsBot(boolean)} for details.
	 *
	 * @return whether to flag edits as bot
	 */
	public boolean editAsBot() {
		return this.editAsBot;
	}

	/**
	 * Switches the use of the bot parameter on or of. When set to true, the bot
	 * flag will be set for all edits. This will only have effect when logged in
	 * with a user account that is in the bot group. Bot users should set this
	 * to true in almost every case.
	 *
	 * @param editAsBot
	 */
	public void setEditAsBot(boolean editAsBot) {
		this.editAsBot = editAsBot;
	}

	/**
	 * Returns the current value of the maxlag parameter. It specifies the
	 * number of seconds. To save actions causing any more site replication lag,
	 * this parameter can make the client wait until the replication lag is less
	 * than the specified value. In case of excessive lag, error code "maxlag"
	 * is returned upon API requests.
	 *
	 * @return current setting of the maxlag parameter
	 */
	public int getMaxLag() {
		return this.wbEditEntityAction.getMaxLag();
	}

	/**
	 * Set the value of the maxlag parameter. If unsure, keep the default. See
	 * {@link WikibaseDataEditor#getMaxLag()} for details.
	 *
	 * @param maxLag
	 *            the new value in seconds
	 */
	public void setMaxLag(int maxLag) {
		this.wbEditEntityAction.setMaxLag(maxLag);
	}

	/**
	 * Returns the average time that a single edit should take, measured in
	 * milliseconds. See {@link WbEditEntityAction#getAverageTimePerEdit()} for
	 * details.
	 *
	 * @return average time per edit in milliseconds
	 */
	public int getAverageTimePerEdit() {
		return this.wbEditEntityAction.getAverageTimePerEdit();
	}

	/**
	 * Sets the average time that a single edit should take, measured in
	 * milliseconds. See {@link WbEditEntityAction#getAverageTimePerEdit()} for
	 * details.
	 *
	 * @param milliseconds
	 *            the new value in milliseconds
	 */
	public void setAverageTimePerEdit(int milliseconds) {
		this.wbEditEntityAction.setAverageTimePerEdit(milliseconds);
	}

	/**
	 * Returns the number of edits that will be performed before entering
	 * simulation mode, or -1 if there is no limit on the number of edits
	 * (default). See {@link WbEditEntityAction#getRemainingEdits()} for
	 * details.
	 *
	 * @return number of remaining edits
	 */
	public int getRemainingEdits() {
		return this.wbEditEntityAction.getRemainingEdits();
	}

	/**
	 * Sets the number of edits that this object can still perform. See
	 * {@link WbEditEntityAction#setRemainingEdits(int)} for details.
	 *
	 * @param remainingEdits
	 *            number of edits that can still be performed, or -1 to disable
	 *            this limit (default setting)
	 */
	public void setRemainingEdits(int remainingEdits) {
		this.wbEditEntityAction.setRemainingEdits(remainingEdits);
	}

	/**
	 * Sets the remaining edits for this component to 0, so that all edits are
	 * simulated but not actually send to the API.
	 */
	public void disableEditing() {
		this.wbEditEntityAction.setRemainingEdits(0);
	}

	/**
	 * Creates a new item document with the summary message as provided.
	 * <p>
	 * The item document that is given as a parameter must use a local item id,
	 * such as {@link ItemIdValue#NULL}, and its revision id must be 0. The
	 * newly created document is returned. It will contain the new id. Note that
	 * the site IRI used in this ID is not part of the API response; the site
	 * IRI given when constructing this object is used in this place.
	 * <p>
	 * Statements in the given data must have empty statement IDs.
	 *
	 * @param itemDocument
	 *            the document that contains the data to be written
	 * @param summary
	 *            additional summary message for the edit, or null to omit this
	 * @return newly created item document, or null if there was an error
	 * @throws IOException
	 *             if there was an IO problem, such as missing network
	 *             connection
	 * @throws MediaWikiApiErrorException
	 */
	public ItemDocument createItemDocument(ItemDocument itemDocument,
			String summary) throws IOException, MediaWikiApiErrorException {
		String data = JsonSerializer.getJsonString(itemDocument);
		return (ItemDocument) this.wbEditEntityAction.wbEditEntity(null, null,
				null, "item", data, false, this.editAsBot, 0, summary);
	}

	/**
	 * Creates a new property document with the summary message as provided.
	 * <p>
	 * The property document that is given as a parameter must use a local
	 * property id, such as {@link PropertyIdValue#NULL}, and its revision id
	 * must be 0. The newly created document is returned. It will contain the
	 * new property id and revision id. Note that the site IRI used in the
	 * property id is not part of the API response; the site IRI given when
	 * constructing this object is used in this place.
	 * <p>
	 * Statements in the given data must have empty statement IDs.
	 *
	 * @param propertyDocument
	 *            the document that contains the data to be written
	 * @param summary
	 *            additional summary message for the edit, or null to omit this
	 * @return newly created property document, or null if there was an error
	 * @throws IOException
	 *             if there was an IO problem, such as missing network
	 *             connection
	 * @throws MediaWikiApiErrorException
	 */
	public PropertyDocument createPropertyDocument(
			PropertyDocument propertyDocument, String summary)
			throws IOException, MediaWikiApiErrorException {
		String data = JsonSerializer.getJsonString(propertyDocument);
		return (PropertyDocument) this.wbEditEntityAction
				.wbEditEntity(null, null, null, "property", data, false,
						this.editAsBot, 0, summary);
	}

	/**
	 * Writes the data for the given item document with the summary message as
	 * given. Optionally, the existing data is cleared (deleted).
	 * <p>
	 * The id of the given item document is used to specify which item document
	 * should be changed. The site IRI will be ignored for this.
	 * <p>
	 * The revision id of the given item document is used to specify the base
	 * revision, enabling the API to detect edit conflicts. The value 0 can be
	 * used to omit this. It is strongly recommended to give a revision id when
	 * making edits where the outcome depends on the previous state of the data
	 * (i.e., any edit that does not use "clear").
	 * <p>
	 * If the data is not cleared, then the existing data will largely be
	 * preserved. Statements with empty ids will be added without checking if
	 * they exist already; statements with (valid) ids will replace any existing
	 * statements with these ids or just be added if there are none. Labels,
	 * descriptions, and aliases will be preserved for all languages for which
	 * no data is given at all. For aliases this means that writing one alias in
	 * a language will overwrite all aliases in this language, so some care is
	 * needed.
	 *
	 * @param itemDocument
	 *            the document that contains the data to be written
	 * @param clear
	 *            if true, the existing data will be replaced by the given data;
	 *            if false, the given data will be added to the existing data,
	 *            overwriting only parts that are set to new values
	 * @param summary
	 *            additional summary message for the edit, or null to omit this
	 * @return the modified item document, or null if there was an error
	 * @throws IOException
	 *             if there was an IO problem, such as missing network
	 *             connection
	 * @throws MediaWikiApiErrorException
	 */
	public ItemDocument editItemDocument(ItemDocument itemDocument,
			boolean clear, String summary) throws IOException,
			MediaWikiApiErrorException {
		String data = JsonSerializer.getJsonString(itemDocument);
		return (ItemDocument) this.wbEditEntityAction.wbEditEntity(itemDocument
				.getItemId().getId(), null, null, null, data, clear,
				this.editAsBot, itemDocument.getRevisionId(), summary);
	}

	/**
	 * Writes the data for the given property document with the summary message
	 * as given. Optionally, the existing data is cleared (deleted).
	 * <p>
	 * The id of the given property document is used to specify which property
	 * document should be changed. The site IRI will be ignored for this.
	 * <p>
	 * The revision id of the given property document is used to specify the
	 * base revision, enabling the API to detect edit conflicts. The value 0 can
	 * be used to omit this. It is strongly recommended to give a revision id
	 * when making edits where the outcome depends on the previous state of the
	 * data (i.e., any edit that does not use "clear").
	 * <p>
	 * If the data is not cleared, then the existing data will largely be
	 * preserved. Statements with empty ids will be added without checking if
	 * they exist already; statements with (valid) ids will replace any existing
	 * statements with these ids or just be added if there are none. Labels,
	 * descriptions, and aliases will be preserved for all languages for which
	 * no data is given at all. For aliases this means that writing one alias in
	 * a language will overwrite all aliases in this language, so some care is
	 * needed.
	 *
	 * @param propertyDocument
	 *            the document that contains the data to be written
	 * @param clear
	 *            if true, the existing data will be replaced by the given data;
	 *            if false, the given data will be added to the existing data,
	 *            overwriting only parts that are set to new values
	 * @param summary
	 *            additional summary message for the edit, or null to omit this
	 * @return the modified property document, or null if there was an error
	 * @throws IOException
	 *             if there was an IO problem, such as missing network
	 *             connection
	 * @throws MediaWikiApiErrorException
	 */
	public PropertyDocument editPropertyDocument(
			PropertyDocument propertyDocument, boolean clear, String summary)
			throws IOException, MediaWikiApiErrorException {
		String data = JsonSerializer.getJsonString(propertyDocument);
		return (PropertyDocument) this.wbEditEntityAction.wbEditEntity(
				propertyDocument.getPropertyId().getId(), null, null, null,
				data, clear, this.editAsBot, propertyDocument.getRevisionId(),
				summary);
	}

	/**
	 * Updates the statements of the item document identified by the given item
	 * id. The updates are computed with respect to the current data found
	 * online, making sure that no redundant deletions or duplicate insertions
	 * happen. The references of duplicate statements will be merged.
	 *
	 * @param itemIdValue
	 *            id of the document to be updated
	 * @param addStatements
	 *            the list of statements to be added or updated; statements with
	 *            empty statement id will be added; statements with non-empty
	 *            statement id will be updated (if such a statement exists)
	 * @param deleteStatements
	 *            the list of statements to be deleted; statements will only be
	 *            deleted if they are present in the current document (in
	 *            exactly the same form, with the same id)
	 * @param summary
	 *            short edit summary
	 * @return the updated document
	 * @throws MediaWikiApiErrorException
	 *             if the API returns errors
	 * @throws IOException
	 *             if there are IO problems, such as missing network connection
	 */
	public ItemDocument updateStatements(ItemIdValue itemIdValue,
			List<Statement> addStatements, List<Statement> deleteStatements,
			String summary) throws MediaWikiApiErrorException, IOException {

		ItemDocument currentDocument = (ItemDocument) this.wikibaseDataFetcher
				.getEntityDocument(itemIdValue.getId());

		return updateStatements(currentDocument, addStatements,
				deleteStatements, summary);
	}

	/**
	 * Updates the statements of the property document identified by the given
	 * property id. The computation of updates is the same as for
	 * {@link #updateStatements(ItemIdValue, List, List, String)}.
	 *
	 * @param propertyIdValue
	 *            id of the document to be updated
	 * @param addStatements
	 *            the list of statements to be added or updated; statements with
	 *            empty statement id will be added; statements with non-empty
	 *            statement id will be updated (if such a statement exists)
	 * @param deleteStatements
	 *            the list of statements to be deleted; statements will only be
	 *            deleted if they are present in the current document (in
	 *            exactly the same form, with the same id)
	 * @param summary
	 *            short edit summary
	 * @return the updated document
	 * @throws MediaWikiApiErrorException
	 *             if the API returns errors
	 * @throws IOException
	 *             if there are IO problems, such as missing network connection
	 */
	public PropertyDocument updateStatements(PropertyIdValue propertyIdValue,
			List<Statement> addStatements, List<Statement> deleteStatements,
			String summary) throws MediaWikiApiErrorException, IOException {

		PropertyDocument currentDocument = (PropertyDocument) this.wikibaseDataFetcher
				.getEntityDocument(propertyIdValue.getId());

		return updateStatements(currentDocument, addStatements,
				deleteStatements, summary);
	}

	/**
	 * Updates statements of the given document. The document should be the
	 * current revision of the data that is to be updated. The updates are
	 * computed with respect to the data found in the document, making sure that
	 * no redundant deletions or duplicate insertions happen. The references of
	 * duplicate statements will be merged.
	 * <p>
	 * The generic type T of this method must be a general interface such as
	 * {@link ItemDocument}, {@link PropertyDocument}, or
	 * {@link StatementDocument}. Specific implementations of these interfaces
	 * are not permitted.
	 *
	 * @param currentDocument
	 *            the document that is to be updated; needs to have a correct
	 *            revision id and entity id
	 * @param addStatements
	 *            the list of statements to be added or updated; statements with
	 *            empty statement id will be added; statements with non-empty
	 *            statement id will be updated (if such a statement exists)
	 * @param deleteStatements
	 *            the list of statements to be deleted; statements will only be
	 *            deleted if they are present in the current document (in
	 *            exactly the same form, with the same id)
	 * @param summary
	 *            short edit summary
	 * @return the updated document
	 * @throws MediaWikiApiErrorException
	 *             if the API returns errors
	 * @throws IOException
	 *             if there are IO problems, such as missing network connection
	 */
	@SuppressWarnings("unchecked")
	public <T extends StatementDocument> T updateStatements(T currentDocument,
			List<Statement> addStatements, List<Statement> deleteStatements,
			String summary) throws MediaWikiApiErrorException, IOException {

		StatementUpdate statementUpdate = new StatementUpdate(currentDocument,
				addStatements, deleteStatements);

		return (T) this.wbEditEntityAction.wbEditEntity(currentDocument
				.getEntityId().getId(), null, null, null, statementUpdate
				.getJsonUpdateString(), false, this.editAsBot, currentDocument
				.getRevisionId(), summary);
	}
}
