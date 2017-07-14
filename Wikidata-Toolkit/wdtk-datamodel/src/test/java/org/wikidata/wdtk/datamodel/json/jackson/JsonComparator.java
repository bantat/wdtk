package org.wikidata.wdtk.datamodel.json.jackson;

/*
 * #%L
 * Wikidata Toolkit Data Model
 * %%
 * Copyright (C) 2014 Wikidata Toolkit Developers
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

import org.junit.Assert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A helper class for comparing JSON objects to each other.
 *
 * @author Fredo Erxleben
 */
public class JsonComparator {

	private static ObjectMapper mapper = new ObjectMapper();

	/**
	 * Compares two JSON objects represented by Strings to each other. Both
	 * Strings are supposed to be valid JSON. From the given Strings the JSON
	 * tree is build and both trees are compared.
	 *
	 * @param string1
	 * @param string2
	 */
	public static void compareJsonStrings(String string1, String string2) {

		try {
			JsonNode tree1 = mapper.readTree(string1);
			JsonNode tree2 = mapper.readTree(string2);
			Assert.assertEquals(tree1, tree2);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
