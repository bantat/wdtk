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
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocument;
import org.wikidata.wdtk.datamodel.json.jackson.JacksonTermedStatementDocument;
import org.wikidata.wdtk.wikibaseapi.apierrors.MaxlagErrorException;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;
import org.wikidata.wdtk.wikibaseapi.apierrors.TokenErrorException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * Java implementation for the wbeditentity API action.
 *
 * @author Michael Guenther
 * @author Markus Kroetzsch
 */
public class WbEditEntityAction {

	static final Logger logger = LoggerFactory
			.getLogger(WbEditEntityAction.class);

	static int MAXLAG_SLEEP_TIME = 5000;

	/**
	 * Connection to an Wikibase API.
	 */
	final ApiConnection connection;

	/**
	 * The IRI that identifies the site that the data is from.
	 */
	final String siteIri;

	/**
	 * Mapper object used for deserializing JSON data.
	 */
	final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Current CSRF (Cross-Site Request Forgery) token, or null if no valid
	 * token is known.
	 */
	String csrfToken = null;

	/**
	 * Value in seconds of MediaWiki's maxlag parameter. Shorter is nicer,
	 * longer is more aggressive.
	 */
	int maxLag = 5;

	/**
	 * Number of recent editing times to monitor in order to avoid editing too
	 * fast. Wikidata.org seems to block fast editors after 9 edits, so this
	 * size seems to make sense.
	 */
	final static int editTimeWindow = 9;

	/**
	 * Average time to wait after each edit. Individual edits can be faster than
	 * this, but it is ensured that this time will be taken per edit in the long
	 * run.
	 */
	int averageMsecsPerEdit = 2000;

	/**
	 * Times of the last {@link #editTimeWindow} edits. Used in a loop. Most
	 * recent edit time is at {@link #curEditTimeSlot}.
	 */
	final long[] recentEditTimes = new long[editTimeWindow];
	/**
	 * @see #recentEditTimes
	 */
	int curEditTimeSlot = 0;

	/**
	 * Number of edits that will be performed before the object enters
	 * simulation mode, or -1 if there is no limit on the number of edits.
	 */
	int remainingEdits = -1;

	/**
	 * Creates an object to modify data on a Wikibase site. The API is used to
	 * request the changes. The site URI is necessary since it is not contained
	 * in the data retrieved from the API.
	 *
	 * @param connection
	 *            {@link ApiConnection} Object to send the requests
	 * @param siteUri
	 *            the URI identifying the site that is accessed (usually the
	 *            prefix of entity URIs), e.g.,
	 *            "http://www.wikidata.org/entity/"
	 */
	public WbEditEntityAction(ApiConnection connection, String siteUri) {
		this.connection = connection;
		this.siteIri = siteUri;
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
		return this.maxLag;
	}

	/**
	 * Set the value of the maxlag parameter. If unsure, keep the default. See
	 * {@link #getMaxLag()} for details.
	 *
	 * @param maxLag
	 *            the new value in seconds
	 */
	public void setMaxLag(int maxLag) {
		this.maxLag = maxLag;
	}

	/**
	 * Returns the number of edits that will be performed before entering
	 * simulation mode, or -1 if there is no limit on the number of edits
	 * (default).
	 *
	 * @return number of remaining edits
	 */
	public int getRemainingEdits() {
		return this.remainingEdits;
	}

	/**
	 * Sets the number of edits that this object can still perform. Thereafter,
	 * edits will only be prepared but not actually performed in the Web API.
	 * This function is useful to do a defined number of test edits. If this
	 * number is set to 0, then no edits will be performed. If it is set to -1
	 * (or any other negative number), then there is no limit on the edits
	 * performed.
	 *
	 * @param remainingEdits
	 *            number of edits that can still be performed, or -1 to disable
	 *            this limit (default setting)
	 */
	public void setRemainingEdits(int remainingEdits) {
		this.remainingEdits = remainingEdits;
	}

	/**
	 * Returns the average time in milliseconds that one edit will take. This
	 * time is enforced to avoid overloading the site with too many edits, and
	 * also to throttle the rate of editing (which is useful to stop a bot in
	 * case of errors). Individual edits can be faster than this, but if several
	 * consecutive edits are above this rate, the program will pause until the
	 * expected speed is reached again. The delay is based on real system time.
	 * This means that it will only wait as long as necessary. If your program
	 * takes time between edits for other reasons, there will be no additional
	 * delay caused by this feature.
	 *
	 * @return average time per edit in milliseconds
	 */
	public int getAverageTimePerEdit() {
		return this.averageMsecsPerEdit;
	}

	/**
	 * Sets the average time that a single edit should take, measured in
	 * milliseconds. See {@link #getAverageTimePerEdit()} for details.
	 *
	 * @param milliseconds
	 *            the new value in milliseconds
	 */
	public void setAverageTimePerEdit(int milliseconds) {
		this.averageMsecsPerEdit = milliseconds;
	}

	/**
	 * Executes the API action "wbeditentity" for the given parameters. Created
	 * or modified items are returned as a result. In particular, this is
	 * relevant to find out about the id assigned to a newly created entity.
	 * <p>
	 * Unless the parameter clear is true, data of existing entities will be
	 * modified or added, but not deleted. For labels, descriptions, and
	 * aliases, this happens by language. In particular, if an item has English
	 * and German aliases, and an edit action writes a new English alias, then
	 * this new alias will replace all previously existing English aliases,
	 * while the German aliases will remain untouched. In contrast, adding
	 * statements for a certain property will not delete existing statements of
	 * this property. In fact, it is even possible to create many copies of the
	 * exact same statement. A special JSON syntax exists for deleting specific
	 * statements.
	 * <p>
	 * See the <a href=
	 * "https://www.wikidata.org/w/api.php?action=help&modules=wbeditentity"
	 * >online API documentation</a> for further information.
	 * <p>
	 * TODO: There is currently no way to delete the label, description, or
	 * aliases for a particular language without clearing all data. Empty
	 * strings are not accepted. One might achieve this by adapting the JSON
	 * serialization to produce null values for such strings, and for alias
	 * lists that contain only such strings.
	 *
	 * @param id
	 *            the id of the entity to be edited; if used, the site and title
	 *            parameters must be null
	 * @param site
	 *            when selecting an entity by title, the site key for the title,
	 *            e.g., "enwiki"; if used, title must also be given but id must
	 *            be null
	 * @param title
	 *            string used to select an entity by title; if used, site must
	 *            also be given but id must be null
	 * @param newEntity
	 *            used for creating a new entity of a given type; the value
	 *            indicates the intended entity type; possible values include
	 *            "item" and "property"; if used, the parameters id, site, and
	 *            title must be null
	 * @param data
	 *            JSON representation of the data that is to be written; this is
	 *            a mandatory parameter
	 * @param clear
	 *            if true, existing data will be cleared (deleted) before
	 *            writing the new data
	 * @param bot
	 *            if true, edits will be flagged as "bot edits" provided that
	 *            the logged in user is in the bot group; for regular users, the
	 *            flag will just be ignored
	 * @param baserevid
	 *            the revision of the data that the edit refers to or 0 if this
	 *            should not be submitted; when used, the site will ensure that
	 *            no edit has happened since this revision to detect edit
	 *            conflicts; it is recommended to use this whenever in all
	 *            operations where the outcome depends on the state of the
	 *            online data
	 * @param summary
	 *            summary for the edit; will be prepended by an automatically
	 *            generated comment; the length limit of the autocomment
	 *            together with the summary is 260 characters: everything above
	 *            that limit will be cut off
	 * @return modified or created entity document
	 * @throws IOException
	 *             if there was an IO problem. such as missing network
	 *             connection
	 * @throws MediaWikiApiErrorException
	 *             if the API returns an error
	 */
	public EntityDocument wbEditEntity(String id, String site, String title,
			String newEntity, String data, boolean clear, boolean bot,
			long baserevid, String summary) throws IOException,
			MediaWikiApiErrorException {

		Validate.notNull(data,
				"Data parameter cannot be null when editing entity data");

		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put(ApiConnection.PARAM_ACTION, "wbeditentity");

		if (newEntity != null) {
			parameters.put("new", newEntity);
			if (title != null || site != null || id != null) {
				throw new IllegalArgumentException(
						"Cannot use parameters \"id\", \"site\", or \"title\" when creating a new entity.");
			}
		} else if (id != null) {
			parameters.put("id", id);
			if (title != null || site != null) {
				throw new IllegalArgumentException(
						"Cannot use parameters \"site\" or \"title\" when using id to edit entity data");
			}
		} else if (title != null) {
			if (site == null) {
				throw new IllegalArgumentException(
						"Site parameter is required when using title parameter to edit entity data.");
			}
			parameters.put("site", site);
			parameters.put("title", title);
		} else {
			throw new IllegalArgumentException(
					"This action must create a new item, or specify an id, or specify a site and title.");
		}

		parameters.put("data", data);

		if (bot) {
			parameters.put("bot", "");
		}

		if (baserevid != 0) {
			parameters.put("baserevid", Long.toString(baserevid));
		}

		if (clear) {
			parameters.put("clear", "");
		}

		if (summary != null) {
			parameters.put("summary", summary);
		}

		parameters.put("maxlag", new Integer(this.maxLag).toString());
		parameters.put("token", getCsrfToken());
		parameters.put(ApiConnection.PARAM_FORMAT, "json");

		if (this.remainingEdits > 0) {
			this.remainingEdits--;
		} else if (this.remainingEdits == 0) {
			logger.info("Not editing entity (simulation mode). Request parameters were: "
					+ parameters.toString());
			return null;
		}

		checkEditSpeed();

		EntityDocument result = null;
		int retry = 5;
		MediaWikiApiErrorException lastException = null;
		while (retry > 0) {
			try {
				result = doWbEditEntity(parameters);
				break;
			} catch (TokenErrorException e) { // try again with a fresh token
				lastException = e;
				refreshCsrfToken();
				parameters.put("token", getCsrfToken());
			} catch (MaxlagErrorException e) { // wait for 5 seconds
				lastException = e;
				logger.warn(e.getMessage() + " -- pausing for 5 seconds.");
				try {
					Thread.sleep(MAXLAG_SLEEP_TIME);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			retry--;
		}

		if (result == null) {
			logger.error("Gave up after several retries. Last error was: "
					+ lastException.toString());
			throw lastException;
		}

		return result;
	}

	/**
	 * Executes a call to wbeditentity. Minimal error handling.
	 *
	 * @param parameters
	 *            the parameters to be used in the call
	 * @return the entity document that is returned, or null in case of errors
	 * @throws IOException
	 *             if there were IO errors
	 * @throws MediaWikiApiErrorException
	 *             if the API returned an error
	 */
	private EntityDocument doWbEditEntity(Map<String, String> parameters)
			throws IOException, MediaWikiApiErrorException {

		try (InputStream response = this.connection.sendRequest("POST",
				parameters)) {

			JsonNode root = this.mapper.readTree(response);

			this.connection.checkErrors(root);
			this.connection.logWarnings(root);

			if (root.has("item")) {
				return parseJsonResponse(root.path("item"));
			} else if (root.has("property")) {
				// TODO: not tested because of missing
				// permissions
				return parseJsonResponse(root.path("property"));
			} else if (root.has("entity")) {
				return parseJsonResponse(root.path("entity"));
			} else {
				throw new JsonMappingException(
						"No entity document found in API response.");
			}
		}
	}

	/**
	 * Returns a CSRF (Cross-Site Request Forgery) token as required to edit
	 * data.
	 */
	private String getCsrfToken() {
		if (this.csrfToken == null) {
			refreshCsrfToken();
		}
		return this.csrfToken;
	}

	/**
	 * Obtains and sets a new CSRF token, whether or not there is already a
	 * token set right now.
	 */
	private void refreshCsrfToken() {

		this.csrfToken = fetchCsrfToken();
		// TODO if this is null, we could try to recover here:
		// (1) Check if we are still logged in; maybe log in again
		// (2) If there is another error, maybe just run the operation again
	}

	/**
	 * Executes a API query action to get a new CSRF (Cross-Site Request
	 * Forgery) token. The method only executes the action, without doing any
	 * checks first. If errors occur, they are logged and null is returned.
	 *
	 * @return newly retrieved token or null if no token was retrieved
	 */
	private String fetchCsrfToken() {
		Map<String, String> params = new HashMap<String, String>();
		params.put(ApiConnection.PARAM_ACTION, "query");
		params.put("meta", "tokens");
		params.put(ApiConnection.PARAM_FORMAT, "json");

		try (InputStream response = this.connection.sendRequest("POST", params)) {

			JsonNode root = this.mapper.readTree(response);

			this.connection.checkErrors(root);
			this.connection.logWarnings(root);

			return root.path("query").path("tokens")
					.path("csrftoken").textValue();
		} catch (IOException | MediaWikiApiErrorException e) {
			logger.error("Error when trying to fetch csrf token: "
					+ e.toString());
		}
		return null;
	}

	/**
	 * Parse a JSON response to extract an entity document.
	 * <p>
	 * TODO This method currently contains code to work around Wikibase issue
	 * https://phabricator.wikimedia.org/T73349. This should be removed once the
	 * issue is fixed.
	 *
	 * @param entityNode
	 *            the JSON node that should contain the entity document data
	 * @return the entitiy document, or null if there were unrecoverable errors
	 * @throws IOException
	 * @throws JsonProcessingException
	 */
	private EntityDocument parseJsonResponse(JsonNode entityNode)
			throws JsonProcessingException, IOException {
		try {
			JacksonTermedStatementDocument ed = mapper.treeToValue(entityNode,
					JacksonTermedStatementDocument.class);
			ed.setSiteIri(this.siteIri);

			return ed;
		} catch (JsonProcessingException e) {
			logger.warn("Error when reading JSON for entity "
					+ entityNode.path("id").asText("UNKNOWN")
					+ ": "
					+ e.toString()
					+ "\nTrying to manually fix issue https://phabricator.wikimedia.org/T73349.");
			String jsonString = entityNode.toString();
			jsonString = jsonString
					.replace("\"sitelinks\":[]", "\"sitelinks\":{}")
					.replace("\"labels\":[]", "\"labels\":{}")
					.replace("\"aliases\":[]", "\"aliases\":{}")
					.replace("\"claims\":[]", "\"claims\":{}")
					.replace("\"descriptions\":[]", "\"descriptions\":{}");

			ObjectReader documentReader = this.mapper
					.reader(JacksonTermedStatementDocument.class);

			JacksonTermedStatementDocument ed;
			ed = documentReader.readValue(jsonString);
			ed.setSiteIri(this.siteIri);
			return ed;
		}
	}

	/**
	 * Makes sure that we are not editing too fast. The method stores the last
	 * {@link WbEditEntityAction#editTimeWindow} time points when an edit was
	 * made. If the time since the oldest edit in this window is shorter than
	 * {@link #averageMsecsPerEdit} milliseconds, then the method will pause the
	 * thread for the remaining time.
	 */
	private void checkEditSpeed() {
		long currentTime = System.nanoTime();
		int nextIndex = (this.curEditTimeSlot + 1) % editTimeWindow;
		if (this.recentEditTimes[nextIndex] != 0
				&& (currentTime - this.recentEditTimes[nextIndex]) / 1000000 < this.averageMsecsPerEdit
						* editTimeWindow) {
			long sleepTime = this.averageMsecsPerEdit * editTimeWindow
					- (currentTime - this.recentEditTimes[nextIndex]) / 1000000;
			logger.info("We are editing too fast. Pausing for " + sleepTime
					+ " milliseconds.");
			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			currentTime = System.nanoTime();
		}

		this.recentEditTimes[nextIndex] = currentTime;
		this.curEditTimeSlot = nextIndex;
	}

}
