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

import org.wikidata.wdtk.datamodel.interfaces.StatementRank;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * A deserializer implementation for the StatementRank enumeration. This is
 * necessary since Java enumerations are in upper case but the Json counterpart
 * is in lower case.
 * 
 * @author Fredo Erxleben
 * 
 */
public class StatementRankDeserializer extends JsonDeserializer<StatementRank> {

	@Override
	public StatementRank deserialize(JsonParser jp, DeserializationContext ctxt)
			throws IOException {
		
		return StatementRank.valueOf(jp.getText().toUpperCase());
	}


}
