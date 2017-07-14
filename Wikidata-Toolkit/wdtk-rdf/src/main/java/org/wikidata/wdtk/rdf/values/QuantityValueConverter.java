package org.wikidata.wdtk.rdf.values;

/*
 * #%L
 * Wikidata Toolkit RDF
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

import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.rio.RDFHandlerException;
import org.wikidata.wdtk.datamodel.interfaces.DatatypeIdValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.QuantityValue;
import org.wikidata.wdtk.rdf.OwlDeclarationBuffer;
import org.wikidata.wdtk.rdf.PropertyRegister;
import org.wikidata.wdtk.rdf.RdfWriter;
import org.wikidata.wdtk.rdf.Vocabulary;

public class QuantityValueConverter extends
		BufferedValueConverter<QuantityValue> {

	public QuantityValueConverter(RdfWriter rdfWriter,
			PropertyRegister propertyRegister,
			OwlDeclarationBuffer rdfConversionBuffer) {
		super(rdfWriter, propertyRegister, rdfConversionBuffer);
	}

	@Override
	public Value getRdfValue(QuantityValue value,
			PropertyIdValue propertyIdValue, boolean simple) {
		String datatype = this.propertyRegister
				.setPropertyTypeFromQuantityValue(propertyIdValue, value);

		switch (datatype) {
		case DatatypeIdValue.DT_QUANTITY:
			if (simple) {
				this.rdfConversionBuffer.addDatatypeProperty(propertyIdValue);
				return this.rdfWriter.getLiteral(value.getNumericValue()
						.toString(), RdfWriter.XSD_DECIMAL);
			} else {
				URI valueUri = this.rdfWriter.getUri(Vocabulary
						.getQuantityValueUri(value,
								this.propertyRegister.getUriPrefix()));

				this.rdfConversionBuffer.addObjectProperty(propertyIdValue);
				addValue(value, valueUri);

				return valueUri;
			}
		default:
			logIncompatibleValueError(propertyIdValue, datatype, "quantity");
			return null;
		}
	}

	@Override
	public void writeValue(QuantityValue value, Resource resource)
			throws RDFHandlerException {
		this.rdfWriter.writeTripleValueObject(resource, RdfWriter.RDF_TYPE,
				RdfWriter.WB_QUANTITY_VALUE);
		this.rdfWriter.writeTripleLiteralObject(resource,
				RdfWriter.WB_NUMERIC_VALUE, value.getNumericValue().toString(),
				RdfWriter.XSD_DECIMAL);
		if(value.getLowerBound() != null) {
			this.rdfWriter.writeTripleLiteralObject(resource,
					RdfWriter.WB_LOWER_BOUND, value.getLowerBound().toString(),
					RdfWriter.XSD_DECIMAL);
		}
		if(value.getUpperBound() != null) {
			this.rdfWriter.writeTripleLiteralObject(resource,
					RdfWriter.WB_UPPER_BOUND, value.getUpperBound().toString(),
					RdfWriter.XSD_DECIMAL);
		}
		String unitIri;
		if ("".equals(value.getUnit())) {
			unitIri = Vocabulary.WB_NO_UNIT;
		} else {
			unitIri = value.getUnit();
		}
		this.rdfWriter.writeTripleUriObject(resource,
				RdfWriter.WB_QUANTITY_UNIT, unitIri);
	}

}
